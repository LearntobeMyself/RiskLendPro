package org.example.risklendpro.risk.blacklist;

import org.example.risklendpro.risk.blacklist.BlacklistAddRequest;
import org.example.risklendpro.risk.blacklist.BlacklistAddResponse;

public interface BlacklistService {
    BlacklistAddResponse addBlacklist(BlacklistAddRequest request);
}
