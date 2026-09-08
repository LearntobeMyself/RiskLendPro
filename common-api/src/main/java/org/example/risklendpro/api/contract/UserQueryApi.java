package org.example.risklendpro.api.contract;

import org.example.risklendpro.api.dto.UserSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

public interface UserQueryApi {

    @GetMapping("/internal/users/{userId}")
    UserSummary getUser(@PathVariable("userId") Long userId);
}
