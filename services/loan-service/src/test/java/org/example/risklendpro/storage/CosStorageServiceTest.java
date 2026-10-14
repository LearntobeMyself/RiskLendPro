package org.example.risklendpro.storage;

import org.example.risklendpro.common.CatalogBusinessException;
import org.example.risklendpro.loan.catalog.CatalogErrorCodes;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CosStorageServiceTest {

    @Test
    void emptyCredentialsFailWithStorageError() {
        StorageProperties properties = new StorageProperties();
        CosStorageService service = new CosStorageService(properties);
        assertFalse(properties.credentialsReady());
        CatalogBusinessException ex = assertThrows(CatalogBusinessException.class,
                () -> service.presignPut("loan/cover/x.png", "image/png", Duration.ofMinutes(15)));
        assertEquals(CatalogErrorCodes.STORAGE_ERROR, ex.getCode());
        CatalogBusinessException exists = assertThrows(CatalogBusinessException.class,
                () -> service.exists("loan/cover/x.png"));
        assertEquals(CatalogErrorCodes.STORAGE_ERROR, exists.getCode());
        CatalogBusinessException put = assertThrows(CatalogBusinessException.class,
                () -> service.putObject("loan/cover/x.png", InputStream.nullInputStream(), "image/png", 0));
        assertEquals(CatalogErrorCodes.STORAGE_ERROR, put.getCode());
    }

    @Test
    void publicBucketReusesUrlWithoutCreatingClient() {
        StorageProperties properties = new StorageProperties();
        properties.setPrivateBucket(false);
        properties.setBucket("demo-bucket");
        properties.setRegion("ap-chengdu");
        properties.setCdnDomain("https://cdn.example.com/");
        CosStorageService service = new CosStorageService(properties);
        String first = service.accessUrl("loan/cover/a.jpg");
        String second = service.accessUrl("loan/cover/a.jpg");
        assertEquals("https://cdn.example.com/loan/cover/a.jpg", first);
        assertEquals(first, second);
    }
}
