package org.example.risklendpro.loan.catalog;

import org.example.risklendpro.loan.catalog.entity.LoanImage;
import org.example.risklendpro.loan.catalog.vo.ImageVO;
import org.example.risklendpro.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogAssemblerTest {

    @Mock StorageService storageService;

    @Test
    void imageFailureDoesNotBreakCatalogResponse() {
        when(storageService.accessUrl("cover.jpg"))
                .thenThrow(new IllegalStateException("object storage unavailable"));
        CatalogAssembler assembler = new CatalogAssembler(storageService);
        LoanImage image = new LoanImage();
        image.setId(1L);
        image.setImageType("COVER");
        image.setSort(1);
        image.setObjectKey("cover.jpg");

        ImageVO result = assembler.image(image, true);

        assertEquals(1L, result.getImageId());
        assertEquals("cover.jpg", result.getObjectKey());
        assertNull(result.getUrl());
    }
}
