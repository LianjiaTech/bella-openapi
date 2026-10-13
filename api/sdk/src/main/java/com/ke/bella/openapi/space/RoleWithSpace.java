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
public class RoleWithSpace {

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 空间编码
     */
    private String spaceCode;

    /**
     * 空间编码
     */
    private String spaceName;
}
