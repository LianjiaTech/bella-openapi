package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.annotations.BellaAPI;
import com.ke.bella.openapi.db.repo.UserRepo;
import com.ke.bella.openapi.user.UserSearchResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@BellaAPI
@RestController
@RequestMapping("/v1/userInfo")
@Tag(name = "用户信息查询")
public class UserInfoController {

    @Autowired
    private UserRepo userRepo;

    @GetMapping
    public Operator whoami() {
        return BellaContext.getOperator();
    }

    @GetMapping("/search")
    public List<UserSearchResult> searchUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "false") boolean excludeSelf) {
        
        Assert.isTrue(StringUtils.isNotBlank(keyword), "搜索关键词不能为空");
        Assert.isTrue(limit > 0 && limit <= 100, "返回数量必须在1-100之间");

        Operator operator = BellaContext.getOperator();

        if(!excludeSelf) {
            return userRepo.searchUsers(keyword.trim(), limit);
        }

        Long currentUserId = operator.getUserId();

        if(currentUserId.toString().equals(operator.getSourceId())) {
            return userRepo.searchUsers(keyword.trim(), limit, null, currentUserId.toString());
        }
        return userRepo.searchUsers(keyword.trim(), limit, currentUserId, null);
    }
}
