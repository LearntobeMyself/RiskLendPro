package org.example.risklendpro.storage;

import java.io.InputStream;
import java.time.Duration;

public interface StorageService {

    PresignResult presignPut(String objectKey, String contentType, Duration expire);

    void putObject(String objectKey, InputStream content, String contentType, long contentLength);

    String accessUrl(String objectKey);

    boolean exists(String objectKey);

    void delete(String objectKey);
}
