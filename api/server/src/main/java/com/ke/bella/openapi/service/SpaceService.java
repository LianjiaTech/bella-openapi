package com.ke.bella.openapi.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ke.bella.openapi.common.RoleCodeEnum;
import com.ke.bella.openapi.db.repo.SpaceRepo;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.space.ChangeSpaceOwnerOp;
import com.ke.bella.openapi.space.CreateMemberOp;
import com.ke.bella.openapi.space.CreateRoleDetail;
import com.ke.bella.openapi.space.CreateRoleOp;
import com.ke.bella.openapi.space.CreateSpaceOp;
import com.ke.bella.openapi.space.ExitSpaceOp;
import com.ke.bella.openapi.space.Member;
import com.ke.bella.openapi.space.RemoveMemberOp;
import com.ke.bella.openapi.space.RoleWithSpace;
import com.ke.bella.openapi.space.Space;
import com.ke.bella.openapi.space.UpdateMemberRoleOp;
import com.ke.bella.openapi.space.UpdateSpaceNameOp;
import com.ke.bella.openapi.tables.records.SpaceMemberRecord;
import com.ke.bella.openapi.tables.records.SpaceRecord;
import com.ke.bella.openapi.tables.records.SpaceRoleRecord;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * function: 空间
 *
 * @author chenhongliang001
 */
@Component
public class SpaceService {

    @Autowired
    private SpaceRepo spaceRepo;

    @Transactional(rollbackFor = Exception.class)
    public String createSpace(CreateSpaceOp op) {

        if(StringUtils.isEmpty(op.getSpaceCode())) {
            op.setSpaceCode(generateSpaceCode());
        }
        SpaceRecord space = spaceRepo.querySpaceBySpaceCode(op.getSpaceCode());
        // 判断空间是否已经存在
        if(space != null) {
            throw new BizParamCheckException(String.format("空间编码:%s已经存在", op.getSpaceCode()));
        }
        // 保存
        spaceRepo.createSpace(buildSpace(op));

        return op.getSpaceCode();
    }

    public void fillCreateSpaceOperator(SpaceRecord spaceRecord, Long userId) {
        spaceRecord.setCuid(userId);
        spaceRecord.setMuid(userId);
    }

    public void fillCreateRoleOperator(SpaceRoleRecord role, Long userId) {
        role.setCuid(userId);
        role.setMuid(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean createRole(CreateRoleOp op) {
        spaceRepo.batchInsertRole(buildRoles(op.getRoles(), op.getSpaceCode(), op.getUserId()), op.getSpaceCode());
        return true;
    }

    private List<SpaceRoleRecord> buildRoles(List<CreateRoleDetail> details, String spaceCode, Long userId) {
        if(CollectionUtils.isEmpty(details)) {
            return Lists.newArrayList();
        }
        List<SpaceRoleRecord> roles = Lists.newArrayList();
        for (CreateRoleDetail detail : details) {
            SpaceRoleRecord role = new SpaceRoleRecord();
            BeanUtils.copyProperties(detail, role);
            role.setSpaceCode(spaceCode);
            fillCreateRoleOperator(role, userId);
            roles.add(role);
        }
        return roles;
    }

    private SpaceRecord buildSpace(CreateSpaceOp spaceCreateOp) {
        SpaceRecord space = new SpaceRecord();
        BeanUtils.copyProperties(spaceCreateOp, space);
        fillCreateSpaceOperator(space, spaceCreateOp.getUserId());
        return space;
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean updateSpaceName(UpdateSpaceNameOp op) {
        spaceRepo.updateSpaceName(op.getSpaceCode(), op.getSpaceName(), op.getUserId());
        return true;
    }

    public String generateSpaceCode() {
        return UUID.randomUUID().toString();
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean changeSpaceOwner(ChangeSpaceOwnerOp op) {

        // 只有空间拥有者才能将自己的空间转给其它人
        SpaceRecord space = spaceRepo.querySpaceBySpaceCode(op.getSpaceCode());
        if(space == null) {
            throw new BizParamCheckException(String.format("转让空间失败，空间:%s不存在", op.getSpaceCode()));
        }
        if(!Objects.equals(space.getOwnerUid(), String.valueOf(op.getUserId()))) {
            throw new BizParamCheckException("只有空间拥有者有权限将团队转让给其他人");
        }

        // 转让人必须在空间内
        SpaceMemberRecord member = spaceRepo.queryBySpaceCodeAndMemberUid(op.getSpaceCode(), op.getOwnerUid());
        if(member == null) {
            throw new BizParamCheckException("新的拥有者必须在空间内，请先将心的拥有者添加到空间中");
        }
        // 空间转让
        spaceRepo.changeSpaceOwner(op.getSpaceCode(), op.getOwnerUid(), op.getUserId());

        // 成员表设置，原拥有者变更为管理员
        spaceRepo.updateMemberRole(op.getUserId(), space.getOwnerUid(),
                op.getSpaceCode(), RoleCodeEnum.ADMIN.getCode());
        // 成员表设置，设置新拥有者
        spaceRepo.updateMemberRole(op.getUserId(), op.getOwnerUid(),
                op.getSpaceCode(), RoleCodeEnum.OWNER.getCode());

        return true;

    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean createMember(CreateMemberOp op) {

        SpaceRecord space = spaceRepo.querySpaceBySpaceCode(op.getSpaceCode());
        if(space == null) {
            throw new BizParamCheckException(String.format("空间不存在:%s", op.getSpaceCode()));
        }

        List<String> memberUids = op.getMembers().stream()
                .map(CreateMemberOp.Member::getMemberUid)
                .collect(Collectors.toList());

        List<SpaceMemberRecord> existingMembers = spaceRepo.queryMemberByTeamCodeAndMemberUids(op.getSpaceCode(), memberUids);
        Set<String> existingMemberUids = existingMembers.stream()
                .map(SpaceMemberRecord::getMemberUid)
                .collect(Collectors.toSet());

        Set<String> duplicates = memberUids.stream()
                .filter(existingMemberUids::contains)
                .collect(Collectors.toSet());

        if(!duplicates.isEmpty()) {
            throw new BizParamCheckException(String.format("重复添加成员: %s", duplicates));
        }

        List<SpaceMemberRecord> newMembers = op.getMembers().stream()
                .map(m -> {
                    SpaceMemberRecord record = new SpaceMemberRecord();
                    BeanUtils.copyProperties(m, record);
                    record.setCuid(op.getUserId());
                    record.setMuid(op.getUserId());
                    record.setSpaceCode(op.getSpaceCode());
                    record.setRoleCode(op.getRoleCode());
                    return record;
                })
                .collect(Collectors.toList());

        spaceRepo.batchInsertMember(newMembers);
        return true;
    }

    public Boolean removeMember(RemoveMemberOp op) {

        SpaceMemberRecord member = spaceRepo.queryMemberBySpaceCodeAndMemberUid(op.getSpaceCode(), op.getMemberUid());
        if(member == null) {
            throw new BizParamCheckException("成员不存在无法删除");
        }

        spaceRepo.removeMember(op.getUserId(), op.getMemberUid(), op.getSpaceCode());
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean updateMember(UpdateMemberRoleOp op) {
        spaceRepo.updateMemberRole(op.getUserId(), op.getMemberUid(), op.getSpaceCode(), op.getRoleCode());
        return true;
    }

    public Boolean exitSpace(ExitSpaceOp op) {
        SpaceMemberRecord member = spaceRepo.queryBySpaceCodeAndMemberUid(op.getSpaceCode(), op.getMemberUid());
        if(member == null) {
            throw new BizParamCheckException(String.format("退出空间失败，用户:%s不在此空间中", op.getMemberUid()));
        }
        spaceRepo.removeMember(op.getUserId(), op.getMemberUid(), op.getSpaceCode());
        return true;
    }

    public List<Member> listMember(String spaceCode) {
        List<SpaceMemberRecord> records = spaceRepo.listBySpaceCode(spaceCode);
        if(CollectionUtils.isEmpty(records)) {
            return Lists.newArrayList();
        }

        List<Member> members = Lists.newArrayList();
        for (SpaceMemberRecord record : records) {
            members.add(Member.builder()
                    .roleCode(record.getRoleCode())
                    .spaceCode(record.getSpaceCode())
                    .memberUid(record.getMemberUid())
                    .memberName(record.getMemberName())
                    .build());
        }
        return members;
    }

    public Space querySpaceBySpaceCode(String spaceCode) {
        SpaceRecord spaceRecord = spaceRepo.querySpaceBySpaceCode(spaceCode);
        if(spaceRecord == null) {
            return null;
        }
        return Space.builder()
                .spaceName(spaceRecord.getSpaceName())
                .spaceCode(spaceRecord.getSpaceCode())
                .ownerUid(spaceRecord.getOwnerUid())
                .build();
    }

    public List<Space> listSpace(List<String> spaceCodes) {

        List<SpaceRecord> spaceRecords = spaceRepo.querySpaceBySpaceCodes(spaceCodes);

        if(CollectionUtils.isEmpty(spaceRecords)) {
            return Collections.emptyList();
        }

        return spaceRecords.stream()
                .map(record -> Space.builder()
                        .spaceName(record.getSpaceName())
                        .spaceCode(record.getSpaceCode())
                        .ownerUid(record.getOwnerUid())
                        .build())
                .collect(Collectors.toList());
    }

    public List<RoleWithSpace> listRole(String memberUid) {

        List<RoleWithSpace> rolesAll = Lists.newArrayList();
        rolesAll.add(RoleWithSpace.builder()
                .roleCode(RoleCodeEnum.OWNER.getCode())
                .spaceCode(memberUid)
                .spaceName("个人空间").build());

        List<SpaceMemberRecord> members = spaceRepo.listMemberByMemberUid(memberUid);
        if(CollectionUtils.isEmpty(members)) {
            return rolesAll;
        }

        // 收集唯一的空间编码
        Set<String> spaceCodes = members.stream()
                .map(SpaceMemberRecord::getSpaceCode)
                .collect(Collectors.toSet());

        // 查询团队信息并转换为 Map
        Map<String, SpaceRecord> spaceMap = queryMapBySpaceCodes(new ArrayList<>(spaceCodes));

        // 构建团队角色列表，并设置团队名称
        List<RoleWithSpace> roles = members.stream()
                .map(member -> {
                    RoleWithSpace role = new RoleWithSpace();
                    role.setRoleCode(member.getRoleCode());
                    role.setSpaceCode(member.getSpaceCode());
                    SpaceRecord space = spaceMap.get(member.getSpaceCode());
                    if(space != null) {
                        role.setSpaceName(space.getSpaceName());
                    }
                    return role;
                })
                .collect(Collectors.toList());
        if(CollectionUtils.isNotEmpty(roles)) {
            rolesAll.addAll(roles);
        }
        return rolesAll;
    }

    public RoleWithSpace getMemberRole(String memberUid, String spaceCode) {

        if(Objects.equals(memberUid, spaceCode)) {
            return RoleWithSpace.builder()
                    .roleCode(RoleCodeEnum.OWNER.getCode())
                    .spaceCode(spaceCode)
                    .spaceName("个人空间")
                    .build();
        }

        SpaceMemberRecord member = spaceRepo.queryMemberBySpaceCodeAndMemberUid(spaceCode, memberUid);
        if(member == null) {
            return null;
        }

        SpaceRecord space = spaceRepo.querySpaceBySpaceCode(spaceCode);
        return RoleWithSpace.builder()
                .roleCode(member.getRoleCode())
                .spaceCode(member.getSpaceCode())
                .spaceName(space != null ? space.getSpaceName() : "")
                .build();
    }

    public List<String> listSpaceCode() {
        return spaceRepo.listSpaceCode();
    }

    public Map<String, SpaceRecord> queryMapBySpaceCodes(List<String> spaceCodes) {

        if(CollectionUtils.isEmpty(spaceCodes)) {
            return Maps.newHashMap();
        }

        List<SpaceRecord> spaces = spaceRepo.listBySpaceCodes(spaceCodes);
        if(CollectionUtils.isEmpty(spaces)) {
            return Maps.newHashMap();
        }

        return spaces.stream().collect(Collectors.toMap(
                SpaceRecord::getSpaceCode,
                Function.identity(),
                (existing, replacement) -> existing // 如果有重复键，保留已有的值
        ));

    }

}
