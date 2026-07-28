package com.ke.bella.openapi.protocol.cost;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.asr.flash.FlashAsrBillingMode;
import com.ke.bella.openapi.protocol.asr.flash.FlashAsrPriceInfo;
import com.ke.bella.openapi.protocol.embedding.EmbeddingPriceInfo;
import com.ke.bella.openapi.protocol.embedding.EmbeddingResponse;
import com.ke.bella.openapi.protocol.images.ImagesEditsPriceInfo;
import com.ke.bella.openapi.protocol.images.ImagesLogHandler;
import com.ke.bella.openapi.protocol.images.ImagesPriceInfo;
import com.ke.bella.openapi.protocol.images.ImagesRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.protocol.ocr.OcrPriceInfo;
import com.ke.bella.openapi.protocol.tts.TtsPriceInfo;
import com.ke.bella.openapi.protocol.video.VideoPriceInfo;
import com.ke.bella.openapi.protocol.video.VideoUsage;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 其他 Endpoint 的成本详细信息测试
 * 测试非 Completion/Responses API 的成本明细
 */
public class OtherEndpointsCostDetailsTest {

    /**
     * 测试场景1: Embedding API 成本明细
     */
    @Test
    public void testEmbeddingCostDetails() {
        EmbeddingPriceInfo priceInfo = new EmbeddingPriceInfo();
        priceInfo.setInput(new BigDecimal("0.5"));  // 0.5分/千token

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        EmbeddingResponse.TokenUsage usage = new EmbeddingResponse.TokenUsage();
        usage.setPrompt_tokens(10000);  // 10k tokens
        usage.setTotal_tokens(10000);

        CostDetails costDetails = CostCalculator.calculate("/v1/embeddings", priceInfoJson, usage);

        // 验证总成本
        BigDecimal expectedCost = new BigDecimal("0.5").multiply(new BigDecimal("10"));
        assertEquals("Embedding总成本应正确", 0, expectedCost.compareTo(costDetails.getTotalCost()));

        // Embedding 只有输入成本，没有输出成本和工具成本
        assertNull("Embedding应该没有输入明细（当前实现）", costDetails.getInputDetails());
        assertNull("Embedding应该没有输出明细", costDetails.getOutputDetails());
        assertNull("Embedding应该没有工具明细", costDetails.getToolDetails());
    }

    /**
     * 测试场景2: TTS API 成本明细
     */
    @Test
    public void testTtsCostDetails() {
        TtsPriceInfo priceInfo = new TtsPriceInfo();
        priceInfo.setInput(new BigDecimal("10"));  // 10分/万字符

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        int inputLength = 50000;  // 5万字符

        CostDetails costDetails = CostCalculator.calculate("/v1/audio/speech", priceInfoJson, inputLength);

        // 验证总成本: 50000 / 10000 * 10 = 50
        BigDecimal expectedCost = new BigDecimal("10").multiply(new BigDecimal("5"));
        assertEquals("TTS总成本应正确", 0, expectedCost.compareTo(costDetails.getTotalCost()));

        // TTS 当前实现没有明细
        assertNull("TTS应该没有输入明细（当前实现）", costDetails.getInputDetails());
        assertNull("TTS应该没有输出明细", costDetails.getOutputDetails());
        assertNull("TTS应该没有工具明细", costDetails.getToolDetails());
    }

    @Test
    public void testRealtimeTtsCostDetailsReuseTtsTextUnit() {
        TtsPriceInfo priceInfo = new TtsPriceInfo();
        priceInfo.setInput(new BigDecimal("10"));

        CostDetails costDetails = CostCalculator.calculate("/v1/audio/tts/stream", JacksonUtils.serialize(priceInfo), 50000);

        assertEquals("Realtime TTS should use TTS text pricing", 0, new BigDecimal("50").compareTo(costDetails.getTotalCost()));
    }

    @Test
    public void testFlashAsrDefaultCostDetailsPerRequest() {
        CostDetails costDetails = CostCalculator.calculate("/v1/audio/asr/flash", "{\"price\":3.5}", 1);

        assertEquals("Flash ASR 默认保持按次计费", 0, new BigDecimal("3.5").compareTo(costDetails.getTotalCost()));
    }

    @Test
    public void testFlashAsrDurationCostDetails() {
        FlashAsrPriceInfo priceInfo = new FlashAsrPriceInfo();
        priceInfo.setPrice(new BigDecimal("120"));
        priceInfo.setBillingMode(FlashAsrBillingMode.duration);
        Map<String, Object> usage = new HashMap<>();
        usage.put("count", 1);
        usage.put("duration", 60.0);

        CostDetails costDetails = CostCalculator.calculate("/v1/audio/asr/flash", JacksonUtils.serialize(priceInfo), usage);

        assertEquals("Flash ASR duration 模式应按元/时折算为分", 0, new BigDecimal("200").compareTo(costDetails.getTotalCost()));
    }

    /**
     * 测试场景3: OCR API 成本明细
     */
    @Test
    public void testOcrCostDetails() {
        OcrPriceInfo priceInfo = new OcrPriceInfo();
        priceInfo.setPricePerRequest(new BigDecimal("0.5"));  // 0.5分/次

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        int times = 10;  // 10次请求

        CostDetails costDetails = CostCalculator.calculate("/v1/ocr/idcard", priceInfoJson, times);

        // 验证总成本: 10 * 0.5 = 5
        BigDecimal expectedCost = new BigDecimal("0.5").multiply(new BigDecimal("10"));
        assertEquals("OCR总成本应正确", 0, expectedCost.compareTo(costDetails.getTotalCost()));

        // OCR 当前实现没有明细
        assertNull("OCR应该没有输入明细（当前实现）", costDetails.getInputDetails());
        assertNull("OCR应该没有输出明细", costDetails.getOutputDetails());
        assertNull("OCR应该没有工具明细", costDetails.getToolDetails());
    }

    /**
     * 测试场景4: Video API 成本明细
     */
    @Test
    public void testVideoCostDetails() {
        VideoPriceInfo priceInfo = new VideoPriceInfo();
        priceInfo.setOutput(new BigDecimal("20"));  // 20分/千token

        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        VideoUsage usage = new VideoUsage();
        usage.setCompletion_tokens(5000);  // 5k tokens

        CostDetails costDetails = CostCalculator.calculate("/v1/videos", priceInfoJson, usage);

        // 验证总成本: 5000 / 1000 * 20 = 100
        BigDecimal expectedCost = new BigDecimal("20").multiply(new BigDecimal("5"));
        assertEquals("Video总成本应正确", 0, expectedCost.compareTo(costDetails.getTotalCost()));

        // Video 当前实现没有明细
        assertNull("Video应该没有输入明细（当前实现）", costDetails.getInputDetails());
        assertNull("Video应该没有输出明细", costDetails.getOutputDetails());
        assertNull("Video应该没有工具明细", costDetails.getToolDetails());
    }

    /**
     * 测试场景5: Images API 成本明细（复杂场景）
     */
    @Test
    public void testImagesCostDetails() {
        ImagesPriceInfo priceInfo = new ImagesPriceInfo();
        ImagesPriceInfo.ImagesPriceInfoDetailsList details = new ImagesPriceInfo.ImagesPriceInfoDetailsList();

        ImagesPriceInfo.ImagesPriceInfoDetails detail = new ImagesPriceInfo.ImagesPriceInfoDetails();
        detail.setSize("1024x1024");
        detail.setHdPricePerImage(new BigDecimal("10"));    // 高清: 10分/张
        detail.setMdPricePerImage(new BigDecimal("6"));     // 中等: 6分/张
        detail.setLdPricePerImage(new BigDecimal("3"));     // 低清: 3分/张
        details.add(detail);

        priceInfo.setDetails(details);
        String priceInfoJson = JacksonUtils.serialize(priceInfo);

        ImagesResponse.Usage usage = new ImagesResponse.Usage();
        usage.setQuality("high");  // 高清
        usage.setSize("1024x1024");
        usage.setNum(3);  // 生成3张

        CostDetails costDetails = CostCalculator.calculate("/v1/images/generations", priceInfoJson, usage);

        // 验证总成本: 3 * 10 = 30
        BigDecimal expectedCost = new BigDecimal("10").multiply(new BigDecimal("3"));
        assertEquals("Images总成本应正确", 0, expectedCost.compareTo(costDetails.getTotalCost()));

        // Images 当前实现没有明细
        assertNull("Images应该没有输入明细（当前实现）", costDetails.getInputDetails());
        assertNull("Images应该没有输出明细", costDetails.getOutputDetails());
        assertNull("Images应该没有工具明细", costDetails.getToolDetails());
    }

    @Test
    public void testImagesPriceInfoUnitsUseCentsPerImage() {
        ImagesPriceInfo priceInfo = new ImagesPriceInfo();
        ImagesPriceInfo.ImagesPriceInfoDetails detail = new ImagesPriceInfo.ImagesPriceInfoDetails();
        ImagesEditsPriceInfo editsPriceInfo = new ImagesEditsPriceInfo();

        assertEquals("文生图整体单位应为分/张", "分/张", priceInfo.getUnit());
        assertEquals("文生图按尺寸价格单位应为分/张", "分/张", detail.getUnit());
        assertEquals("图生图单位应为分/张", "分/张", editsPriceInfo.getUnit());

        assertTrue("文生图低质量价格描述应标明分/张",
                detail.description().get("ldPricePerImage").contains("分/张"));
        assertTrue("文生图高清价格描述应标明分/张",
                detail.description().get("hdPricePerImage").contains("分/张"));
        assertTrue("图生图单张价格描述应标明分/张",
                editsPriceInfo.description().get("pricePerEdit").contains("分/张"));

        assertTrue("文生图文字token价格仍应标明分/千token",
                detail.description().get("textTokenPrice").contains("分/千token"));
        assertTrue("文生图输入图片token价格应标明分/千token",
                detail.description().get("imageInputTokenPrice").contains("分/千token"));
        assertTrue("文生图输出图片token价格应标明分/千token",
                detail.description().get("imageOutputTokenPrice").contains("分/千token"));
        assertTrue("文生图图片token价格仍应标明分/千token",
                detail.description().get("imageTokenPrice").contains("分/千token"));
        assertTrue("图生图图片token价格仍应标明分/千token",
                editsPriceInfo.description().get("imageTokenPrice").contains("分/千token"));

        detail.setTextTokenPrice(new BigDecimal("1"));
        detail.setImageInputTokenPrice(new BigDecimal("2"));
        detail.setImageOutputTokenPrice(new BigDecimal("3"));
        assertTrue("文生图展示字符串应包含文字token价格", detail.toString().contains("文字token价格：1"));
        assertTrue("文生图展示字符串应包含输入图片token价格", detail.toString().contains("输入图片token价格：2"));
        assertTrue("文生图展示字符串应包含输出图片token价格", detail.toString().contains("输出图片token价格：3"));
    }

    @Test
    public void testImagesCostIncludesPerImageCentsAndTokenPricing() {
        ImagesPriceInfo priceInfo = new ImagesPriceInfo();
        ImagesPriceInfo.ImagesPriceInfoDetailsList details = new ImagesPriceInfo.ImagesPriceInfoDetailsList();

        ImagesPriceInfo.ImagesPriceInfoDetails detail = new ImagesPriceInfo.ImagesPriceInfoDetails();
        detail.setSize("1024x1024");
        detail.setHdPricePerImage(new BigDecimal("10"));
        detail.setMdPricePerImage(new BigDecimal("6"));
        detail.setLdPricePerImage(new BigDecimal("3"));
        detail.setTextTokenPrice(new BigDecimal("2"));
        detail.setImageTokenPrice(new BigDecimal("4"));
        details.add(detail);

        priceInfo.setDetails(details);

        ImagesResponse.Usage usage = new ImagesResponse.Usage();
        usage.setQuality("high");
        usage.setSize("1024x1024");
        usage.setNum(2);
        ImagesResponse.InputTokensDetails inputTokensDetails = new ImagesResponse.InputTokensDetails();
        inputTokensDetails.setText_tokens(1500);
        inputTokensDetails.setImage_tokens(250);
        usage.setInput_tokens_details(inputTokensDetails);

        CostDetails costDetails = CostCalculator.calculate("/v1/images/generations", JacksonUtils.serialize(priceInfo), usage);

        BigDecimal expectedCost = new BigDecimal("10").multiply(new BigDecimal("2"))
                .add(new BigDecimal("2").multiply(new BigDecimal("1.5")))
                .add(new BigDecimal("4").multiply(new BigDecimal("0.25")));
        assertEquals("Images总成本应直接使用分/张并叠加分/千token成本", 0, expectedCost.compareTo(costDetails.getTotalCost()));
    }

    @Test
    public void testGptImageTokenBillingUsesOutputTokensWithoutPerImageCost() {
        ImagesPriceInfo priceInfo = new ImagesPriceInfo();
        priceInfo.setBillingMode("token");
        ImagesPriceInfo.ImagesPriceInfoDetailsList details = new ImagesPriceInfo.ImagesPriceInfoDetailsList();
        ImagesPriceInfo.ImagesPriceInfoDetails detail = new ImagesPriceInfo.ImagesPriceInfoDetails();
        detail.setSize("1024x1024");
        detail.setHdPricePerImage(new BigDecimal("10"));
        detail.setMdPricePerImage(new BigDecimal("6"));
        detail.setLdPricePerImage(new BigDecimal("3"));
        detail.setTextTokenPrice(new BigDecimal("1"));
        detail.setImageInputTokenPrice(new BigDecimal("2"));
        detail.setImageOutputTokenPrice(new BigDecimal("8"));
        details.add(detail);
        priceInfo.setDetails(details);

        ImagesResponse.Usage usage = new ImagesResponse.Usage();
        usage.setQuality("high");
        usage.setSize("1024x1024");
        usage.setNum(2);
        usage.setOutput_tokens(500);
        ImagesResponse.InputTokensDetails inputTokensDetails = new ImagesResponse.InputTokensDetails();
        inputTokensDetails.setText_tokens(1000);
        inputTokensDetails.setImage_tokens(250);
        usage.setInput_tokens_details(inputTokensDetails);

        CostDetails costDetails = CostCalculator.calculate("/v1/images/generations", JacksonUtils.serialize(priceInfo), usage);

        BigDecimal expectedCost = new BigDecimal("1")
                .add(new BigDecimal("2").multiply(new BigDecimal("0.25")))
                .add(new BigDecimal("8").multiply(new BigDecimal("0.5")));
        assertEquals("GPT Image token模式应只计算token且包含output_tokens", 0, expectedCost.compareTo(costDetails.getTotalCost()));
    }

    @Test
    public void testGptImageTokenBillingUsesLegacyImageTokenPriceAsFallback() {
        ImagesPriceInfo priceInfo = new ImagesPriceInfo();
        priceInfo.setBillingMode("token");
        ImagesPriceInfo.ImagesPriceInfoDetailsList details = new ImagesPriceInfo.ImagesPriceInfoDetailsList();
        ImagesPriceInfo.ImagesPriceInfoDetails detail = new ImagesPriceInfo.ImagesPriceInfoDetails();
        detail.setSize("1024x1024");
        detail.setImageTokenPrice(new BigDecimal("4"));
        details.add(detail);
        priceInfo.setDetails(details);

        ImagesResponse.Usage usage = new ImagesResponse.Usage();
        usage.setSize("1024x1024");
        usage.setOutput_tokens(1500);
        ImagesResponse.InputTokensDetails inputTokensDetails = new ImagesResponse.InputTokensDetails();
        inputTokensDetails.setImage_tokens(500);
        usage.setInput_tokens_details(inputTokensDetails);

        CostDetails costDetails = CostCalculator.calculate("/v1/images/generations", JacksonUtils.serialize(priceInfo), usage);

        BigDecimal expectedCost = new BigDecimal("4").multiply(new BigDecimal("0.5"))
                .add(new BigDecimal("4").multiply(new BigDecimal("1.5")));
        assertEquals("旧imageTokenPrice应作为输入/输出图片token兜底价", 0, expectedCost.compareTo(costDetails.getTotalCost()));
    }

    @Test
    public void testImageTokenBillingValidationRequiresOutputTokenPrice() {
        ImagesPriceInfo priceInfo = new ImagesPriceInfo();
        priceInfo.setBillingMode("token");
        ImagesPriceInfo.ImagesPriceInfoDetailsList details = new ImagesPriceInfo.ImagesPriceInfoDetailsList();
        ImagesPriceInfo.ImagesPriceInfoDetails detail = new ImagesPriceInfo.ImagesPriceInfoDetails();
        detail.setTextTokenPrice(new BigDecimal("1"));
        detail.setImageInputTokenPrice(new BigDecimal("2"));
        details.add(detail);
        priceInfo.setDetails(details);

        assertFalse("token模式缺少输出图片token单价时校验应失败",
                CostCalculator.validate("/v1/images/generations", JacksonUtils.serialize(priceInfo)));

        detail.setImageOutputTokenPrice(new BigDecimal("8"));
        assertTrue("token模式配置imageOutputTokenPrice后校验应通过",
                CostCalculator.validate("/v1/images/generations", JacksonUtils.serialize(priceInfo)));

        detail.setImageOutputTokenPrice(null);
        detail.setImageTokenPrice(new BigDecimal("4"));
        assertTrue("token模式可使用旧imageTokenPrice作为输出图片token兜底价",
                CostCalculator.validate("/v1/images/generations", JacksonUtils.serialize(priceInfo)));
    }

    @Test
    public void testDoubaoSeedreamPerImageBillingIgnoresTokensAndUsesMappedGeneratedImages() {
        ImagesPriceInfo priceInfo = new ImagesPriceInfo();
        priceInfo.setBillingMode("per_image");
        ImagesPriceInfo.ImagesPriceInfoDetailsList details = new ImagesPriceInfo.ImagesPriceInfoDetailsList();
        ImagesPriceInfo.ImagesPriceInfoDetails detail = new ImagesPriceInfo.ImagesPriceInfoDetails();
        detail.setSize("1024x1024");
        detail.setHdPricePerImage(new BigDecimal("9"));
        detail.setMdPricePerImage(new BigDecimal("5"));
        detail.setLdPricePerImage(new BigDecimal("2"));
        detail.setImageOutputTokenPrice(new BigDecimal("100"));
        details.add(detail);
        priceInfo.setDetails(details);

        ImagesResponse.Usage usage = JacksonUtils.deserialize(
                "{\"generated_images\":3,\"quality\":\"high\",\"size\":\"1024x1024\",\"output_tokens\":10000}",
                ImagesResponse.Usage.class);

        CostDetails costDetails = CostCalculator.calculate("/v1/images/generations", JacksonUtils.serialize(priceInfo), usage);

        assertEquals("Doubao per_image模式应使用映射后的num且忽略token", 0,
                new BigDecimal("27").compareTo(costDetails.getTotalCost()));
    }

    @Test
    public void testDallEPerImageBillingNormalizesStandardQualityToMedium() {
        ImagesPriceInfo priceInfo = new ImagesPriceInfo();
        priceInfo.setBillingMode("per_image");
        ImagesPriceInfo.ImagesPriceInfoDetailsList details = new ImagesPriceInfo.ImagesPriceInfoDetailsList();
        ImagesPriceInfo.ImagesPriceInfoDetails detail = new ImagesPriceInfo.ImagesPriceInfoDetails();
        detail.setSize("1792x1024");
        detail.setHdPricePerImage(new BigDecimal("20"));
        detail.setMdPricePerImage(new BigDecimal("10"));
        detail.setLdPricePerImage(new BigDecimal("4"));
        details.add(detail);
        priceInfo.setDetails(details);

        ImagesResponse.Usage usage = new ImagesResponse.Usage();
        usage.setQuality("standard");
        usage.setSize("1792x1024");
        usage.setNum(2);

        CostDetails costDetails = CostCalculator.calculate("/v1/images/generations", JacksonUtils.serialize(priceInfo), usage);

        assertEquals("DALL-E standard质量应按medium价格计费", 0, new BigDecimal("20").compareTo(costDetails.getTotalCost()));
    }

    @Test
    public void testImagesLogHandlerKeepsLegacyNormalizationWhenBillingModeMissing() {
        ImagesPriceInfo priceInfo = new ImagesPriceInfo();
        ImagesPriceInfo.ImagesPriceInfoDetailsList details = new ImagesPriceInfo.ImagesPriceInfoDetailsList();
        ImagesPriceInfo.ImagesPriceInfoDetails detail = new ImagesPriceInfo.ImagesPriceInfoDetails();
        detail.setSize("1024x1024");
        detail.setHdPricePerImage(new BigDecimal("10"));
        detail.setMdPricePerImage(new BigDecimal("6"));
        detail.setLdPricePerImage(new BigDecimal("3"));
        details.add(detail);
        priceInfo.setDetails(details);

        EndpointProcessData processData = new EndpointProcessData();
        processData.setPriceInfo(JacksonUtils.serialize(priceInfo));
        ImagesRequest request = new ImagesRequest();
        request.setN(4);
        request.setQuality("standard");
        request.setSize("1792x1024");
        processData.setRequest(request);
        processData.setResponse(new ImagesResponse());

        new ImagesLogHandler() {
            @Override
            public String endpoint() {
                return "/v1/images/generations";
            }
        }.process(processData);

        ImagesResponse.Usage usage = (ImagesResponse.Usage) processData.getUsage();
        assertEquals("legacy模式应保持无data时按1张记录", Integer.valueOf(1), usage.getNum());
        assertEquals("legacy模式应保持默认high质量", "high", usage.getQuality());
        assertEquals("legacy模式应保持默认1024x1024尺寸", "1024x1024", usage.getSize());
    }

    @Test
    public void testImagesLogHandlerNormalizesRequestForExplicitBillingMode() {
        ImagesPriceInfo priceInfo = new ImagesPriceInfo();
        priceInfo.setBillingMode("per_image");
        ImagesPriceInfo.ImagesPriceInfoDetailsList details = new ImagesPriceInfo.ImagesPriceInfoDetailsList();
        ImagesPriceInfo.ImagesPriceInfoDetails detail = new ImagesPriceInfo.ImagesPriceInfoDetails();
        detail.setSize("1792x1024");
        detail.setHdPricePerImage(new BigDecimal("20"));
        detail.setMdPricePerImage(new BigDecimal("10"));
        detail.setLdPricePerImage(new BigDecimal("4"));
        details.add(detail);
        priceInfo.setDetails(details);

        ImagesResponse response = new ImagesResponse();
        response.setUsage(new ImagesResponse.Usage());
        response.getUsage().setNum(2);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setPriceInfo(JacksonUtils.serialize(priceInfo));
        ImagesRequest request = new ImagesRequest();
        request.setN(4);
        request.setQuality("standard");
        request.setSize("1792x1024");
        processData.setRequest(request);
        processData.setResponse(response);

        new ImagesLogHandler() {
            @Override
            public String endpoint() {
                return "/v1/images/generations";
            }
        }.process(processData);

        ImagesResponse.Usage usage = (ImagesResponse.Usage) processData.getUsage();
        assertEquals("显式模式应优先保留usage张数", Integer.valueOf(2), usage.getNum());
        assertEquals("显式模式应归一化standard为medium", "medium", usage.getQuality());
        assertEquals("显式模式应保留request尺寸", "1792x1024", usage.getSize());
    }

    @Test
    public void testGeneratedImagesUsageMapsToNumWithoutSerializingProviderField() {
        ImagesResponse.Usage usage = JacksonUtils.deserialize("{\"generated_images\":2}", ImagesResponse.Usage.class);

        assertEquals("上游generated_images应映射到协议内num字段", Integer.valueOf(2), usage.getNum());
        String serialized = JacksonUtils.serialize(usage);
        assertTrue("序列化应输出num字段", serialized.contains("\"num\":2"));
        assertFalse("序列化不应输出上游generated_images字段", serialized.contains("generated_images"));
    }

    @Test
    public void testImagesEditsCostUsesPerEditCentsAndTokenPricing() {
        ImagesEditsPriceInfo priceInfo = new ImagesEditsPriceInfo();
        priceInfo.setPricePerEdit(new BigDecimal("7"));
        priceInfo.setImageTokenPrice(new BigDecimal("2"));

        ImagesResponse.Usage usage = new ImagesResponse.Usage();
        usage.setNum(4);
        usage.setTotal_tokens(2500);

        CostDetails costDetails = CostCalculator.calculate("/v1/images/edits", JacksonUtils.serialize(priceInfo), usage);

        BigDecimal expectedCost = new BigDecimal("7").multiply(new BigDecimal("4"))
                .add(new BigDecimal("2").multiply(new BigDecimal("2.5")));
        assertEquals("Images edits总成本应直接使用分/张并叠加分/千token成本", 0, expectedCost.compareTo(costDetails.getTotalCost()));
    }

    /**
     * 测试场景6: 验证所有 endpoint 返回的 CostDetails 结构
     */
    @Test
    public void testAllEndpointsReturnCostDetailsStructure() {
        // 所有 endpoint 都应该返回 CostDetails 对象（不是 null）

        // Embedding
        EmbeddingPriceInfo embeddingPrice = new EmbeddingPriceInfo();
        embeddingPrice.setInput(new BigDecimal("1"));
        EmbeddingResponse.TokenUsage embeddingUsage = new EmbeddingResponse.TokenUsage();
        embeddingUsage.setPrompt_tokens(1000);
        CostDetails embeddingDetails = CostCalculator.calculate("/v1/embeddings",
                JacksonUtils.serialize(embeddingPrice), embeddingUsage);
        assertNotNull("Embedding应返回CostDetails", embeddingDetails);
        assertNotNull("Embedding应有totalCost", embeddingDetails.getTotalCost());

        // TTS
        TtsPriceInfo ttsPrice = new TtsPriceInfo();
        ttsPrice.setInput(new BigDecimal("1"));
        CostDetails ttsDetails = CostCalculator.calculate("/v1/audio/speech",
                JacksonUtils.serialize(ttsPrice), 1000);
        assertNotNull("TTS应返回CostDetails", ttsDetails);
        assertNotNull("TTS应有totalCost", ttsDetails.getTotalCost());

        // OCR
        OcrPriceInfo ocrPrice = new OcrPriceInfo();
        ocrPrice.setPricePerRequest(new BigDecimal("1"));
        CostDetails ocrDetails = CostCalculator.calculate("/v1/ocr/general",
                JacksonUtils.serialize(ocrPrice), 1);
        assertNotNull("OCR应返回CostDetails", ocrDetails);
        assertNotNull("OCR应有totalCost", ocrDetails.getTotalCost());

        // Video
        VideoPriceInfo videoPrice = new VideoPriceInfo();
        videoPrice.setOutput(new BigDecimal("1"));
        VideoUsage videoUsage = new VideoUsage();
        videoUsage.setCompletion_tokens(1000);
        CostDetails videoDetails = CostCalculator.calculate("/v1/videos",
                JacksonUtils.serialize(videoPrice), videoUsage);
        assertNotNull("Video应返回CostDetails", videoDetails);
        assertNotNull("Video应有totalCost", videoDetails.getTotalCost());
    }
}
