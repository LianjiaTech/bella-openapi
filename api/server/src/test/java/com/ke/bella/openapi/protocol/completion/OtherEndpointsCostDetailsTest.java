package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.cost.CostCalculator;
import com.ke.bella.openapi.protocol.cost.CostDetails;
import com.ke.bella.openapi.protocol.embedding.EmbeddingPriceInfo;
import com.ke.bella.openapi.protocol.embedding.EmbeddingResponse;
import com.ke.bella.openapi.protocol.images.ImagesPriceInfo;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.protocol.ocr.OcrPriceInfo;
import com.ke.bella.openapi.protocol.tts.TtsPriceInfo;
import com.ke.bella.openapi.protocol.video.VideoPriceInfo;
import com.ke.bella.openapi.protocol.video.VideoUsage;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.math.BigDecimal;

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
