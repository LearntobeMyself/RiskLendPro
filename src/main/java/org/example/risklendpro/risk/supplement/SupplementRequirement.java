package org.example.risklendpro.risk.supplement;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplementRequirement {
    private String code;
    private String label;
    private String description;
    private boolean required;
}
