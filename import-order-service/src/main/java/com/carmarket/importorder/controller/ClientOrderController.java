package com.carmarket.importorder.controller;

import com.carmarket.importorder.config.ImportOrderProperties;
import com.carmarket.importorder.dto.ClientOrderView;
import com.carmarket.importorder.dto.ImportOrderDtos.ClientAccessGranted;
import com.carmarket.importorder.dto.ImportOrderDtos.ClientAccessRequest;
import com.carmarket.importorder.dto.ImportOrderDtos.FileView;
import com.carmarket.importorder.dto.ImportOrderDtos.PresignedUrl;
import com.carmarket.importorder.security.ClientRequestInfo;
import com.carmarket.importorder.service.ClientAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * The client page /zlecenie/{code}. Public at the gateway (and in SecurityConfig); secured here
 * with the order password and the X-Order-Token it is exchanged for.
 *
 * POST /import-orders/public/{code}/access        {password} → {token, expiresAt}
 * GET  /import-orders/public/{code}               X-Order-Token → ClientOrderView
 * GET  /import-orders/public/{code}/files/{id}/url X-Order-Token → presigned URL (visible files only)
 * POST /import-orders/public/{code}/files         X-Order-Token, multipart files + category → the client's documents
 */
@RestController
@RequestMapping("/import-orders/public/{code}")
@RequiredArgsConstructor
public class ClientOrderController {

    static final String TOKEN_HEADER = "X-Order-Token";

    private final ClientAccessService service;
    private final ImportOrderProperties properties;

    @PostMapping("/access")
    public ClientAccessGranted access(@PathVariable String code, @Valid @RequestBody ClientAccessRequest request,
                                      HttpServletRequest http) {
        return service.login(code, request.password(), ClientRequestInfo.from(http, properties.trustedProxies()));
    }

    @GetMapping
    public ClientOrderView view(@PathVariable String code, @RequestHeader(TOKEN_HEADER) String token,
                                HttpServletRequest http) {
        return service.view(code, token, ClientRequestInfo.from(http, properties.trustedProxies()));
    }

    @PostMapping(path = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<FileView> upload(@PathVariable String code, @RequestHeader(TOKEN_HEADER) String token,
                                 @RequestParam("files") List<MultipartFile> files, @RequestParam String category,
                                 HttpServletRequest http) {
        return service.upload(code, token, files, category, ClientRequestInfo.from(http, properties.trustedProxies()));
    }

    @GetMapping("/files/{fileId}/url")
    public PresignedUrl fileUrl(@PathVariable String code, @PathVariable UUID fileId,
                                @RequestHeader(TOKEN_HEADER) String token) {
        return service.fileUrl(code, token, fileId);
    }
}
