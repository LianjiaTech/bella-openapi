package com.ke.bella.openapi.space;

import com.ke.bella.openapi.Operator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotEmpty;

/**
 * function: 更新团队成员角色
 *
 * @author chenhongliang001
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateMemberRoleOp extends Operator {

    /**
     * 成员id
     */
    @NotEmpty(message = "memberUid不能为空")
    private String memberUid;

    /**
     * 空间编码
     */
    @NotEmpty(message = "spaceCode不能为空")
    private String spaceCode;

    /**
     * 角色编码
     */
    @NotEmpty(message = "roleCode不能为空")
    private String roleCode;

}
