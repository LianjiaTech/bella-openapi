package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.images.ImagesRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.protocol.images.generator.ImagesGeneratorAdaptor;
import com.ke.bella.openapi.protocol.images.editor.ImagesEditorAdaptor;
import com.ke.bella.openapi.protocol.images.ImagesProperty;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ImagesController 兼容性测试
 *
 * 绕过Spring的AOP、拦截器、过滤器等，直接测试Controller的核心业务逻辑
 * 测试数据与测试逻辑分离，通过外部JSON文件管理历史请求案例
 *
 * 核心目标：确保代码迭代过程中，历史API请求不会受到影响
 */
@RunWith(MockitoJUnitRunner.class)
public class ImagesControllerTest {

    @Mock
    private ChannelRouter channelRouter;

    @Mock
    private AdaptorManager adaptorManager;

    @Mock
    private LimiterManager limiterManager;

    @Mock
    private ImagesGeneratorAdaptor<ImagesProperty> mockGeneratorAdaptor;

    @Mock
    private ImagesEditorAdaptor<ImagesEditorProperty> mockEditorAdaptor;

    @Mock
    private ContentCachingRequestWrapper mockWrappedRequest;

    @InjectMocks
    private ImagesController imagesController;

    @Before
    public void setUp() {
        setupBasicMockEnvironment();
    }

    /**
     * 批量验证所有图片生成历史请求的兼容性
     */
    @Test
    public void testAllGenerationsHistoricalRequestsBackwardCompatibility() {
        System.out.println("=== 开始批量图片生成兼容性验证 ===");

        // 加载测试数据
        List<ImagesHistoricalDataLoader.GenerationsTestCase> allCases =
            ImagesHistoricalDataLoader.loadGenerationsRequests();

        int totalCases = allCases.size();
        int passedCases = 0;
        List<String> failedCases = new ArrayList<>();

        System.out.println("加载图片生成历史请求场景总数: " + totalCases);

        for (ImagesHistoricalDataLoader.GenerationsTestCase testCase : allCases) {
            try {
                System.out.println("--- 验证场景: " + testCase.getScenarioName() + " ---");
                System.out.println("场景描述: " + testCase.getDescription());

                // 执行单个历史请求测试
                validateSingleGenerationsHistoricalRequest(testCase);

                passedCases++;
                System.out.println("✅ " + testCase.getScenarioName() + " - 兼容性验证通过");

            } catch (Exception | AssertionError e) {
                failedCases.add(testCase.getScenarioName() + ": " + e.getMessage());
                System.err.println("❌ " + testCase.getScenarioName() + " - 兼容性验证失败: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("=== 批量图片生成兼容性验证结果 ===");
        System.out.println("总测试场景: " + totalCases);
        System.out.println("通过场景: " + passedCases);
        System.out.println("失败场景: " + failedCases.size());

        if (!failedCases.isEmpty()) {
            System.err.println("失败的场景详情:");
            failedCases.forEach(failure -> System.err.println("  - " + failure));
            fail("存在 " + failedCases.size() + " 个图片生成历史请求兼容性验证失败的场景");
        }

        System.out.println("🎉 所有图片生成历史请求的兼容性验证全部完成！");
    }

    /**
     * 批量验证所有图片编辑历史请求的兼容性
     */
    @Test
    public void testAllEditsHistoricalRequestsBackwardCompatibility() {
        System.out.println("=== 开始批量图片编辑兼容性验证 ===");

        // 加载测试数据
        List<ImagesHistoricalDataLoader.EditsTestCase> allCases =
            ImagesHistoricalDataLoader.loadEditsRequests();

        int totalCases = allCases.size();
        int passedCases = 0;
        List<String> failedCases = new ArrayList<>();

        System.out.println("加载图片编辑历史请求场景总数: " + totalCases);

        for (ImagesHistoricalDataLoader.EditsTestCase testCase : allCases) {
            try {
                System.out.println("--- 验证场景: " + testCase.getScenarioName() + " ---");
                System.out.println("场景描述: " + testCase.getDescription());

                // 执行单个历史请求测试
                validateSingleEditsHistoricalRequest(testCase);

                passedCases++;
                System.out.println("✅ " + testCase.getScenarioName() + " - 兼容性验证通过");

            } catch (Exception | AssertionError e) {
                failedCases.add(testCase.getScenarioName() + ": " + e.getMessage());
                System.err.println("❌ " + testCase.getScenarioName() + " - 兼容性验证失败: " + e.getMessage());
                e.printStackTrace(); // 打印完整的堆栈信息
            }
        }

        // 输出最终结果
        System.out.println("=== 批量图片编辑兼容性验证结果 ===");
        System.out.println("总测试场景: " + totalCases);
        System.out.println("通过场景: " + passedCases);
        System.out.println("失败场景: " + failedCases.size());

        if (!failedCases.isEmpty()) {
            System.err.println("失败的场景详情:");
            failedCases.forEach(failure -> System.err.println("  - " + failure));
            fail("存在 " + failedCases.size() + " 个图片编辑历史请求兼容性验证失败的场景");
        }

        System.out.println("🎉 所有图片编辑历史请求的兼容性验证全部完成！");
    }

    /**
     * 验证单个图片生成历史请求场景的纯逻辑
     */
    private void validateSingleGenerationsHistoricalRequest(ImagesHistoricalDataLoader.GenerationsTestCase testCase) {
        // 1. 设置请求上下文
        setupGenerationsRequestContext();

        // 2. 准备测试环境
        setupMockForGenerationsTestCase(testCase);

        // 3. 执行Controller核心逻辑（绕过所有AOP）
        ImagesResponse actualResponse = imagesController.generateImages(testCase.getRequest());

        // 4. 验证响应格式兼容性
        validateGenerationsResponseCompatibility(testCase, actualResponse);

        // 5. 验证底层服务调用参数
        validateGenerationsServiceCallParameters(testCase);

        // 6. 重置Mock状态以准备下一个测试
        reset(channelRouter, adaptorManager, mockGeneratorAdaptor);
    }

    /**
     * 验证单个图片编辑历史请求场景的纯逻辑
     */
    private void validateSingleEditsHistoricalRequest(ImagesHistoricalDataLoader.EditsTestCase testCase) {
        // 1. 设置请求上下文
        setupEditsRequestContext();

        // 2. 准备测试环境
        setupMockForEditsTestCase(testCase);

        // 3. 执行Controller核心逻辑（绕过所有AOP）
        ImagesResponse actualResponse = imagesController.editImages(testCase.getRequest());

        // 4. 验证响应格式兼容性
        validateEditsResponseCompatibility(testCase, actualResponse);

        // 5. 验证底层服务调用参数
        validateEditsServiceCallParameters(testCase);

        // 6. 重置Mock状态以准备下一个测试
        reset(channelRouter, adaptorManager, mockEditorAdaptor);
    }

    /**
     * 为图片生成测试场景设置Mock
     */
    private void setupMockForGenerationsTestCase(ImagesHistoricalDataLoader.GenerationsTestCase testCase) {
        // 设置ChannelRouter Mock
        when(channelRouter.route(eq("/v1/images/generations"), eq(testCase.getRequest().getModel()), any(), eq(false)))
            .thenReturn(testCase.getMockChannel());

        // 设置AdaptorManager Mock
        when(adaptorManager.getProtocolAdaptor(eq("/v1/images/generations"),
                                              eq(testCase.getMockChannel().getProtocol()),
                                              eq(ImagesGeneratorAdaptor.class)))
            .thenReturn(mockGeneratorAdaptor);

        when(mockGeneratorAdaptor.getPropertyClass())
            .thenReturn((Class) ImagesProperty.class);

        // 设置Adaptor Mock响应
        when(mockGeneratorAdaptor.generateImages(any(ImagesRequest.class),
                                               eq(testCase.getMockChannel().getUrl()),
                                               any(ImagesProperty.class)))
            .thenReturn(testCase.getExpectedResponse());
    }

    /**
     * 为图片编辑测试场景设置Mock
     */
    private void setupMockForEditsTestCase(ImagesHistoricalDataLoader.EditsTestCase testCase) {
        // 设置ChannelRouter Mock
        when(channelRouter.route(eq("/v1/images/edits"), eq(testCase.getRequest().getModel()), any(), eq(false)))
            .thenReturn(testCase.getMockChannel());

        // 设置AdaptorManager Mock
        when(adaptorManager.getProtocolAdaptor(eq("/v1/images/edits"),
                                              eq(testCase.getMockChannel().getProtocol()),
                                              eq(ImagesEditorAdaptor.class)))
            .thenReturn(mockEditorAdaptor);

        when(mockEditorAdaptor.getPropertyClass())
            .thenReturn((Class) ImagesEditorProperty.class);

        // 设置Adaptor Mock响应
        when(mockEditorAdaptor.editImages(any(ImagesEditRequest.class),
                                        eq(testCase.getMockChannel().getUrl()),
                                        any(ImagesEditorProperty.class)))
            .thenReturn(testCase.getExpectedResponse());
    }

    /**
     * 验证图片生成响应格式的兼容性
     */
    private void validateGenerationsResponseCompatibility(ImagesHistoricalDataLoader.GenerationsTestCase testCase,
                                                        ImagesResponse actualResponse) {
        assertNotNull(testCase.getScenarioName() + " - 响应不能为空", actualResponse);
        assertNotNull(testCase.getScenarioName() + " - 响应必须包含data字段", actualResponse.getData());
        assertFalse(testCase.getScenarioName() + " - 响应data不能为空", actualResponse.getData().isEmpty());


        // 验证每个图片数据的必要字段
        for (int i = 0; i < actualResponse.getData().size(); i++) {
            ImagesResponse.ImageData imageData = actualResponse.getData().get(i);
            String fieldPrefix = testCase.getScenarioName() + " - 图片" + (i + 1);

            // 根据response_format验证相应字段
            if ("url".equals(testCase.getRequest().getResponse_format())) {
                assertNotNull(fieldPrefix + " - URL不能为空", imageData.getUrl());
            } else if ("b64_json".equals(testCase.getRequest().getResponse_format())) {
                assertNotNull(fieldPrefix + " - Base64数据不能为空", imageData.getB64_json());
            } else {
				assertTrue(fieldPrefix + " - Base64数据或URL不能为空",
					imageData.getB64_json() != null || imageData.getUrl() != null);
			}


            // 验证尺寸字段
            if (testCase.getRequest().getSize() != null) {
                assertEquals(fieldPrefix + " - 尺寸必须匹配请求",
                           testCase.getRequest().getSize(),
                           imageData.getSize());
            }
        }

        // 执行场景特定的验证
        if (testCase.getCustomValidator() != null) {
            testCase.getCustomValidator().accept(actualResponse);
        }
    }

    /**
     * 验证图片编辑响应格式的兼容性
     */
    private void validateEditsResponseCompatibility(ImagesHistoricalDataLoader.EditsTestCase testCase,
                                                  ImagesResponse actualResponse) {
        assertNotNull(testCase.getScenarioName() + " - 响应不能为空", actualResponse);
        assertNotNull(testCase.getScenarioName() + " - 响应必须包含data字段", actualResponse.getData());
        assertFalse(testCase.getScenarioName() + " - 响应data不能为空", actualResponse.getData().isEmpty());


        // 验证每个图片数据的必要字段
        for (int i = 0; i < actualResponse.getData().size(); i++) {
            ImagesResponse.ImageData imageData = actualResponse.getData().get(i);
            String fieldPrefix = testCase.getScenarioName() + " - 图片" + (i + 1);

            // 根据response_format验证相应字段
            if ("url".equals(testCase.getRequest().getResponse_format())) {
                assertNotNull(fieldPrefix + " - URL不能为空", imageData.getUrl());
            } else if ("b64_json".equals(testCase.getRequest().getResponse_format())) {
                assertNotNull(fieldPrefix + " - Base64数据不能为空", imageData.getB64_json());
            } else {
				assertTrue(fieldPrefix + " - Base64数据或URL不能为空",
					imageData.getB64_json() != null || imageData.getUrl() != null);
			}

            // 验证尺寸字段
            if (testCase.getRequest().getSize() != null) {
                assertEquals(fieldPrefix + " - 尺寸必须匹配请求",
                           testCase.getRequest().getSize(),
                           imageData.getSize());
            }
        }

        // 执行场景特定的验证
        if (testCase.getCustomValidator() != null) {
            testCase.getCustomValidator().accept(actualResponse);
        }
    }

    /**
     * 验证图片生成底层服务调用参数的正确性
     */
    private void validateGenerationsServiceCallParameters(ImagesHistoricalDataLoader.GenerationsTestCase testCase) {
        // 验证ChannelRouter调用
        verify(channelRouter, times(1)).route(
            eq("/v1/images/generations"),
            eq(testCase.getRequest().getModel()),
            any(), // API密钥
            eq(false) // 非Mock模式
        );

        // 验证AdaptorManager调用
        verify(adaptorManager, times(1)).getProtocolAdaptor(
            eq("/v1/images/generations"),
            eq(testCase.getMockChannel().getProtocol()),
            eq(ImagesGeneratorAdaptor.class)
        );

        // 验证Adaptor调用的参数传递
        verify(mockGeneratorAdaptor, times(1)).generateImages(
            argThat(req -> testCase.getParameterValidator().test(req)),
            eq(testCase.getMockChannel().getUrl()),
            any(ImagesProperty.class)
        );
    }

    /**
     * 验证图片编辑底层服务调用参数的正确性
     */
    private void validateEditsServiceCallParameters(ImagesHistoricalDataLoader.EditsTestCase testCase) {
        // 验证ChannelRouter调用
        verify(channelRouter, times(1)).route(
            eq("/v1/images/edits"),
            eq(testCase.getRequest().getModel()),
            any(), // API密钥
            eq(false) // 非Mock模式
        );

        // 验证AdaptorManager调用
        verify(adaptorManager, times(1)).getProtocolAdaptor(
            eq("/v1/images/edits"),
            eq(testCase.getMockChannel().getProtocol()),
            eq(ImagesEditorAdaptor.class)
        );

        // 验证Adaptor调用的参数传递
        verify(mockEditorAdaptor, times(1)).editImages(
            argThat(req -> testCase.getParameterValidator().test(req)),
            eq(testCase.getMockChannel().getUrl()),
            any(ImagesEditorProperty.class)
        );
    }

    /**
     * 设置基础的Mock环境
     */
    private void setupBasicMockEnvironment() {
        // 设置BellaContext中的API密钥
        ApikeyInfo testApikey = new ApikeyInfo();
        testApikey.setApikey("test-key");
        ApikeyInfo.RolePath rolePath = new ApikeyInfo.RolePath();
        rolePath.getIncluded().add("/v1/**");
        testApikey.setRolePath(rolePath);
        BellaContext.setApikey(testApikey);
    }

    /**
     * 为图片生成测试设置请求上下文
     */
    private void setupGenerationsRequestContext() {
        when(mockWrappedRequest.getRequestURI()).thenReturn("/v1/images/generations");
        EndpointContext.setRequest(mockWrappedRequest);
    }

    /**
     * 为图片编辑测试设置请求上下文
     */
    private void setupEditsRequestContext() {
        when(mockWrappedRequest.getRequestURI()).thenReturn("/v1/images/edits");
        EndpointContext.setRequest(mockWrappedRequest);
    }

    @After
    public void tearDown() {
        BellaContext.clearAll();
        EndpointContext.clearAll();
    }
}
