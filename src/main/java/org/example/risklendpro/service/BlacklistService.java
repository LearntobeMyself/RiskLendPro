package org.example.risklendpro.service;

import org.example.risklendpro.pojo.request.BlacklistAddRequest;
import org.example.risklendpro.pojo.response.BlacklistAddResponse;

public interface BlacklistService {
    BlacklistAddResponse addBlacklist(BlacklistAddRequest request);
}
