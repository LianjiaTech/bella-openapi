package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.Tables;
import com.ke.bella.openapi.common.StatusEnum;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.tables.records.SpaceMemberRecord;
import com.ke.bella.openapi.tables.records.SpaceRecord;
import com.ke.bella.openapi.tables.records.SpaceRoleRecord;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * function:
 *
 * @author chenhongliang001
 */
@Component
public class SpaceRepo implements BaseRepo {

    @Resource
    private DSLContext db;

    public SpaceRecord querySpaceBySpaceCode(String spaceCode) {
        return db.selectFrom(Tables.SPACE)
                .where(Tables.SPACE.SPACE_CODE.eq(spaceCode))
                .and(Tables.SPACE.STATUS.eq(StatusEnum.VALID.getCode()))
                .fetchOneInto(SpaceRecord.class);
    }

    public List<SpaceRecord> querySpaceBySpaceCodes(List<String> spaceCodes) {
        return db.selectFrom(Tables.SPACE)
                .where(Tables.SPACE.SPACE_CODE.in(spaceCodes))
                .and(Tables.SPACE.STATUS.eq(StatusEnum.VALID.getCode()))
                .fetchInto(SpaceRecord.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createSpace(SpaceRecord record) {
        if(StringUtils.isEmpty(record.getSpaceDescription())) {
            record.setSpaceDescription("");
        }
        db.insertInto(Tables.SPACE).set(record).execute();
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchInsertRole(List<SpaceRoleRecord> roles, String spaceCode) {
        if(CollectionUtils.isEmpty(roles)) {
            return;
        }

        // 收集所有的角色代码
        List<String> roleCodes = roles.stream()
                .map(SpaceRoleRecord::getRoleCode)
                .collect(Collectors.toList());

        // 查询数据库中已存在的角色
        List<SpaceRoleRecord> rolesFromDb = queryRoleByTeamCodeAndRoleCode(spaceCode, roleCodes);

        // 如果存在重复的角色代码，抛出异常
        if(CollectionUtils.isNotEmpty(rolesFromDb)) {
            String duplicateRoles = rolesFromDb.stream()
                    .map(SpaceRoleRecord::getRoleCode)
                    .collect(Collectors.joining(","));
            throw new BizParamCheckException(String.format("保存角色失败，部分角色编码已经存在:%s", duplicateRoles));
        }

        // 设置角色描述为空字符串（如果原本为空）
        roles.forEach(role -> {
            if(StringUtils.isEmpty(role.getRoleDesc())) {
                role.setRoleDesc("");
            }
        });

        db.batchInsert(roles).execute();
    }

    public List<SpaceRoleRecord> queryRoleByTeamCodeAndRoleCode(String spaceCode, List<String> roleCodes) {
        return db.selectFrom(Tables.SPACE_ROLE)
                .where(Tables.SPACE_ROLE.SPACE_CODE.eq(spaceCode))
                .and(Tables.SPACE_ROLE.ROLE_CODE.in(roleCodes))
                .and(Tables.SPACE_ROLE.STATUS.eq(StatusEnum.VALID.getCode()))
                .fetchInto(SpaceRoleRecord.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSpaceName(String spaceCode, String spaceName, Long muid) {
        db.update(Tables.SPACE)
                .set(Tables.SPACE.SPACE_NAME, spaceName)
                .set(Tables.SPACE.MUID, muid)
                .where(Tables.SPACE.SPACE_CODE.eq(spaceCode))
                .execute();
    }

    public SpaceMemberRecord queryBySpaceCodeAndMemberUid(String spaceCode, String memberUid) {
        return db.selectFrom(Tables.SPACE_MEMBER)
                .where(Tables.SPACE_MEMBER.SPACE_CODE.eq(spaceCode))
                .and(Tables.SPACE_MEMBER.MEMBER_UID.eq(memberUid))
                .and(Tables.SPACE_MEMBER.STATUS.eq(StatusEnum.VALID.getCode()))
                .fetchOneInto(SpaceMemberRecord.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeSpaceOwner(String spaceCode, String ownerUid, Long muid) {
        db.update(Tables.SPACE)
                .set(Tables.SPACE.OWNER_UID, ownerUid)
                .set(Tables.SPACE.MUID, muid)
                .where(Tables.SPACE.SPACE_CODE.eq(spaceCode))
                .execute();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateMemberRole(Long muid, String memberUid, String spaceCode, String roleCode) {
        db.update(Tables.SPACE_MEMBER)
                .set(Tables.SPACE_MEMBER.MUID, muid)
                .set(Tables.SPACE_MEMBER.ROLE_CODE, roleCode)
                .where(Tables.SPACE_MEMBER.SPACE_CODE.eq(spaceCode))
                .and(Tables.SPACE_MEMBER.MEMBER_UID.eq(memberUid))
                .execute();
    }

    public List<SpaceMemberRecord> queryMemberByTeamCodeAndMemberUids(String spaceCode, List<String> memberUids) {
        return db.selectFrom(Tables.SPACE_MEMBER)
                .where(Tables.SPACE_MEMBER.SPACE_CODE.eq(spaceCode))
                .and(Tables.SPACE_MEMBER.MEMBER_UID.in(memberUids))
                .and(Tables.SPACE_MEMBER.STATUS.eq(StatusEnum.VALID.getCode()))
                .fetchInto(SpaceMemberRecord.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchInsertMember(List<SpaceMemberRecord> records) {
        if(records != null && !records.isEmpty()) {
            db.batchInsert(records).execute();
        }
    }

    public SpaceMemberRecord queryMemberBySpaceCodeAndMemberUid(String spaceCode, String memberUid) {
        return db.selectFrom(Tables.SPACE_MEMBER)
                .where(Tables.SPACE_MEMBER.SPACE_CODE.eq(spaceCode))
                .and(Tables.SPACE_MEMBER.MEMBER_UID.eq(memberUid))
                .and(Tables.SPACE_MEMBER.STATUS.eq(StatusEnum.VALID.getCode()))
                .fetchOneInto(SpaceMemberRecord.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long muid, String memberUid, String spaceCode) {
        db.update(Tables.SPACE_MEMBER)
                .set(Tables.SPACE_MEMBER.STATUS, StatusEnum.INVALID.getCode())
                .set(Tables.SPACE_MEMBER.MUID, muid)
                .where(Tables.SPACE_MEMBER.SPACE_CODE.eq(spaceCode))
                .and(Tables.SPACE_MEMBER.MEMBER_UID.eq(memberUid))
                .execute();
    }

    public List<String> listSpaceCode() {
        return db.select(Tables.SPACE.SPACE_CODE)
                .from(Tables.SPACE)
                .where(Tables.SPACE.STATUS.eq(StatusEnum.VALID.getCode()))
                .fetch(Tables.SPACE.SPACE_CODE);
    }

    public List<SpaceMemberRecord> listBySpaceCode(String spaceCode) {
        return db.selectFrom(Tables.SPACE_MEMBER)
                .where(Tables.SPACE_MEMBER.SPACE_CODE.eq(spaceCode))
                .and(Tables.SPACE_MEMBER.STATUS.eq(StatusEnum.VALID.getCode()))
                .fetchInto(SpaceMemberRecord.class);
    }

    public List<SpaceRecord> listBySpaceCodes(List<String> spaceCodes) {
        return db.selectFrom(Tables.SPACE)
                .where(Tables.SPACE.SPACE_CODE.in(spaceCodes))
                .and(Tables.SPACE.STATUS.eq(StatusEnum.VALID.getCode()))
                .fetchInto(SpaceRecord.class);
    }

    public List<SpaceMemberRecord> listMemberByMemberUid(String memberUid) {
        return db.selectFrom(Tables.SPACE_MEMBER)
                .where(Tables.SPACE_MEMBER.MEMBER_UID.eq(memberUid))
                .and(Tables.SPACE_MEMBER.STATUS.eq(StatusEnum.VALID.getCode()))
                .fetchInto(SpaceMemberRecord.class);
    }

}
