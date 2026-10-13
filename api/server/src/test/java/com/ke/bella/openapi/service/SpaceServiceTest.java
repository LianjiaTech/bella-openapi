package com.ke.bella.openapi.service;

import com.ke.bella.openapi.common.RoleCodeEnum;
import com.ke.bella.openapi.common.StatusEnum;
import com.ke.bella.openapi.db.repo.SpaceRepo;
import com.ke.bella.openapi.space.CreateMemberOp;
import com.ke.bella.openapi.space.CreateSpaceOp;
import com.ke.bella.openapi.tables.records.SpaceMemberRecord;
import com.ke.bella.openapi.tables.records.SpaceRecord;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SpaceServiceTest {

    @InjectMocks
    private SpaceService spaceService;

    @Mock
    private SpaceRepo spaceRepo;

    @Test
    public void createSpaceAlsoCreatesOwnerMember() {
        CreateSpaceOp op = CreateSpaceOp.builder()
                .spaceCode("space-1")
                .spaceName("Space One")
                .ownerUid("owner-1")
                .ownerName("Owner One")
                .userId(100L)
                .build();
        when(spaceRepo.querySpaceBySpaceCode("space-1")).thenReturn(null);

        assertThat(spaceService.createSpace(op)).isEqualTo("space-1");

        ArgumentCaptor<SpaceRecord> spaceCaptor = ArgumentCaptor.forClass(SpaceRecord.class);
        verify(spaceRepo).createSpace(spaceCaptor.capture());
        assertThat(spaceCaptor.getValue().getOwnerUid()).isEqualTo("owner-1");

        ArgumentCaptor<List<SpaceMemberRecord>> memberCaptor = memberRecordsCaptor();
        verify(spaceRepo).batchInsertMember(memberCaptor.capture());
        assertThat(memberCaptor.getValue()).hasSize(1);
        SpaceMemberRecord member = memberCaptor.getValue().get(0);
        assertThat(member.getSpaceCode()).isEqualTo("space-1");
        assertThat(member.getMemberUid()).isEqualTo("owner-1");
        assertThat(member.getMemberName()).isEqualTo("Owner One");
        assertThat(member.getRoleCode()).isEqualTo(RoleCodeEnum.OWNER.getCode());
        assertThat(member.getStatus()).isEqualTo(StatusEnum.VALID.getCode());
        assertThat(member.getCuid()).isEqualTo(100L);
        assertThat(member.getMuid()).isEqualTo(100L);
    }

    @Test
    public void createMemberInsertsNewMember() {
        CreateMemberOp op = createMemberOp(Collections.singletonList(member("member-1", "Member One")));
        when(spaceRepo.querySpaceBySpaceCode("space-1")).thenReturn(new SpaceRecord());
        when(spaceRepo.queryMemberByTeamCodeAndMemberUids("space-1", Collections.singletonList("member-1")))
                .thenReturn(Collections.emptyList());

        assertThat(spaceService.createMember(op)).isTrue();

        ArgumentCaptor<List<SpaceMemberRecord>> memberCaptor = memberRecordsCaptor();
        verify(spaceRepo).batchInsertMember(memberCaptor.capture());
        assertThat(memberCaptor.getValue()).hasSize(1);
        SpaceMemberRecord member = memberCaptor.getValue().get(0);
        assertThat(member.getMemberUid()).isEqualTo("member-1");
        assertThat(member.getMemberName()).isEqualTo("Member One");
        assertThat(member.getRoleCode()).isEqualTo(RoleCodeEnum.ADMIN.getCode());
        assertThat(member.getStatus()).isEqualTo(StatusEnum.VALID.getCode());

        verify(spaceRepo).batchUpdateMember(Collections.emptyList());
    }

    @Test
    public void createMemberUpdatesExistingMember() {
        CreateMemberOp op = createMemberOp(Collections.singletonList(member("member-1", "Updated Name")));
        SpaceMemberRecord existingMember = new SpaceMemberRecord();
        existingMember.setMemberUid("member-1");
        when(spaceRepo.querySpaceBySpaceCode("space-1")).thenReturn(new SpaceRecord());
        when(spaceRepo.queryMemberByTeamCodeAndMemberUids("space-1", Collections.singletonList("member-1")))
                .thenReturn(Collections.singletonList(existingMember));

        assertThat(spaceService.createMember(op)).isTrue();

        ArgumentCaptor<List<SpaceMemberRecord>> updateCaptor = memberRecordsCaptor();
        verify(spaceRepo).batchUpdateMember(updateCaptor.capture());
        assertThat(updateCaptor.getValue()).hasSize(1);
        assertThat(updateCaptor.getValue().get(0).getMemberName()).isEqualTo("Updated Name");
        assertThat(updateCaptor.getValue().get(0).getRoleCode()).isEqualTo(RoleCodeEnum.ADMIN.getCode());
        verify(spaceRepo).batchInsertMember(Collections.emptyList());
    }

    @Test
    public void createMemberHandlesNewAndExistingMembersTogether() {
        CreateMemberOp.Member existing = member("member-1", "Updated Name");
        CreateMemberOp.Member added = member("member-2", "Member Two");
        CreateMemberOp op = createMemberOp(Arrays.asList(existing, added));
        SpaceMemberRecord existingRecord = new SpaceMemberRecord();
        existingRecord.setMemberUid("member-1");
        when(spaceRepo.querySpaceBySpaceCode("space-1")).thenReturn(new SpaceRecord());
        when(spaceRepo.queryMemberByTeamCodeAndMemberUids("space-1", Arrays.asList("member-1", "member-2")))
                .thenReturn(Collections.singletonList(existingRecord));

        assertThat(spaceService.createMember(op)).isTrue();

        ArgumentCaptor<List<SpaceMemberRecord>> updateCaptor = memberRecordsCaptor();
        verify(spaceRepo).batchUpdateMember(updateCaptor.capture());
        assertThat(updateCaptor.getValue()).extracting(SpaceMemberRecord::getMemberUid).containsExactly("member-1");
        ArgumentCaptor<List<SpaceMemberRecord>> memberCaptor = memberRecordsCaptor();
        verify(spaceRepo).batchInsertMember(memberCaptor.capture());
        assertThat(memberCaptor.getValue()).extracting(SpaceMemberRecord::getMemberUid).containsExactly("member-2");
    }

    private CreateMemberOp createMemberOp(List<CreateMemberOp.Member> members) {
        return CreateMemberOp.builder()
                .spaceCode("space-1")
                .roleCode(RoleCodeEnum.ADMIN.getCode())
                .members(members)
                .userId(100L)
                .build();
    }

    private CreateMemberOp.Member member(String uid, String name) {
        return CreateMemberOp.Member.builder().memberUid(uid).memberName(name).build();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<SpaceMemberRecord>> memberRecordsCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }
}
