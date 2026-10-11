package com.carmarket.importorder;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Storage configured with dummy values; S3Client / S3Presigner are mocks, no bucket is touched. */
@TestPropertySource(properties = {
    "import-order.s3.bucket=test-bucket",
    "import-order.s3.region=eu-central-1",
    "import-order.s3.access-key-id=test-access-key",
    "import-order.s3.secret-access-key=test-secret-key",
})
public abstract class StorageEnabledTestBase extends IntegrationTestBase {

    @MockBean
    protected S3Client s3;

    @MockBean
    protected S3Presigner presigner;

    @BeforeEach
    void stubStorage() {
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenAnswer(invocation -> {
            GetObjectPresignRequest request = invocation.getArgument(0);
            PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
            when(presigned.url()).thenReturn(new URL("https://test-bucket.s3.eu-central-1.amazonaws.com/"
                + request.getObjectRequest().key() + "?X-Amz-Signature=test"));
            when(presigned.expiration()).thenReturn(Instant.now().plus(5, ChronoUnit.MINUTES));
            return presigned;
        });
        when(s3.listObjectsV2(any(ListObjectsV2Request.class)))
            .thenReturn(ListObjectsV2Response.builder().isTruncated(false).build());
    }
}
