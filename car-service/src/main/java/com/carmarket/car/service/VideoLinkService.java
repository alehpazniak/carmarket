package com.carmarket.car.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a YouTube, TikTok or Instagram link pasted by a seller into a canonical URL the frontend knows how
 * to embed: {@code https://www.youtube.com/watch?v=<id>}, {@code https://www.tiktok.com/@<user>/video/<id>},
 * {@code https://m.tiktok.com/v/<id>.html}, {@code https://www.instagram.com/reel/<code>/}
 * or {@code https://www.instagram.com/p/<code>/}.
 * TikTok share links ({@code vm.tiktok.com/...}, {@code tiktok.com/t/...}) carry no video id, so they are
 * resolved by following their redirects; only tiktok.com hosts are ever requested.
 */
@Slf4j
@Service
public class VideoLinkService {

    private static final Pattern YOUTUBE_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern TIKTOK_VIDEO_PATH = Pattern.compile("^/@([A-Za-z0-9._]{1,64})/video/(\\d{5,25})/?$");
    private static final Pattern TIKTOK_MOBILE_PATH = Pattern.compile("^/v/(\\d{5,25})(\\.html)?/?$");
    // /reel/<code>, /reels/<code>, /p/<code>, /tv/<code>, optionally prefixed by /<username>
    private static final Pattern INSTAGRAM_PATH =
        Pattern.compile("^(?:/[A-Za-z0-9._]{1,30})?/(reels?|p|tv)/([A-Za-z0-9_-]{5,40})/?$");
    private static final Pattern TIKTOK_SHORT_PATH = Pattern.compile("^/(t/)?[A-Za-z0-9]{5,20}/?$");

    private static final Set<String> YOUTUBE_HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com");
    private static final Set<String> TIKTOK_HOSTS = Set.of("tiktok.com", "www.tiktok.com", "m.tiktok.com");
    private static final Set<String> INSTAGRAM_HOSTS = Set.of("instagram.com", "www.instagram.com", "m.instagram.com", "instagr.am");
    private static final Set<String> TIKTOK_SHORT_HOSTS = Set.of("vm.tiktok.com", "vt.tiktok.com");
    private static final List<String> YOUTUBE_PATH_PREFIXES = List.of("/shorts/", "/embed/", "/live/");
    private static final int MAX_REDIRECTS = 3;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    public String normalize(String rawUrl) {
        URI uri = parse(rawUrl);
        String host = uri.getHost().toLowerCase();
        String path = pathOf(uri);

        if (host.equals("youtu.be")) {
            return youtube(stripSlashes(path));
        }
        if (YOUTUBE_HOSTS.contains(host)) {
            if (path.equals("/watch")) {
                return youtube(queryParam(uri, "v").orElse(""));
            }
            for (String prefix : YOUTUBE_PATH_PREFIXES) {
                if (path.startsWith(prefix)) {
                    return youtube(stripSlashes(path.substring(prefix.length())));
                }
            }
        }
        if (TIKTOK_HOSTS.contains(host)) {
            Optional<String> canonical = canonicalTikTok(path);
            if (canonical.isPresent()) {
                return canonical.get();
            }
            if (path.startsWith("/t/") && TIKTOK_SHORT_PATH.matcher(path).matches()) {
                return resolveTikTokShortLink(uri);
            }
        }
        if (TIKTOK_SHORT_HOSTS.contains(host) && TIKTOK_SHORT_PATH.matcher(path).matches()) {
            return resolveTikTokShortLink(uri);
        }
        if (INSTAGRAM_HOSTS.contains(host)) {
            Matcher instagram = INSTAGRAM_PATH.matcher(path);
            if (instagram.matches()) {
                String kind = instagram.group(1).equals("p") ? "p" : "reel";
                return "https://www.instagram.com/%s/%s/".formatted(kind, instagram.group(2));
            }
        }
        throw new IllegalArgumentException("Unsupported video link, paste a YouTube, TikTok or Instagram video URL");
    }

    private URI parse(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        if (!trimmed.matches("(?i)^https?://.*")) {
            trimmed = "https://" + trimmed;
        }
        try {
            URI uri = new URI(trimmed);
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("Invalid video link");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid video link");
        }
    }

    private String youtube(String id) {
        if (!YOUTUBE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid YouTube video link");
        }
        return "https://www.youtube.com/watch?v=" + id;
    }

    private Optional<String> canonicalTikTok(String path) {
        Matcher full = TIKTOK_VIDEO_PATH.matcher(path);
        if (full.matches()) {
            return Optional.of("https://www.tiktok.com/@%s/video/%s".formatted(full.group(1), full.group(2)));
        }
        Matcher mobile = TIKTOK_MOBILE_PATH.matcher(path);
        if (mobile.matches()) {
            return Optional.of("https://m.tiktok.com/v/%s.html".formatted(mobile.group(1)));
        }
        return Optional.empty();
    }

    private String resolveTikTokShortLink(URI shortLink) {
        URI current = shortLink;
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            Optional<URI> location = fetchRedirect(current);
            if (location.isEmpty()) {
                break;
            }
            current = current.resolve(location.get());
            String host = current.getHost() == null ? "" : current.getHost().toLowerCase();
            if (!TIKTOK_HOSTS.contains(host) && !TIKTOK_SHORT_HOSTS.contains(host)) {
                break;
            }
            Optional<String> canonical = canonicalTikTok(pathOf(current));
            if (canonical.isPresent()) {
                return canonical.get();
            }
        }
        throw new IllegalArgumentException("Could not resolve TikTok link, paste the full video URL");
    }

    /** The Location header of a redirect response, empty otherwise. Package-private so tests can stub it. */
    Optional<URI> fetchRedirect(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(5))
            .header("User-Agent", "Mozilla/5.0")
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 3) {
                return Optional.empty();
            }
            return response.headers().firstValue("Location").map(URI::create);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Failed to resolve TikTok short link {}: {}", uri, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static String pathOf(URI uri) {
        return uri.getPath() == null ? "" : uri.getPath();
    }

    private static String stripSlashes(String s) {
        return s.replaceAll("^/+|/+$", "");
    }

    private static Optional<String> queryParam(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null) {
            return Optional.empty();
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return Optional.of(pair.substring(eq + 1));
            }
        }
        return Optional.empty();
    }
}
