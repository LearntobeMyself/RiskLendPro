package org.example.risklendpro.api.contract;

import org.example.risklendpro.api.dto.CreditLimitGrantCommand;
import org.example.risklendpro.api.dto.CreditLimitSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

public interface CreditLimitApi {

    @GetMapping("/internal/credit-limits/users/{userId}")
    CreditLimitSnapshot getCreditLimit(@PathVariable("userId") Long userId);

    @PostMapping("/internal/credit-limits/grants")
    CreditLimitSnapshot grantCreditLimit(@RequestBody CreditLimitGrantCommand command);
}
