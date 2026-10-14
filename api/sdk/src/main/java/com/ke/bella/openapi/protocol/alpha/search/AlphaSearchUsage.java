package com.ke.bella.openapi.protocol.alpha.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlphaSearchUsage implements Serializable {
    private int requestCount;
}
