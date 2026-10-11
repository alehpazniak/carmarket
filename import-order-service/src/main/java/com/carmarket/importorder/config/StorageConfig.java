package com.carmarket.importorder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 beans, created only when bucket, region and both keys are set.
 * <p>
 * Credentials come only from import-order.s3.* via {@link StaticCredentialsProvider}, never the
 * default provider chain: on a dev machine that could silently pick up ~/.aws or car-service's
 * broader AWS_* keys and hide permission errors of the narrowly scoped import-orders IAM user.
 */
@Configuration
public class StorageConfig {

    @Bean(destroyMethod = "close")
    @Conditional(S3Configured.class)
    public S3Client importOrderS3Client(ImportOrderProperties properties) {
        ImportOrderProperties.S3 s3 = properties.s3();
        return S3Client.builder()
            .region(Region.of(s3.region()))
            .credentialsProvider(credentials(s3))
            .build();
    }

    @Bean(destroyMethod = "close")
    @Conditional(S3Configured.class)
    public S3Presigner importOrderS3Presigner(ImportOrderProperties properties) {
        ImportOrderProperties.S3 s3 = properties.s3();
        return S3Presigner.builder()
            .region(Region.of(s3.region()))
            .credentialsProvider(credentials(s3))
            .build();
    }

    private static StaticCredentialsProvider credentials(ImportOrderProperties.S3 s3) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKeyId(), s3.secretAccessKey()));
    }

    static class S3Configured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var env = context.getEnvironment();
            return new ImportOrderProperties.S3(
                env.getProperty("import-order.s3.bucket"),
                env.getProperty("import-order.s3.region"),
                env.getProperty("import-order.s3.access-key-id"),
                env.getProperty("import-order.s3.secret-access-key"),
                null).isConfigured();
        }
    }
}
