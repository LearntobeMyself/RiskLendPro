package org.example.risklendpro.api.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BorrowGateResultTest {

    @Test
    void allowAndDenyFlags() {
        assertTrue(BorrowGateResult.allow("a1", "id").allowed());
        assertFalse(BorrowGateResult.deny("NO_CREDIT", "no", null, null).allowed());
    }
}
