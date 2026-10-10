package com.carmarket.car.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("VideoLinkService Test")
class VideoLinkServiceTest {

    private final VideoLinkService service = new VideoLinkService();

    @ParameterizedTest
    @CsvSource({
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ, https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=10, https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://m.youtube.com/watch?v=dQw4w9WgXcQ, https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://youtu.be/dQw4w9WgXcQ?si=abc, https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "youtube.com/shorts/dQw4w9WgXcQ, https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://www.tiktok.com/@cars.pl/video/7301234567890123456?is_from_webapp=1, https://www.tiktok.com/@cars.pl/video/7301234567890123456",
        "https://m.tiktok.com/v/7301234567890123456.html, https://m.tiktok.com/v/7301234567890123456.html",
        "https://www.instagram.com/reel/C8xYz12AbCd/?igsh=MWQ1ZGUxMzBkMA==, https://www.instagram.com/reel/C8xYz12AbCd/",
        "https://instagram.com/reels/C8xYz12AbCd, https://www.instagram.com/reel/C8xYz12AbCd/",
        "https://www.instagram.com/tv/C8xYz12AbCd/, https://www.instagram.com/reel/C8xYz12AbCd/",
        "https://www.instagram.com/autokomis.pl/reel/C8xYz12AbCd/, https://www.instagram.com/reel/C8xYz12AbCd/",
        "https://www.instagram.com/p/C8xYz12AbCd/?img_index=1, https://www.instagram.com/p/C8xYz12AbCd/",
        "instagr.am/p/C8xYz12AbCd, https://www.instagram.com/p/C8xYz12AbCd/",
    })
    @DisplayName("normalizes YouTube, TikTok and Instagram links to a canonical URL")
    void normalizesKnownLinks(String input, String expected) {
        assertThat(service.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "not a url",
        "https://vimeo.com/123456",
        "https://www.youtube.com/watch?v=short",
        "https://www.youtube.com/channel/UC123",
        "https://www.tiktok.com/@cars.pl",
        "https://evil.com/@cars.pl/video/7301234567890123456",
        "https://tiktok.com.evil.com/@cars.pl/video/7301234567890123456",
        "https://www.instagram.com/autokomis.pl/",
        "https://www.instagram.com/stories/autokomis.pl/3412345678901234567/",
        "https://instagram.com.evil.com/reel/C8xYz12AbCd/",
    })
    @DisplayName("rejects links that are not a YouTube, TikTok or Instagram video")
    void rejectsUnsupportedLinks(String input) {
        assertThatThrownBy(() -> service.normalize(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("resolves a TikTok share link through its redirect")
    void resolvesShortLink() {
        List<URI> requested = new ArrayList<>();
        VideoLinkService stubbed = new VideoLinkService() {
            @Override
            Optional<URI> fetchRedirect(URI uri) {
                requested.add(uri);
                return Optional.of(URI.create("https://www.tiktok.com/@cars.pl/video/7301234567890123456?_r=1"));
            }
        };

        assertThat(stubbed.normalize("https://vm.tiktok.com/ZMabc123/"))
            .isEqualTo("https://www.tiktok.com/@cars.pl/video/7301234567890123456");
        assertThat(requested).containsExactly(URI.create("https://vm.tiktok.com/ZMabc123/"));
    }

    @Test
    @DisplayName("stops following a share link that redirects off tiktok.com")
    void doesNotFollowForeignRedirect() {
        List<URI> requested = new ArrayList<>();
        VideoLinkService stubbed = new VideoLinkService() {
            @Override
            Optional<URI> fetchRedirect(URI uri) {
                requested.add(uri);
                return Optional.of(URI.create("http://169.254.169.254/@x/video/7301234567890123456"));
            }
        };

        assertThatThrownBy(() -> stubbed.normalize("https://www.tiktok.com/t/ZTabc123/"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(requested).hasSize(1);
    }
}
