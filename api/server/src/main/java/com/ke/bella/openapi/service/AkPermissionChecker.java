package com.ke.bella.openapi.service;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.AkOperation;
import com.ke.bella.openapi.apikey.AkPermissionMatrix;
import com.ke.bella.openapi.apikey.AkRelation;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.db.repo.ApikeyRepo;
import com.ke.bella.openapi.tables.pojos.ApikeyDB;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
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

    @Autowired
    private ApikeyRepo apikeyRepo;

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
                    AkOperation.VIEW_TRANSFER_HISTORY,
                    AkOperation.CREATE_CHILD,
                    AkOperation.UPDATE_MANAGER
            )
    );

    /**
     * operator（Console 登录用户）作为 AK 管理者时，可执行的操作集合。
     * 与 OWNER 相比，不含 TRANSFER（转移所有权属于 owner 专属操作）。
     */
    private static final Set<AkOperation> OPERATOR_MANAGER_OPS = Collections.unmodifiableSet(
            EnumSet.of(
                    AkOperation.QUERY,
                    AkOperation.RESET,
                    AkOperation.RENAME,
                    AkOperation.CERTIFY,
                    AkOperation.UPDATE_QPS,
                    AkOperation.CHANGE_STATUS,
                    AkOperation.VIEW_TRANSFER_HISTORY,
                    AkOperation.CREATE_CHILD,
                    AkOperation.UPDATE_MANAGER
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
        db.setManagerCode(targetInfo.getManagerCode());
        check(db, operation);
    }

    /**
     * operator（Console 登录用户，无 AK）的独立权限分支。
     * - OWNER（ownerCode == userId，ownerType ∈ {PERSON, CONSOLE}）→ 允许 OPERATOR_OWNER_OPS
     * - MANAGER（manager_code == userId）→ 允许 OPERATOR_MANAGER_OPS（比 OWNER 少 TRANSFER）
     * - 其余情况拒绝
     */
    private void checkOperatorPermission(ApikeyDB targetDb, AkOperation operation) {
        Operator op = BellaContext.getOperator();
        if (op != null && isAdminOperator(op)) {
            String roleCode = String.valueOf(op.getOptionalInfo().get("roleCode"));
            if (AkPermissionMatrix.isAllowed(roleCode, AkRelation.UNRELATED, operation)) {
                return;
            }
        }
        String userId = op.getUserId().toString();

        boolean isOwner = (PERSON.equals(targetDb.getOwnerType()) || CONSOLE.equals(targetDb.getOwnerType()))
                && userId.equals(targetDb.getOwnerCode());
        if (isOwner) {
            if (!OPERATOR_OWNER_OPS.contains(operation)) {
                throw new BellaException.AuthorizationException("没有操作权限");
            }
            return;
        }

        boolean isManager = StringUtils.isNotEmpty(targetDb.getManagerCode())
                && targetDb.getManagerCode().equals(userId);
        if (isManager) {
            if (!OPERATOR_MANAGER_OPS.contains(operation)) {
                throw new BellaException.AuthorizationException("没有操作权限");
            }
            return;
        }

        // 子AK：向上追溯父AK的所有者/管理者权限
        if (StringUtils.isNotEmpty(targetDb.getParentCode())) {
            ApikeyDB parentDb = apikeyRepo.queryByUniqueKey(targetDb.getParentCode());
            if (parentDb != null) {
                boolean isParentOwner = (PERSON.equals(parentDb.getOwnerType()) || CONSOLE.equals(parentDb.getOwnerType()))
                        && userId.equals(parentDb.getOwnerCode());
                boolean isParentManager = StringUtils.isNotEmpty(parentDb.getManagerCode())
                        && parentDb.getManagerCode().equals(userId);
                if (isParentOwner) {
                    if (!OPERATOR_OWNER_OPS.contains(operation)) {
                        throw new BellaException.AuthorizationException("没有操作权限");
                    }
                    return;
                }
                if (isParentManager) {
                    if (!OPERATOR_MANAGER_OPS.contains(operation)) {
                        throw new BellaException.AuthorizationException("没有操作权限");
                    }
                    return;
                }
            }
        }

        throw new BellaException.AuthorizationException("没有操作权限");
    }

    private boolean isAdminOperator(Operator op) {
        if (op == null || op.getOptionalInfo() == null) {
            return false;
        }
        Object roleCode = op.getOptionalInfo().get("roleCode");
        return CONSOLE.equals(roleCode) || "all".equals(roleCode);
    }

    /**
     * 解析调用方 AK 与目标 AK 的关系。
     * 优先级：OWNER > MANAGER > SAME_ORG > UNRELATED
     * 若目标为子AK且直接关系不满足，向上追溯父AK的关系。
     */
    AkRelation resolveRelation(ApikeyInfo caller, ApikeyDB target) {
        if (isOwner(caller, target)) {
            return AkRelation.OWNER;
        }

        if (isManager(caller, target)) {
            return AkRelation.MANAGER;
        }

        if (isSameOrg(caller, target)) {
            return AkRelation.SAME_ORG;
        }

        // 子AK：向上追溯父AK的关系
        if (StringUtils.isNotEmpty(target.getParentCode())) {
            ApikeyDB parentDb = apikeyRepo.queryByUniqueKey(target.getParentCode());
            if (parentDb != null) {
                if (isOwner(caller, parentDb)) {
                    return AkRelation.OWNER;
                }
                if (isManager(caller, parentDb)) {
                    return AkRelation.MANAGER;
                }
            }
        }

        return AkRelation.UNRELATED;
    }

    private static final Set<String> PERSONAL_OWNER_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(PERSON, CONSOLE)));

    private boolean isOwner(ApikeyInfo caller, ApikeyDB target) {
        if (!caller.getOwnerCode().equals(target.getOwnerCode())) {
            return false;
        }
        // person 和 console 同属一个自然人，ownerType 可以互通
        if (PERSONAL_OWNER_TYPES.contains(caller.getOwnerType())
                && PERSONAL_OWNER_TYPES.contains(target.getOwnerType())) {
            return true;
        }
        return caller.getOwnerType().equals(target.getOwnerType());
    }

    /**
     * MANAGER 判断：target.manager_code 存的是人的 userCode，只有 person/console 类型的 AK
     * 才能代表某个自然人身份，org/system 类型不参与 manager 关系。
     */
    private boolean isManager(ApikeyInfo caller, ApikeyDB target) {
        if (!PERSONAL_OWNER_TYPES.contains(caller.getOwnerType())) {
            return false;
        }
        return StringUtils.isNotEmpty(target.getManagerCode())
                && target.getManagerCode().equals(caller.getOwnerCode());
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
