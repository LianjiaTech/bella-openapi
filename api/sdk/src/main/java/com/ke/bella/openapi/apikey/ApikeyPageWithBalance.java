package com.ke.bella.openapi.apikey;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ApikeyPageWithBalance extends ApikeyInfo {
    private static final long serialVersionUID = 1L;

    private ApikeyBalanceView balance;
}
