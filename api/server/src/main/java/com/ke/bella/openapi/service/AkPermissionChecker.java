package com.ke.bella.openapi.service;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.AkOperation;
import com.ke.bella.openapi.apikey.AkPermissionMatrix;
import com.ke.bella.openapi.apikey.AkRelation;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.tables.pojos.ApikeyDB;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static com.ke.bella.openapi.common.EntityConstants.CONSOLE;
import static com.ke.bella.openapi.common.EntityConstants.ORG;
import static com.ke.bella.openapi.common.EntityConstants.PERSON;
import static com.ke.bella.openapi.common.EntityConstants.SYSTEM;

@Component
public class AkPermissionChecker {

    /** operator（Console 登录用户）作为 AK 所有者时，可执行的操作集合 */
    private static final Set<AkOperation> OPERATOR_OWNER_OPS = Collections.unmodifiableSet(
            EnumSet.of(
                    AkOperation.QUERY,
                    AkOperation.RESET,
                    AkOperation.RENAME,
                    AkOperation.CERTIFY,
                    AkOperation.UPDATE_QPS,
                    AkOperation.CHANGE_STATUS,
                    AkOperation.TRANSFER,
                    AkOperation.VIEW_TRANSFER_HISTORY
            )
    );

    /**
     * 主入口：接受 ApikeyDB（从 queryByUniqueKey 得到）
     */
    public void check(ApikeyDB targetDb, AkOperation operation) {
        ApikeyInfo caller = EndpointContext.getApikeyIgnoreNull();

        if (caller == null) {
            checkOperatorPermission(targetDb, operation);
            return;
        }

        // system ownerType：目标非 system 则全放行，否则拒绝
        if (SYSTEM.equals(caller.getOwnerType())) {
            if (SYSTEM.equals(targetDb.getOwnerType())) {
                throw new BellaException.AuthorizationException("没有操作权限");
            }
            return;
        }

        AkRelation relation = resolveRelation(caller, targetDb);
        if (!AkPermissionMatrix.isAllowed(caller.getRoleCode(), relation, operation)) {
            throw new BellaException.AuthorizationException("没有操作权限");
        }
    }

    /**
     * 重载：接受 ApikeyInfo（从 queryByCode 得到，transferApikeyOwner / getTransferHistory 使用）
     */
    public void check(ApikeyInfo targetInfo, AkOperation operation) {
        ApikeyDB db = new ApikeyDB();
        db.setOwnerType(targetInfo.getOwnerType());
        db.setOwnerCode(targetInfo.getOwnerCode());
        check(db, operation);
    }

    /**
     * operator（Console 登录用户，无 AK）的独立权限分支。
     * ownerCode == userId 且 ownerType ∈ {PERSON, CONSOLE} → 允许 OPERATOR_OWNER_OPS 中的操作。
     * 其余情况拒绝（预留：未来 Manager 关系在此扩展）。
     */
    private void checkOperatorPermission(ApikeyDB targetDb, AkOperation operation) {
        Operator op = BellaContext.getOperator();
        String userId = op.getUserId().toString();

        boolean isOwner = (PERSON.equals(targetDb.getOwnerType()) || CONSOLE.equals(targetDb.getOwnerType()))
                && userId.equals(targetDb.getOwnerCode());

        if (isOwner) {
            if (!OPERATOR_OWNER_OPS.contains(operation)) {
                throw new BellaException.AuthorizationException("没有操作权限");
            }
            return;
        }

        // 非自己的 AK：当前不允许（TODO: isManager 时开放部分权限）
        throw new BellaException.AuthorizationException("没有操作权限");
    }

    /**
     * 解析调用方 AK 与目标 AK 的关系。
     * 优先级：OWNER > MANAGER（预留）> SAME_ORG > UNRELATED
     */
    AkRelation resolveRelation(ApikeyInfo caller, ApikeyDB target) {
        if (isOwner(caller, target)) {
            return AkRelation.OWNER;
        }

        // MANAGER 预留扩展点：实现 isManager() 后取消注释
        // if (isManager(caller, target)) { return AkRelation.MANAGER; }

        if (isSameOrg(caller, target)) {
            return AkRelation.SAME_ORG;
        }

        return AkRelation.UNRELATED;
    }

    private boolean isOwner(ApikeyInfo caller, ApikeyDB target) {
        return caller.getOwnerCode().equals(target.getOwnerCode())
                && caller.getOwnerType().equals(target.getOwnerType());
    }

    /**
     * SAME_ORG 判断：保留现有 TODO 结构，orgCodes 始终为空集，当前不会命中。
     * 未来在此填充 org 查询逻辑即可。
     */
    private boolean isSameOrg(ApikeyInfo caller, ApikeyDB target) {
        if (!ORG.equals(target.getOwnerType())) {
            return false;
        }
        // TODO: 获取调用方所属的所有 orgCodes
        Set<String> orgCodes = new HashSet<>();
        return orgCodes.contains(target.getOwnerCode());
    }
}
