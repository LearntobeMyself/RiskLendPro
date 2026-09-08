package org.example.risklendpro.api.contract;

import org.example.risklendpro.api.dto.LoanBehaviorSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

public interface LoanQueryApi {

    @GetMapping("/internal/loans/users/{userId}/behavior")
    LoanBehaviorSnapshot getLoanBehavior(@PathVariable("userId") Long userId);
}
