package com.ke.bella.openapi.space;

import com.ke.bella.openapi.Operator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * function:
 *
 * @author chenhongliang001
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class CreateMemberOp extends Operator {

    @NotEmpty(message = "spaceCode不能为空")
    @Size(max = 64, message = "spaceCode不能超过64个字符")
    private String spaceCode;

    /**
     * 角色编码
     */
    @NotEmpty(message = "roleCode不能为空")
    @Size(max = 64, message = "roleCode不能超过64个字符")
    private String roleCode;

    /**
     * 成员集合
     */
    private List<Member> members;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Member {

        /**
         * 成员id
         */
        private String memberUid;

        /**
         * 成员姓名
         */
        private String memberName;
    }

}
