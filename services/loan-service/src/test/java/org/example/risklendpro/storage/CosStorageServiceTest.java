package org.example.risklendpro.storage;

import org.example.risklendpro.common.CatalogBusinessException;
import org.example.risklendpro.loan.catalog.CatalogErrorCodes;
import org.junit.jupiter.api.Test;

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
    }
}
