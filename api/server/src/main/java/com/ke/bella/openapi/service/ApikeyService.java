package com.ke.bella.openapi.service;

import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CachePenetrationProtect;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.CacheUpdate;
import com.alicp.jetcache.anno.Cached;
import com.alicp.jetcache.template.QuickConfig;
import com.google.common.collect.Sets;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.PermissionCondition;
import com.ke.bella.openapi.apikey.ApikeyCreateOp;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.apikey.ApikeyOps;
import com.ke.bella.openapi.apikey.ApikeyTransferLog;
import com.ke.bella.openapi.apikey.SubApikeyUpdateOp;
import com.ke.bella.openapi.apikey.TransferApikeyOwnerOp;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.event.ApiKeyTransferEvent;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.db.repo.ApikeyCostRepo;
import com.ke.bella.openapi.db.repo.ApikeyRepo;
import com.ke.bella.openapi.db.repo.ApikeyRoleRepo;
import com.ke.bella.openapi.db.repo.ApikeyTransferLogRepo;
import com.ke.bella.openapi.db.repo.UserRepo;
import com.ke.bella.openapi.db.repo.Page;
import com.ke.bella.openapi.safety.ISafetyAuditService;
import com.ke.bella.openapi.tables.pojos.ApikeyDB;
import com.ke.bella.openapi.tables.pojos.ApikeyMonthCostDB;
import com.ke.bella.openapi.tables.pojos.ApikeyRoleDB;
import com.ke.bella.openapi.tables.pojos.UserDB;
import com.ke.bella.openapi.utils.EncryptUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.MatchUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.ke.bella.openapi.common.EntityConstants.ACTIVE;
import static com.ke.bella.openapi.common.EntityConstants.CONSOLE;
import static com.ke.bella.openapi.common.EntityConstants.INACTIVE;
import static com.ke.bella.openapi.common.EntityConstants.ORG;
import static com.ke.bella.openapi.common.EntityConstants.PERSON;
import static com.ke.bella.openapi.common.EntityConstants.SYSTEM;

@Slf4j
@Component
public class ApikeyService {
    @Autowired
    private ApikeyRepo apikeyRepo;

    @Autowired
    private ApikeyRoleRepo apikeyRoleRepo;

    @Autowired
    private ApikeyCostRepo apikeyCostRepo;

    @Autowired
    private ApikeyTransferLogRepo apikeyTransferLogRepo;

    @Autowired
    private UserRepo userRepo;

    @Value("${apikey.basic.monthQuota:200}")
    private int basicMonthQuota;

    @Value("${apikey.basic.roleCode:low}")
    private String basicRoleCode;

    @Value("${apikey.basic.safetyLevel:40}")
    private byte basicSafetyLevel;

    @Value("#{'${apikey.basic.childRoleCodes:low,high}'.split (',')}")
    private List<String> childRoleCodes;
    @Value("${cache.use:true}")
    private boolean useCache;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private ISafetyAuditService safetyAuditService;
    private static final String apikeyCacheKey = "apikey:sha:";

    @PostConstruct
    public void postConstruct() {
        QuickConfig quickConfig = QuickConfig.newBuilder(apikeyCacheKey)
                .cacheNullValue(true)
                .cacheType(CacheType.LOCAL)
                .expire(Duration.ofSeconds(30))
                .localExpire(Duration.ofSeconds(30))
                .localLimit(500)
                .penetrationProtect(true)
                .penetrationProtectTimeout(Duration.ofSeconds(10))
                .build();
        cacheManager.getOrCreateCache(quickConfig);
    }

    @Transactional
    public String apply(ApikeyOps.ApplyOp op) {
        String ak = UUID.randomUUID().toString();
        String sha = EncryptUtils.sha256(ak);
        String display = EncryptUtils.desensitize(ak);
        if(StringUtils.isNotEmpty(op.getRoleCode())) {
            Assert.isTrue(childRoleCodes.contains(op.getRoleCode()), "role code不可使用");
        }
        ApikeyDB db = new ApikeyDB();
        db.setAkSha(sha);
        db.setAkDisplay(display);
        db.setOwnerType(op.getOwnerType());
        db.setOwnerCode(op.getOwnerCode());
        db.setOwnerName(op.getOwnerName());
        db.setRoleCode(StringUtils.isEmpty(op.getRoleCode()) ? basicRoleCode : op.getRoleCode());
        db.setSafetyLevel(basicSafetyLevel);
        db.setMonthQuota(op.getMonthQuota() == null ? BigDecimal.valueOf(basicMonthQuota) : op.getMonthQuota());
        db.setName(op.getName());
        db.setRemark(op.getRemark());
        apikeyRepo.insert(db);
        return ak;
    }

    @Transactional
    public String createByParentCode(ApikeyCreateOp op) {
        ApikeyInfo cur = EndpointContext.getApikey();
        ApikeyInfo apikey = queryByCode(op.getParentCode(), true);
        if(!apikey.getOwnerCode().equals(cur.getOwnerCode())) {
            throw new ChannelException.AuthorizationException("请求使用AK和父AK必须属于同一个人");
        }
        Assert.notNull(apikey, "父AK不存在或已停用");
        Assert.isTrue(StringUtils.isEmpty(apikey.getParentCode()), "当前AK无创建子AK权限");
        if(StringUtils.isNotEmpty(op.getRoleCode())) {
            apikeyRoleRepo.checkExist(op.getRoleCode(), true);
        }
        Assert.isTrue(op.getMonthQuota() == null || op.getMonthQuota().doubleValue() <= apikey.getMonthQuota().doubleValue(), "配额超出ak的最大配额");
        Assert.isTrue(op.getSafetyLevel() <= apikey.getSafetyLevel(), "安全等级超出ak的最高等级");
        String ak = UUID.randomUUID().toString();
        String sha = EncryptUtils.sha256(ak);
        String display = EncryptUtils.desensitize(ak);
        ApikeyDB db = new ApikeyDB();
        db.setAkSha(sha);
        db.setAkDisplay(display);
        db.setParentCode(op.getParentCode());
        db.setOutEntityCode(op.getOutEntityCode());
        db.setOwnerType(apikey.getOwnerType());
        db.setOwnerCode(apikey.getOwnerCode());
        db.setOwnerName(apikey.getOwnerName());
        db.setRoleCode(op.getRoleCode());
        db.setMonthQuota(op.getMonthQuota());
        db.setSafetyLevel(op.getSafetyLevel());
        db.setName(op.getName());
        db.setRemark(op.getRemark());
        db = apikeyRepo.insert(db);
        if(CollectionUtils.isNotEmpty(op.getPaths())) {
           boolean match = op.getPaths().stream().allMatch(url -> apikey.getRolePath().getIncluded().stream().anyMatch(pattern -> MatchUtils.matchUrl(pattern, url))
                    && apikey.getRolePath().getExcluded().stream().noneMatch(pattern -> MatchUtils.matchUrl(pattern, url)));
            Assert.isTrue(match, "超出ak的权限范围");
            updateRole(ApikeyOps.RoleOp.builder().code(db.getCode()).paths(op.getPaths()).build());
        }
        return ak;
    }

    @Transactional
    public boolean updateSubApikey(SubApikeyUpdateOp op) {
        ApikeyInfo cur = EndpointContext.getApikey();
        ApikeyInfo subApikey = apikeyRepo.queryByCode(op.getCode());
        ApikeyInfo apikey = queryByCode(subApikey.getParentCode(), false);
        Assert.notNull(apikey, "只可以修改子ak");
        if(!apikey.getOwnerCode().equals(cur.getOwnerCode())) {
            throw new ChannelException.AuthorizationException("只能修改自己的apikey");
        }
        if(StringUtils.isNotEmpty(op.getRoleCode())) {
            apikeyRoleRepo.checkExist(op.getRoleCode(), true);
        }
        Assert.isTrue(op.getMonthQuota() == null || op.getMonthQuota().doubleValue() <= apikey.getMonthQuota().doubleValue(), "配额超出ak的最大配额");
        Assert.isTrue(op.getSafetyLevel() <= apikey.getSafetyLevel(), "安全等级超出ak的最高等级");
        apikeyRepo.update(op, op.getCode());
        if(CollectionUtils.isNotEmpty(op.getPaths())) {
            boolean match = op.getPaths().stream().allMatch(url -> apikey.getRolePath().getIncluded().stream().anyMatch(pattern -> MatchUtils.matchUrl(pattern, url))
                    && apikey.getRolePath().getExcluded().stream().noneMatch(pattern -> MatchUtils.matchUrl(pattern, url)));
            Assert.isTrue(match, "超出ak的权限范围");
            updateRole(ApikeyOps.RoleOp.builder().code(op.getCode()).paths(op.getPaths()).build());
        }
        return true;
    }

    @Transactional
    public String reset(ApikeyOps.CodeOp op) {
        apikeyRepo.checkExist(op.getCode(), true);
        checkPermission(op.getCode());
        String ak = UUID.randomUUID().toString();
        String sha = EncryptUtils.sha256(ak);
        String display = EncryptUtils.desensitize(ak);
        ApikeyDB db = new ApikeyDB();
        db.setAkSha(sha);
        db.setAkDisplay(display);
        apikeyRepo.update(db, op.getCode());
        return ak;
    }

    @Transactional
    public void rename(ApikeyOps.NameOp op) {
        apikeyRepo.update(op, op.getCode());
    }

    @Transactional
    public void bindService(ApikeyOps.ServiceOp op) {
        apikeyRepo.update(op, op.getCode());
    }

    @Transactional
    public void updateRole(ApikeyOps.RoleOp op) {
        apikeyRepo.checkExist(op.getCode(), true);
        checkPermission(op.getCode());
        if(StringUtils.isNotEmpty(op.getRoleCode())) {
            apikeyRoleRepo.checkExist(op.getRoleCode(), true);
        } else {
            ApikeyRoleDB roleDB = new ApikeyRoleDB();
            ApikeyInfo.RolePath rolePath = new ApikeyInfo.RolePath();
            rolePath.setIncluded(op.getPaths());
            roleDB.setPath(JacksonUtils.serialize(rolePath));
            roleDB = apikeyRoleRepo.insert(roleDB);
            op.setRoleCode(roleDB.getRoleCode());
        }
        apikeyRepo.update(op, op.getCode());
    }

    @Transactional
    public void certify(ApikeyOps.CertifyOp op) {
        apikeyRepo.checkExist(op.getCode(), true);
        checkPermission(op.getCode());
        Byte level = safetyAuditService.fetchLevelByCertifyCode(op.getCertifyCode());
        ApikeyDB db = new ApikeyDB();
        db.setCertifyCode(op.getCertifyCode());
        db.setSafetyLevel(level);
        apikeyRepo.update(db, op.getCode());
    }

    @Transactional
    public void updateQuota(ApikeyOps.QuotaOp op) {
        apikeyRepo.checkExist(op.getCode(), true);
        checkPermission(op.getCode());
        apikeyRepo.update(op, op.getCode());
    }

    @Transactional
    public void changeStatus(ApikeyOps.CodeOp op, boolean active) {
        apikeyRepo.checkExist(op.getCode(), true);
        checkPermission(op.getCode());
        String status = active ? ACTIVE : INACTIVE;
        apikeyRepo.updateStatus(op.getCode(), status);
    }

    public ApikeyInfo verifyAuth(String auth) {
        String ak;
        if(auth.startsWith("Bearer ")) {
            ak = auth.substring(7);
        } else {
            ak = auth;
        }
        String sha = EncryptUtils.sha256(ak);
        ApikeyInfo info = queryBySha(sha, true);
        if(info == null) {
            String display = EncryptUtils.desensitizeByLength(auth);
            String displayAk = EncryptUtils.desensitize(ak);
            throw new ChannelException.AuthorizationException("api key不存在，请求的header为：" + display + ", apikey为：" + displayAk);
        }
        if(StringUtils.isNotEmpty(info.getParentCode())) {
            ApikeyInfo parent = queryByCode(info.getParentCode(), true);
            if(parent == null) {
                String display = EncryptUtils.desensitizeByLength(auth);
                String displayAk = EncryptUtils.desensitize(ak);
                throw new ChannelException.AuthorizationException("api key不存在，请求的header为：" + display + ", apikey为：" + displayAk);
            }
            info.setParentInfo(parent);
        }
        info.setApikey(ak);
        return info;
    }

    public ApikeyInfo queryBySha(String sha, boolean onlyActive) {
        ApikeyInfo apikeyInfo;
        if(useCache && onlyActive) {
            apikeyInfo = applicationContext.getBean(ApikeyService.class).queryWithCache(sha);
        } else {
            apikeyInfo = apikeyRepo.queryBySha(sha);
        }
        if(apikeyInfo != null) {
            if(apikeyInfo.getOwnerType().equals(PERSON)) {
                apikeyInfo.setUserId(Long.parseLong(apikeyInfo.getOwnerCode()));
            } else {
                apikeyInfo.setUserId(0L);
            }
        }
        return apikeyInfo;
    }

    public ApikeyInfo queryByCode(String code, boolean onlyActive) {
        ApikeyInfo apikeyInfo = apikeyRepo.queryByCode(code);
        if(apikeyInfo == null ||(onlyActive && apikeyInfo.getStatus().equals(INACTIVE))) {
            return null;
        }
        return apikeyInfo;
    }

    @Transactional
    @CacheUpdate(name = "apikey:cost:month:", key = "#akCode + ':' + #month", value = "#result")
    public BigDecimal recordCost(String akCode, String month, BigDecimal cost) {
        BigDecimal amount = apikeyCostRepo.queryCost(akCode, month);
        if(amount == null) {
            apikeyCostRepo.insert(akCode, month);
        }
        apikeyCostRepo.increment(akCode, month, cost);
        return apikeyCostRepo.queryCost(akCode, month);
    }

    @Cached(name = "apikey:cost:month:", key = "#akCode + ':' + #month", expire = 31 * 24 * 3600,
            condition = "T(com.ke.bella.openapi.utils.DateTimeUtils).isCurrentMonth(#month)")
    @CachePenetrationProtect(timeout = 5)
    public BigDecimal loadCost(String akCode, String month) {
        BigDecimal amount = apikeyCostRepo.queryCost(akCode, month);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public List<ApikeyMonthCostDB> queryBillingsByAkCode(String akCode) {
        return apikeyCostRepo.queryByAkCode(akCode);
    }

    private void checkPermission(String code) {
        ApikeyDB db = apikeyRepo.queryByUniqueKey(code);
        ApikeyInfo apikeyInfo = EndpointContext.getApikeyIgnoreNull();
        if(apikeyInfo == null) {
            Operator op = BellaContext.getOperator();
            Assert.isTrue((db.getOwnerType().equals(PERSON) || db.getOwnerType().equals(CONSOLE)) && db.getOwnerCode().equals(op.getUserId().toString()),
                    "没有操作权限");
            return;
        }
        if(apikeyInfo.getOwnerType().equals(SYSTEM)) {
            return;
        }
        //todo: 获取所有 org
        Set<String> orgCodes = new HashSet<>();
        if(db.getOwnerType().equals(SYSTEM)) {
            throw new ChannelException.AuthorizationException("没有操作权限");
        }
        if(db.getOwnerType().equals(ORG)) {
            validateOrgPermission(apikeyInfo, Sets.newHashSet(db.getOwnerCode()), orgCodes);
        } else {
            validateUserPermission(apikeyInfo, db.getOwnerCode());
        }
    }

    public Page<ApikeyDB> pageApikey(ApikeyOps.ApikeyCondition condition) {
        fillPermissionCode(condition, false);
        return apikeyRepo.pageAccessKeys(condition);
    }

    public void fillPermissionCode(PermissionCondition condition, boolean apikeyFirst) {
        ApikeyInfo apikeyInfo = EndpointContext.getApikeyIgnoreNull();
        Operator op = BellaContext.getOperatorIgnoreNull();
        if(apikeyInfo == null || (!apikeyFirst && op != null)) {
            if (op == null || CollectionUtils.isNotEmpty(condition.getOrgCodes())) {
                throw new ChannelException.AuthorizationException("没有操作权限");
            }
            if (StringUtils.isNotEmpty(condition.getPersonalCode())) {
                Assert.isTrue(op.getUserId().toString().equals(condition.getPersonalCode()), "没有操作权限");
            } else {
                condition.setPersonalCode(op.getUserId().toString());
            }
            return;
        }
        // TODO: 获取所有组织代码并填充到 orgCodes
        Set<String> orgCodes = new HashSet<>();

        if (StringUtils.isEmpty(condition.getPersonalCode())) {
            if(apikeyInfo.getOwnerType().equals(PERSON)) {
                condition.setPersonalCode(apikeyInfo.getOwnerCode());
            }
        } else {
            validateUserPermission(apikeyInfo, condition.getPersonalCode());
        }

        if (CollectionUtils.isEmpty(condition.getOrgCodes())) {
            condition.setOrgCodes(orgCodes);
        } else {
            validateOrgPermission(apikeyInfo, condition.getOrgCodes(), orgCodes);
        }
    }

    private void validateUserPermission(ApikeyInfo apikeyInfo, String personalCode) {
        if(apikeyInfo.getOwnerType().equals(SYSTEM) || ((apikeyInfo.getOwnerType().equals(PERSON) || apikeyInfo.getOwnerType().equals(CONSOLE)) && personalCode.equals(apikeyInfo.getOwnerCode()))) {
            return;
        }
        throw new ChannelException.AuthorizationException("没有操作权限");
    }

    private void validateOrgPermission(ApikeyInfo apikeyInfo, Set<String> conditionOrgCodes, Set<String> orgCodes) {
        if(apikeyInfo.getOwnerType().equals(SYSTEM) || CollectionUtils.isEmpty(conditionOrgCodes) || orgCodes.containsAll(conditionOrgCodes)) {
            return;
        }
        throw new ChannelException.AuthorizationException("没有操作权限");
    }

    @Cached(name = apikeyCacheKey, key = "#sha")
    public ApikeyInfo queryWithCache(String sha) {
        ApikeyInfo apikeyInfo = apikeyRepo.queryBySha(sha);
        if(apikeyInfo == null || (apikeyInfo.getStatus().equals(INACTIVE))) {
            return null;
        }
        return apikeyInfo;
    }

    /**
     * 转移API Key所有者
     * 注意：缓存清理操作在事务外执行，避免影响事务
     * 
     * @param op 转移操作参数
     * @param currentOperator 当前操作者
     * @return 是否成功
     */
    @Transactional
    public boolean transferApikeyOwner(TransferApikeyOwnerOp op, Operator currentOperator) {
        // 1. 验证API Key是否存在且为主API Key
        ApikeyInfo apikeyInfo = apikeyRepo.queryByCode(op.getAkCode());
        if (apikeyInfo == null) {
            throw new ChannelException.AuthorizationException("API Key不存在");
        }
        
        if (StringUtils.isNotEmpty(apikeyInfo.getParentCode())) {
            throw new ChannelException.AuthorizationException("子API Key不允许转移，只能转移主API Key");
        }
        
        if (!ACTIVE.equals(apikeyInfo.getStatus())) {
            throw new ChannelException.AuthorizationException("API Key状态不允许转移");
        }

        // 只有个人类型的API Key才能转移
        if (!PERSON.equals(apikeyInfo.getOwnerType())) {
            throw new ChannelException.AuthorizationException("只有个人类型的API Key才能转移");
        }

        // 2. 验证当前用户权限 (只有当前所有者可以转移)
        if (!apikeyInfo.getOwnerCode().equals(currentOperator.getUserId().toString())) {
            throw new ChannelException.AuthorizationException("只有API Key所有者才能执行转移操作");
        }

        // 3. 查找并验证目标用户
        UserDB targetUser = findAndValidateTargetUser(op);

        // 4. 新的owner_code保持与当前操作者相同的规则
        String newOwnerCode;
        if (StringUtils.equals(currentOperator.getSourceId(), String.valueOf(currentOperator.getUserId()))) {
            // 如果操作者使用sourceId规则，目标用户也使用sourceId
            newOwnerCode = targetUser.getSourceId();
        } else {
            // 如果操作者使用userId规则，目标用户也使用userId
            newOwnerCode = targetUser.getId().toString();
        }

        Assert.isTrue(!currentOperator.getUserId().toString().equals(newOwnerCode), "不能将ak转交给自己");

        // 5. 记录转移前的状态用于审计
        String fromOwnerType = apikeyInfo.getOwnerType();
        String fromOwnerCode = apikeyInfo.getOwnerCode();
        String fromOwnerName = apikeyInfo.getOwnerName();

        // 6. 更新主API Key和所有子API Key的所有者信息
        ApikeyDB updateDB = new ApikeyDB();
        updateDB.setOwnerType(PERSON);
        updateDB.setOwnerCode(newOwnerCode);
        updateDB.setOwnerName(StringUtils.defaultIfEmpty(targetUser.getUserName(), "用户" + targetUser.getId()));
        updateDB.setMuid(currentOperator.getUserId());
        updateDB.setMuName(currentOperator.getUserName());
        
        // 更新主API Key
        apikeyRepo.update(updateDB, op.getAkCode());
        
        // 批量更新所有子API Key
        apikeyRepo.batchUpdateByParentCode(updateDB, op.getAkCode());

        // 7. 记录转移日志
        ApikeyTransferLog transferLog = ApikeyTransferLog.builder()
                .akCode(op.getAkCode())
                .fromOwnerType(fromOwnerType)
                .fromOwnerCode(fromOwnerCode)
                .fromOwnerName(fromOwnerName)
                .toOwnerType(PERSON)
                .toOwnerCode(newOwnerCode)
                .toOwnerName(StringUtils.defaultIfEmpty(targetUser.getUserName(), "用户" + targetUser.getId()))
                .transferReason(StringUtils.defaultString(op.getTransferReason(), ""))
                .status("completed")
                .operatorUid(currentOperator.getUserId())
                .operatorName(currentOperator.getUserName())
                .build();
                
        apikeyTransferLogRepo.insertTransferLog(transferLog);
        
        // 8. 发布API Key转移事件（事务提交后自动处理）
        ApiKeyTransferEvent event = ApiKeyTransferEvent.of(op.getAkCode(), fromOwnerCode, fromOwnerName,
                                                          newOwnerCode, updateDB.getOwnerName(), 
                                                          StringUtils.defaultString(op.getTransferReason(), ""),
                                                          currentOperator.getUserId(), currentOperator.getUserName());
        eventPublisher.publishEvent(event);
        
        return true;
    }
    

    /**
     * 获取API Key转移历史
     *
     * @param akCode API Key编码
     * @return 转移历史列表
     */
    public List<ApikeyTransferLog> getTransferHistory(String akCode) {
        // 验证权限：只有API Key所有者或系统管理员可以查看转移历史
        ApikeyInfo apikeyInfo = apikeyRepo.queryByCode(akCode);
        if (apikeyInfo == null) {
            throw new ChannelException.AuthorizationException("API Key不存在");
        }

        ApikeyInfo currentApikey = EndpointContext.getApikey();
        if (!apikeyInfo.getOwnerCode().equals(currentApikey.getOwnerCode())
            && !SYSTEM.equals(currentApikey.getOwnerType())) {
            throw new ChannelException.AuthorizationException("没有权限查看转移历史");
        }

        return apikeyTransferLogRepo.queryByAkCode(akCode);
    }

    /**
     * 清除API Key相关缓存
     */
    @CacheInvalidate(name = apikeyCacheKey, key = "#sha")
    public void clearApikeyCache(String sha) {
        // 方法体为空，注解会处理缓存更新
    }

    /**
     * 查找并验证目标用户
     *
     * @param op 转移操作请求
     * @return 目标用户信息
     */
    private UserDB findAndValidateTargetUser(TransferApikeyOwnerOp op) {
        UserDB targetUser;
        
        // 方式1: 通过用户ID查找
        if (op.getTargetUserId() != null && op.getTargetUserId() > 0) {
            targetUser = userRepo.queryById(op.getTargetUserId());
        }
        // 方式2: 通过source + sourceId查找 
        else if (StringUtils.isNotEmpty(op.getTargetUserSource()) && StringUtils.isNotEmpty(op.getTargetUserSourceId())) {
            targetUser = userRepo.queryBySourceAndSourceId(op.getTargetUserSource(), op.getTargetUserSourceId());
        }
        // 方式3: 通过source + email查找
        else if (StringUtils.isNotEmpty(op.getTargetUserSource()) && StringUtils.isNotEmpty(op.getTargetUserEmail())) {
            targetUser = userRepo.queryBySourceAndEmail(op.getTargetUserSource(), op.getTargetUserEmail());
        }
        else {
            throw new ChannelException.AuthorizationException("必须指定目标用户：可使用用户ID、source+sourceId或source+email");
        }

        if (targetUser == null) {
            throw new ChannelException.AuthorizationException("目标用户不存在");
        }

        return targetUser;
    }

}
