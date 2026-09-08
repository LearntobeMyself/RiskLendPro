package org.example.risklendpro.risk.client;

import org.example.risklendpro.api.contract.UserQueryApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "user-service", url = "${service.user.url}")
public interface UserServiceClient extends UserQueryApi {
}
