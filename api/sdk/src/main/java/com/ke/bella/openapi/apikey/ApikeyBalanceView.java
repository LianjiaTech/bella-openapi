package com.ke.bella.openapi.apikey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApikeyBalanceView implements Serializable {
    private static final long serialVersionUID = 1L;

    private String akCode;
    private String month;
    private BigDecimal cost;
    private BigDecimal quota;
    private BigDecimal balance;
}
