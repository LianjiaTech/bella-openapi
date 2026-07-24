package com.ke.bella.openapi.space;

import com.ke.bella.openapi.Operator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotEmpty;

/**
 * function: 团队拥有者变更
 *
 * @author chenhongliang001
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeSpaceOwnerOp extends Operator {

    /**
     * 空间编码
     */
    @NotEmpty(message = "spaceCode不能为空")
    private String spaceCode;

    /**
     * 新的拥有者系统号
     */
    @NotEmpty(message = "ownerUid不能为空")
    private String ownerUid;

}
