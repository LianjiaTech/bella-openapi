package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.annotations.BellaAPI;
import com.ke.bella.openapi.service.SpaceService;
import com.ke.bella.openapi.space.CreateMemberOp;
import com.ke.bella.openapi.space.ExitSpaceOp;
import com.ke.bella.openapi.space.Member;
import com.ke.bella.openapi.space.RemoveMemberOp;
import com.ke.bella.openapi.space.RoleWithSpace;
import com.ke.bella.openapi.space.Space;
import com.ke.bella.openapi.space.UpdateMemberRoleOp;
import com.ke.bella.openapi.space.CreateRoleOp;
import com.ke.bella.openapi.space.CreateSpaceOp;
import com.ke.bella.openapi.space.UpdateSpaceNameOp;
import com.ke.bella.openapi.space.ChangeSpaceOwnerOp;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * function: 空间管理
 *
 * @author chenhongliang001
 */
@BellaAPI
@RestController
@RequestMapping("/v1/space")
@Tag(name = "空间管理")
public class SpaceController {

    @Autowired
    private SpaceService spaceService;

    @PostMapping("/create")
    public String createSpace(@RequestBody @Validated CreateSpaceOp op) {
        return spaceService.createSpace(op);
    }

    @PostMapping("/name/update")
    public Boolean updateSpaceName(@RequestBody @Validated UpdateSpaceNameOp op) {
        return spaceService.updateSpaceName(op);
    }

    @GetMapping("/get")
    public Space getSpace(@RequestParam String spaceCode) {
        return spaceService.querySpaceBySpaceCode(spaceCode);
    }

    @GetMapping("/list")
    public List<Space> listSpace(@RequestParam List<String> spaceCodes) {
        return spaceService.listSpace(spaceCodes);
    }

    @PostMapping("/owner/change")
    public Boolean changeSpaceOwner(@RequestBody ChangeSpaceOwnerOp op) {
        return spaceService.changeSpaceOwner(op);
    }

    @PostMapping("/role/create")
    public Boolean createRole(@RequestBody @Validated CreateRoleOp op) {
        return spaceService.createRole(op);
    }

    @GetMapping("/role/list")
    public List<RoleWithSpace> listRole(@RequestParam String memberUid) {
        return spaceService.listRole(memberUid);
    }

    @PostMapping("/member/create")
    public Boolean createMember(@RequestBody @Validated CreateMemberOp op) {
        return spaceService.createMember(op);
    }

    @PostMapping("/member/remove")
    public Boolean removeMember(@RequestBody @Validated RemoveMemberOp op) {
        return spaceService.removeMember(op);
    }

    @PostMapping("/member/update")
    public Boolean updateMember(@RequestBody @Validated UpdateMemberRoleOp op) {
        return spaceService.updateMember(op);
    }

    @PostMapping("/member/exit")
    public Boolean exitSpace(@RequestBody @Validated ExitSpaceOp op) {
        return spaceService.exitSpace(op);
    }

    @GetMapping("/member/list")
    public List<Member> listMember(@RequestParam String spaceCode) {
        return spaceService.listMember(spaceCode);
    }

    @GetMapping("/member/role")
    public RoleWithSpace getMemberRole(@RequestParam String memberUid, @RequestParam String spaceCode) {
        return spaceService.getMemberRole(memberUid, spaceCode);
    }

}
