package com.ke.bella.openapi.space;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

/**
 * function: 创建角色明细
 *
 * @author chenhongliang001
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateRoleDetail {

    @NotEmpty(message = "roleCode不能为空")
    @Size(max = 64, message = "roleCode不能超过64个字符")
    private String roleCode;

    @NotEmpty(message = "roleName不能为空")
    @Size(max = 64, message = "roleName不能超过64个字符")
    private String roleName;

    @Size(max = 64, message = "roleDesc不能超过64个字符")
    private String roleDesc;
}
