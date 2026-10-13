package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.service.SpaceService;
import com.ke.bella.openapi.space.RoleWithSpace;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SpaceControllerTest {

    @InjectMocks
    private SpaceController controller;

    @Mock
    private SpaceService spaceService;

    @Test
    public void listRoleSupportsNewAndLegacyPaths() throws Exception {
        Method method = SpaceController.class.getMethod("listRole", String.class);
        GetMapping mapping = method.getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactlyInAnyOrder("/role/list", "/member/roles");
    }

    @Test
    public void listRoleDelegatesToSpaceService() {
        List<RoleWithSpace> roles = Collections.singletonList(RoleWithSpace.builder()
                .roleCode("admin")
                .spaceCode("space-1")
                .spaceName("Space One")
                .build());
        when(spaceService.listRole("member-1")).thenReturn(roles);

        assertThat(controller.listRole("member-1")).isSameAs(roles);

        verify(spaceService).listRole("member-1");
    }
}
