package org.example.risklendpro.service.impl;

import org.example.risklendpro.entity.credit.Blacklist;
import org.example.risklendpro.mapper.credit.BlacklistMapper;
import org.example.risklendpro.service.CreditScoreEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditScoreEngineImplBlacklistTest {

    @InjectMocks
    private CreditScoreEngineImpl creditScoreEngine;

    @Mock
    private BlacklistMapper blacklistMapper;

    @Test
    void checkBlacklist_prefersNameAreaOverNameOnlyWhenBothMatch() {
        List<Blacklist> records = new ArrayList<>();

        Blacklist nameOnlyRecord = new Blacklist();
        nameOnlyRecord.setName("孙*");
        nameOnlyRecord.setAreaCode("310000");
        nameOnlyRecord.setBirthYear(1981);
        records.add(nameOnlyRecord);

        Blacklist nameAreaRecord = new Blacklist();
        nameAreaRecord.setName("孙*");
        nameAreaRecord.setAreaCode("110112");
        nameAreaRecord.setBirthYear(1986);
        records.add(nameAreaRecord);

        when(blacklistMapper.selectAll()).thenReturn(records);

        CreditScoreEngine.BlacklistMatchResult result =
                creditScoreEngine.checkBlacklist("孙七", "110112198702150007");

        assertEquals(CreditScoreEngine.MatchLevel.NAME_AREA, result.getMatchLevel());
        assertEquals(nameAreaRecord, result.getRecord());
    }

    @Test
    void checkBlacklist_returnsFullMatchImmediately() {
        List<Blacklist> records = new ArrayList<>();

        Blacklist fullRecord = new Blacklist();
        fullRecord.setName("孙*");
        fullRecord.setAreaCode("110112");
        fullRecord.setBirthYear(1987);
        records.add(fullRecord);

        Blacklist nameAreaRecord = new Blacklist();
        nameAreaRecord.setName("孙*");
        nameAreaRecord.setAreaCode("110112");
        nameAreaRecord.setBirthYear(1986);
        records.add(nameAreaRecord);

        when(blacklistMapper.selectAll()).thenReturn(records);

        CreditScoreEngine.BlacklistMatchResult result =
                creditScoreEngine.checkBlacklist("孙七", "110112198702150007");

        assertEquals(CreditScoreEngine.MatchLevel.FULL, result.getMatchLevel());
        assertEquals(fullRecord, result.getRecord());
    }
}
