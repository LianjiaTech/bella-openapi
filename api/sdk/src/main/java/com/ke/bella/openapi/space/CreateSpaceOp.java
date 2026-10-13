package com.ke.bella.openapi.space;

import com.ke.bella.openapi.Operator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

/**
 * function: 创建团队参数
 *
 * @author chenhongliang001
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class CreateSpaceOp extends Operator {

    /**
     * 空间名称
     */
    @NotEmpty(message = "spaceName不能为空")
    @Size(max = 128, message = "spaceName不能超过128个字符")
    private String spaceName;

    /**
     * 空间描述
     */
    @Size(max = 255, message = "spaceDescription不能超过255个字符")
    private String spaceDescription;

    /**
     * 空间编码
     */
    @Size(max = 64, message = "spaceCode不能超过64个字符")
    private String spaceCode;

    /**
     * 空间拥有者id
     */
    @NotEmpty(message = "ownerUid不能为空")
    @Size(max = 64, message = "ownerUid不能超过64个字符")
    private String ownerUid;

    /**
     * 空间拥有者姓名
     */
    @NotEmpty(message = "ownerName不能为空")
    @Size(max = 64, message = "ownerName不能超过64个字符")
    private String ownerName;

}
