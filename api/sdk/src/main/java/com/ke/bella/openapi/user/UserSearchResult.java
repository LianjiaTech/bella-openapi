package com.ke.bella.openapi.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户搜索结果
 *
 * @author claude
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 用户ID
     */
    private Long id;
    
    /**
     * 用户名
     */
    private String userName;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 用户来源
     */
    private String source;
    
    /**
     * 来源ID
     */
    private String sourceId;
}