package com.ke.bella.openapi.space;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * function:
 *
 * @author chenhongliang001
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class Member {

    private String spaceCode;

    private String roleCode;

    private String memberName;

    private String memberUid;

}
