package com.carmarket.car.service;

import com.carmarket.car.entity.ListingStatus;
import com.carmarket.car.kafka.CarEventProducer;
import com.carmarket.car.mapper.CarListingMapper;
import com.carmarket.car.repository.CarListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Dealer page images in S3 and the public "Oferta" list of a seller. */
class DealerMediaTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    private final S3Client s3 = mock(S3Client.class);
    private final S3Service service = new S3Service(s3);
    private final UUID dealer = UUID.randomUUID();
    private final String prefix = "https://bucket.s3.eu-central-1.amazonaws.com/";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "bucket", "bucket");
        ReflectionTestUtils.setField(service, "region", "eu-central-1");
    }

    @Test
    void detectsTheTypeFromTheBytesNotTheHeader() {
        assertThat(S3Service.imageType(PNG)).containsExactly("image/png", "png");
        assertThat(S3Service.imageType(JPEG)).containsExactly("image/jpeg", "jpg");
        assertThat(S3Service.imageType(WEBP)).containsExactly("image/webp", "webp");
        assertThat(S3Service.imageType("<svg onload=alert(1)>".getBytes())).isNull();
        assertThat(S3Service.imageType(new byte[0])).isNull();
    }

    @Test
    void uploadsUnderTheDealersOwnPrefix() {
        String url = service.uploadDealerImage(dealer, new MockMultipartFile("file", "logo.svg", "image/svg+xml", PNG));

        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(put.capture(), any(RequestBody.class));
        assertThat(put.getValue().key()).matches("dealers/" + dealer + "/[0-9a-f-]{36}\\.png");
        assertThat(put.getValue().contentType()).isEqualTo("image/png");
        assertThat(url).isEqualTo(prefix + put.getValue().key());
    }

    @Test
    void rejectsNonImagesAndEmptyFiles() {
        assertThatThrownBy(() -> service.uploadDealerImage(dealer,
            new MockMultipartFile("file", "x.png", "image/png", "<html>".getBytes())))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.uploadDealerImage(dealer,
            new MockMultipartFile("file", "x.png", "image/png", new byte[0])))
            .isInstanceOf(IllegalArgumentException.class);
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void deletesOnlyTheDealersOwnImages() {
        assertThat(service.deleteDealerImage(dealer, prefix + "dealers/" + dealer + "/a.png")).isTrue();
        verify(s3).deleteObject(any(DeleteObjectRequest.class));

        UUID other = UUID.randomUUID();
        assertThat(service.deleteDealerImage(dealer, prefix + "dealers/" + other + "/a.png")).isFalse();
        assertThat(service.deleteDealerImage(dealer, prefix + "cars/" + UUID.randomUUID() + "/a.png")).isFalse();
        assertThat(service.deleteDealerImage(dealer, prefix + "dealers/" + dealer + "/../../cars/x.png")).isFalse();
        assertThat(service.deleteDealerImage(dealer, null)).isFalse();
    }

    @Test
    void sellerListingsAreActiveOnlyWithASafeSortAndPageSize() {
        CarListingRepository repository = mock(CarListingRepository.class);
        CarListingService listings = new CarListingService(repository, mock(CarListingMapper.class),
            mock(CarEventProducer.class), service, mock(VideoLinkService.class));
        when(repository.findBySellerIdAndStatus(eq(dealer), eq(ListingStatus.ACTIVE), any(Pageable.class)))
            .thenReturn(Page.empty());

        listings.getSellerListings(dealer, PageRequest.of(1, 500, Sort.by("sellerId")));
        listings.getSellerListings(dealer, PageRequest.of(0, 24, Sort.by(Sort.Direction.ASC, "price")));

        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, org.mockito.Mockito.times(2))
            .findBySellerIdAndStatus(eq(dealer), eq(ListingStatus.ACTIVE), pages.capture());
        assertThat(pages.getAllValues().get(0).getPageSize()).isEqualTo(48);
        assertThat(pages.getAllValues().get(0).getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(pages.getAllValues().get(1).getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "price"));
    }
}
