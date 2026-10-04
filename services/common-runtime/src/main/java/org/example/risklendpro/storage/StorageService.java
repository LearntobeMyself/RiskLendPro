package org.example.risklendpro.storage;

import java.time.Duration;

public interface StorageService {

    PresignResult presignPut(String objectKey, String contentType, Duration expire);

    String accessUrl(String objectKey);

    boolean exists(String objectKey);

    void delete(String objectKey);
}
