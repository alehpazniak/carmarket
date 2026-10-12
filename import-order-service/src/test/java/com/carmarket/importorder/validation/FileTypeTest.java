package com.carmarket.importorder.validation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class FileTypeTest {

    public static final byte[] PDF = "%PDF-1.7\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1);
    public static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
    public static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    public static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8'};

    @Test
    void detectsAcceptedTypesByMagicBytes() {
        assertThat(FileType.detect(PDF)).contains(FileType.PDF);
        assertThat(FileType.detect(JPEG)).contains(FileType.JPEG);
        assertThat(FileType.detect(PNG)).contains(FileType.PNG);
        assertThat(FileType.detect(WEBP)).contains(FileType.WEBP);
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(FileType.detect("<html><body>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(FileType.detect(new byte[]{'G', 'I', 'F', '8', '9', 'a'})).isEmpty();
        assertThat(FileType.detect(new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'})).isEmpty();
        assertThat(FileType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isEmpty(); // truncated
        assertThat(FileType.detect(new byte[0])).isEmpty();
    }
}
