package com.ke.bella.openapi.apikey;

import com.ke.bella.openapi.Operator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotEmpty;

/**
 * API Key所有权转移操作请求
 *
 * @author claude
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TransferApikeyOwnerOp extends Operator {

    /**
     * API Key编码
     */
    @NotEmpty(message = "API Key编码不能为空")
    private String akCode;

    /**
     * 目标用户ID (可选，与其他字段二选一)
     */
    private Long targetUserId;

    /**
     * 目标用户来源 (如: github, google, cas等)
     */
    private String targetUserSource;

    /**
     * 目标用户来源ID (即source_id，也就是owner_code)
     */
    private String targetUserSourceId;

    /**
     * 目标用户邮箱 (与targetUserSource配合使用)
     */
    private String targetUserEmail;

    /**
     * 转移原因
     */
    private String transferReason;
}