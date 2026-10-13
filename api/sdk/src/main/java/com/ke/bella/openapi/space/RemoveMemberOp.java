package com.ke.bella.openapi.space;

import com.ke.bella.openapi.Operator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotEmpty;

/**
 * function: 移除团队成员
 *
 * @author chenhongliang001
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class RemoveMemberOp extends Operator {

    /**
     * 成员id
     */
    @NotEmpty(message = "memberUid不能为空")
    private String memberUid;

    /**
     * 编码
     */
    @NotEmpty(message = "spaceCode不能为空")
    private String spaceCode;

}
