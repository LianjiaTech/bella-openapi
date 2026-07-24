package com.ke.bella.openapi.space;

import com.ke.bella.openapi.Operator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * function: 创建角色
 *
 * @author chenhongliang001
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class CreateRoleOp extends Operator {

    /**
     * 空间编码
     */
    @NotEmpty(message = "spaceCode不能为空")
    @Size(max = 64, message = "spaceCode不能超过64个字符")
    private String spaceCode;

    /**
     * 角色集合
     */
    @NotEmpty(message = "roles不能为空")
    private List<CreateRoleDetail> roles;

}
