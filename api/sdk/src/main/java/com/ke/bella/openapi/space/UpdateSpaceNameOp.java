package com.ke.bella.openapi.space;

import com.ke.bella.openapi.Operator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

/**
 * function:
 *
 * @author chenhongliang001
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSpaceNameOp extends Operator {

    /**
     * 空间编码
     */
    @NotEmpty(message = "spaceCode不能为空")
    private String spaceCode;

    /**
     * 空间名称
     */
    @Size(max = 128, message = "spaceName不能超过128个字符")
    private String spaceName;

}
