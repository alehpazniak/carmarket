package com.carmarket.importorder.service;

import com.carmarket.importorder.config.ImportOrderProperties;
import com.carmarket.importorder.dto.ClientOrderView;
import com.carmarket.importorder.dto.ImportOrderDtos.ClientAccessGranted;
import com.carmarket.importorder.dto.ImportOrderDtos.FileView;
import com.carmarket.importorder.dto.ImportOrderDtos.PresignedUrl;
import com.carmarket.importorder.entity.AccessEvent;
import com.carmarket.importorder.entity.AccessLogEntry;
import com.carmarket.importorder.entity.ImportOrder;
import com.carmarket.importorder.entity.ImportOrderFile;
import com.carmarket.importorder.exception.ApiException;
import com.carmarket.importorder.repository.AccessLogRepository;
import com.carmarket.importorder.repository.ImportOrderFileRepository;
import com.carmarket.importorder.repository.ImportOrderRepository;
import com.carmarket.importorder.security.ClientRequestInfo;
import com.carmarket.importorder.security.ClientTokenService;
import com.carmarket.importorder.security.ClientTokenService.ClientToken;
import com.carmarket.importorder.security.ClientTokenService.IssuedToken;
import com.carmarket.importorder.storage.FileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The client side: link + password → X-Order-Token, then the order view, file URLs and the client's own uploads.
 * <p>
 * Every failed check gives the same 401, and an unknown code still costs a BCrypt comparison, so
 * neither the answer nor its timing tells whether a code exists. After too many wrong passwords
 * the order is locked for a while (429 with Retry-After).
 */
@Slf4j
@Service
public class ClientAccessService {

    private static final Pattern CODE_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");
    private static final Duration VIEW_LOG_INTERVAL = Duration.ofHours(1);

    private final ImportOrderRepository orders;
    private final ImportOrderFileRepository files;
    private final AccessLogRepository accessLog;
    private final ClientTokenService tokens;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ClientViewFactory views;
    private final FileStorage storage;
    private final OrderFileService fileService;
    private final ImportOrderProperties.Access config;
    private final TransactionTemplate transaction;
    /** Compared against when the code is unknown, so that case takes as long as a real check. */
    private final String dummyHash;

    public ClientAccessService(ImportOrderRepository orders, ImportOrderFileRepository files,
                               AccessLogRepository accessLog, ClientTokenService tokens,
                               BCryptPasswordEncoder passwordEncoder, ClientViewFactory views, FileStorage storage,
                               OrderFileService fileService, ImportOrderProperties properties,
                               PlatformTransactionManager transactionManager) {
        this.orders = orders;
        this.files = files;
        this.accessLog = accessLog;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.views = views;
        this.storage = storage;
        this.fileService = fileService;
        this.config = properties.access();
        this.transaction = new TransactionTemplate(transactionManager);
        byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        this.dummyHash = passwordEncoder.encode(Base64.getEncoder().encodeToString(random));
    }

    private enum Result { GRANTED, DENIED, LOCKED }

    private record Outcome(Result result, IssuedToken token, long retryAfterSeconds) {
        static Outcome denied() {
            return new Outcome(Result.DENIED, null, 0);
        }
    }

    /**
     * Checks the password under a row lock. The outcome is computed inside the transaction and only
     * turned into an error afterwards, so the failure counter and the access log are committed.
     */
    public ClientAccessGranted login(String code, String password, ClientRequestInfo request) {
        String normalized = ClientCredentialsGenerator.normalize(password);
        Outcome outcome = transaction.execute(status -> attempt(code, normalized, request));
        return switch (outcome.result()) {
            case GRANTED -> new ClientAccessGranted(outcome.token().token(), outcome.token().expiresAt());
            case LOCKED -> throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many attempts, try again later", outcome.retryAfterSeconds());
            case DENIED -> throw ApiException.unauthorized();
        };
    }

    private Outcome attempt(String code, String password, ClientRequestInfo request) {
        Optional<ImportOrder> found = code != null && CODE_FORMAT.matcher(code).matches()
            ? orders.findByPublicCodeForUpdate(code)
            : Optional.empty();
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            return Outcome.denied();
        }
        ImportOrder order = found.get();
        Instant now = Instant.now();
        if (order.getLockedUntil() != null && order.getLockedUntil().isAfter(now)) {
            return new Outcome(Result.LOCKED, null, secondsUntil(order.getLockedUntil(), now));
        }

        boolean matches = passwordEncoder.matches(password, order.getPasswordHash());
        if (!order.isClientAccessOpen()) {
            return Outcome.denied();
        }
        if (!matches) {
            logAccess(order, AccessEvent.LOGIN_FAILED, request, null);
            int failures = order.getFailedAttempts() + 1;
            if (failures >= config.maxFailedAttempts()) {
                order.setFailedAttempts(0);
                order.setLockedUntil(now.plus(config.lockDuration()));
                log.warn("Client access of import order {} locked after {} failed attempts", order.getId(), failures);
                return new Outcome(Result.LOCKED, null, config.lockDuration().toSeconds());
            }
            order.setFailedAttempts(failures);
            return Outcome.denied();
        }

        order.setFailedAttempts(0);
        order.setLockedUntil(null);
        IssuedToken token = tokens.issue(order.getId(), order.getAccessVersion());
        logAccess(order, AccessEvent.LOGIN_OK, request, tokens.parse(token.token()).map(ClientToken::tokenId).orElse(null));
        return new Outcome(Result.GRANTED, token, 0);
    }

    /** The client view; a VIEW is logged at most once per token per hour. */
    @Transactional
    public ClientOrderView view(String code, String token, ClientRequestInfo request) {
        Authorized auth = authorize(code, token);
        boolean recentlyLogged = accessLog.existsByOrderIdAndTokenIdAndEventAndAtAfter(
            auth.order().getId(), auth.token().tokenId(), AccessEvent.VIEW, Instant.now().minus(VIEW_LOG_INTERVAL));
        if (!recentlyLogged) {
            logAccess(auth.order(), AccessEvent.VIEW, request, auth.token().tokenId());
        }
        return views.build(auth.order());
    }

    /** Only files marked visible to the client; anything else is a 404. */
    @Transactional(readOnly = true)
    public PresignedUrl fileUrl(String code, String token, UUID fileId) {
        Authorized auth = authorize(code, token);
        ImportOrderFile file = files.findByIdAndOrderId(fileId, auth.order().getId())
            .filter(ImportOrderFile::isClientVisible)
            .orElseThrow(ApiException::notFound);
        storage.requireEnabled();
        return fileService.presign(file);
    }

    /**
     * The client's own documents (e.g. a payment confirmation). The token is checked again right
     * before the rows are written, so a password reset during a slow upload still stops it.
     */
    public List<FileView> upload(String code, String token, List<MultipartFile> uploads, String category,
                                 ClientRequestInfo request) {
        Authorized auth = transaction.execute(status -> authorize(code, token));
        List<FileView> stored = fileService.uploadFromClient(() -> authorize(code, token).order(), uploads, category);
        transaction.executeWithoutResult(status ->
            logAccess(auth.order(), AccessEvent.UPLOAD, request, auth.token().tokenId()));
        return stored;
    }

    private record Authorized(ImportOrder order, ClientToken token) {
    }

    /**
     * Signature and expiry, the token's order matches the code, its version is the current
     * access_version (a password reset bumps it) and access is still open.
     */
    private Authorized authorize(String code, String rawToken) {
        ClientToken token = tokens.parse(rawToken).orElseThrow(ApiException::unauthorized);
        if (code == null || !CODE_FORMAT.matcher(code).matches()) throw ApiException.unauthorized();
        ImportOrder order = orders.findByPublicCode(code).orElseThrow(ApiException::unauthorized);
        if (!order.getId().equals(token.orderId())
            || order.getAccessVersion() != token.accessVersion()
            || !order.isClientAccessOpen()) {
            throw ApiException.unauthorized();
        }
        return new Authorized(order, token);
    }

    private void logAccess(ImportOrder order, AccessEvent event, ClientRequestInfo request, String tokenId) {
        accessLog.save(AccessLogEntry.builder()
            .orderId(order.getId())
            .event(event)
            .ipPrefix(request.ipPrefix())
            .userAgent(request.userAgent())
            .tokenId(tokenId)
            .build());
    }

    private static long secondsUntil(Instant until, Instant now) {
        return Math.max(1, (Duration.between(now, until).toMillis() + 999) / 1000);
    }
}
