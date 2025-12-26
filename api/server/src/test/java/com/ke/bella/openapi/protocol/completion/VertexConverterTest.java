package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.completion.gemini.UsageMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VertexConverterTest {

	@Test
	void testConvertUsage_WithNull_ShouldReturnNull() {
		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(null);

		assertNull(result);
	}

	@Test
	void testConvertUsage_WithBasicTokenCounts_ShouldConvertAllFields() {
		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(100)
				.candidatesTokenCount(50)
				.totalTokenCount(150)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertEquals(100, result.getPrompt_tokens());
		assertEquals(50, result.getCompletion_tokens());
		assertEquals(150, result.getTotal_tokens());
		assertEquals(0, result.getCache_read_tokens());
		assertNull(result.getPrompt_tokens_details());
		assertNull(result.getCompletion_tokens_details());
	}

	@Test
	void testConvertUsage_WithCachedTokens_ShouldSetBothCacheFieldsCorrectly() {
		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(100)
				.candidatesTokenCount(50)
				.totalTokenCount(150)
				.cachedContentTokenCount(30)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertEquals(100, result.getPrompt_tokens());
		assertEquals(50, result.getCompletion_tokens());
		assertEquals(150, result.getTotal_tokens());
		assertEquals(30, result.getCache_read_tokens());
		assertNotNull(result.getPrompt_tokens_details());
		assertEquals(30, result.getPrompt_tokens_details().getCached_tokens());
	}

	@Test
	void testConvertUsage_WithZeroCachedTokens_ShouldNotSetCacheFields() {
		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(100)
				.candidatesTokenCount(50)
				.totalTokenCount(150)
				.cachedContentTokenCount(0)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertEquals(0, result.getCache_read_tokens());
		assertNull(result.getPrompt_tokens_details());
	}

	@Test
	void testConvertUsage_WithImageTokensInPrompt_ShouldConvertCorrectly() {
		List<UsageMetadata.TokensDetails> promptDetails = Arrays.asList(
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.IMAGE.name())
						.tokenCount(200)
						.build(),
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.TEXT.name())
						.tokenCount(100)
						.build()
		);

		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(300)
				.candidatesTokenCount(50)
				.totalTokenCount(350)
				.promptTokensDetails(promptDetails)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertNotNull(result.getPrompt_tokens_details());
		assertEquals(200, result.getPrompt_tokens_details().getImage_tokens());
		assertEquals(0, result.getPrompt_tokens_details().getAudio_tokens());
	}

	@Test
	void testConvertUsage_WithAudioTokensInPrompt_ShouldConvertCorrectly() {
		List<UsageMetadata.TokensDetails> promptDetails = Arrays.asList(
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.AUDIO.name())
						.tokenCount(150)
						.build()
		);

		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(150)
				.candidatesTokenCount(50)
				.totalTokenCount(200)
				.promptTokensDetails(promptDetails)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertNotNull(result.getPrompt_tokens_details());
		assertEquals(0, result.getPrompt_tokens_details().getImage_tokens());
		assertEquals(150, result.getPrompt_tokens_details().getAudio_tokens());
	}

	@Test
	void testConvertUsage_WithMultipleImageAndAudioTokensInPrompt_ShouldSumCorrectly() {
		List<UsageMetadata.TokensDetails> promptDetails = Arrays.asList(
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.IMAGE.name())
						.tokenCount(100)
						.build(),
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.IMAGE.name())
						.tokenCount(150)
						.build(),
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.AUDIO.name())
						.tokenCount(80)
						.build(),
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.AUDIO.name())
						.tokenCount(70)
						.build()
		);

		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(400)
				.candidatesTokenCount(50)
				.totalTokenCount(450)
				.promptTokensDetails(promptDetails)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertNotNull(result.getPrompt_tokens_details());
		assertEquals(250, result.getPrompt_tokens_details().getImage_tokens());
		assertEquals(150, result.getPrompt_tokens_details().getAudio_tokens());
	}

	@Test
	void testConvertUsage_WithCachedTokensAndImageTokens_ShouldSetBothFieldsCorrectly() {
		List<UsageMetadata.TokensDetails> promptDetails = Arrays.asList(
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.IMAGE.name())
						.tokenCount(200)
						.build()
		);

		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(300)
				.candidatesTokenCount(50)
				.totalTokenCount(350)
				.cachedContentTokenCount(40)
				.promptTokensDetails(promptDetails)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertEquals(40, result.getCache_read_tokens());
		assertNotNull(result.getPrompt_tokens_details());
		assertEquals(40, result.getPrompt_tokens_details().getCached_tokens());
		assertEquals(200, result.getPrompt_tokens_details().getImage_tokens());
		assertEquals(0, result.getPrompt_tokens_details().getAudio_tokens());
	}

	@Test
	void testConvertUsage_WithReasoningTokens_ShouldSetCompletionTokensDetailsCorrectly() {
		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(100)
				.candidatesTokenCount(50)
				.thoughtsTokenCount(20)
				.totalTokenCount(170)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertEquals(100, result.getPrompt_tokens());
		assertEquals(70, result.getCompletion_tokens());
		assertEquals(170, result.getTotal_tokens());
		assertNull(result.getCompletion_tokens_details());
	}

	@Test
	void testConvertUsage_WithImageTokensInCandidates_ShouldSetCompletionTokensDetailsCorrectly() {
		List<UsageMetadata.TokensDetails> candidatesDetails = Arrays.asList(
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.IMAGE.name())
						.tokenCount(100)
						.build()
		);

		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(100)
				.candidatesTokenCount(50)
				.totalTokenCount(150)
				.candidatesTokensDetails(candidatesDetails)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertNotNull(result.getCompletion_tokens_details());
		assertEquals(100, result.getCompletion_tokens_details().getImage_tokens());
		assertEquals(0, result.getCompletion_tokens_details().getAudio_tokens());
		assertEquals(0, result.getCompletion_tokens_details().getReasoning_tokens());
	}

	@Test
	void testConvertUsage_WithAudioTokensInCandidates_ShouldSetCompletionTokensDetailsCorrectly() {
		List<UsageMetadata.TokensDetails> candidatesDetails = Arrays.asList(
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.AUDIO.name())
						.tokenCount(120)
						.build()
		);

		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(100)
				.candidatesTokenCount(50)
				.totalTokenCount(150)
				.candidatesTokensDetails(candidatesDetails)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertNotNull(result.getCompletion_tokens_details());
		assertEquals(0, result.getCompletion_tokens_details().getImage_tokens());
		assertEquals(120, result.getCompletion_tokens_details().getAudio_tokens());
		assertEquals(0, result.getCompletion_tokens_details().getReasoning_tokens());
	}

	@Test
	void testConvertUsage_WithReasoningAndImageTokensInCandidates_ShouldSetAllFieldsCorrectly() {
		List<UsageMetadata.TokensDetails> candidatesDetails = Arrays.asList(
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.IMAGE.name())
						.tokenCount(80)
						.build(),
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.AUDIO.name())
						.tokenCount(60)
						.build()
		);

		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(100)
				.candidatesTokenCount(50)
				.thoughtsTokenCount(30)
				.totalTokenCount(180)
				.candidatesTokensDetails(candidatesDetails)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertEquals(80, result.getCompletion_tokens());
		assertNotNull(result.getCompletion_tokens_details());
		assertEquals(80, result.getCompletion_tokens_details().getImage_tokens());
		assertEquals(60, result.getCompletion_tokens_details().getAudio_tokens());
		assertEquals(30, result.getCompletion_tokens_details().getReasoning_tokens());
	}

	@Test
	void testConvertUsage_WithCacheTokensDetailsForImage_ShouldAddToPromptTokensDetails() {
		List<UsageMetadata.TokensDetails> cacheDetails = Arrays.asList(
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.IMAGE.name())
						.tokenCount(50)
						.build()
		);

		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(100)
				.candidatesTokenCount(50)
				.totalTokenCount(150)
				.cacheTokensDetails(cacheDetails)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertNotNull(result.getPrompt_tokens_details());
		assertEquals(50, result.getPrompt_tokens_details().getImage_tokens());
	}

	@Test
	void testConvertUsage_WithCacheTokensDetailsForAudio_ShouldAddToPromptTokensDetails() {
		List<UsageMetadata.TokensDetails> cacheDetails = Arrays.asList(
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.AUDIO.name())
						.tokenCount(70)
						.build()
		);

		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(100)
				.candidatesTokenCount(50)
				.totalTokenCount(150)
				.cacheTokensDetails(cacheDetails)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertNotNull(result.getPrompt_tokens_details());
		assertEquals(70, result.getPrompt_tokens_details().getAudio_tokens());
	}

	@Test
	void testConvertUsage_WithCacheTokensDetailsTextModality_ShouldNotAddToPromptTokensDetails() {
		List<UsageMetadata.TokensDetails> cacheDetails = Arrays.asList(
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.TEXT.name())
						.tokenCount(40)
						.build()
		);

		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(100)
				.candidatesTokenCount(50)
				.totalTokenCount(150)
				.cacheTokensDetails(cacheDetails)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertNull(result.getPrompt_tokens_details());
	}

	@Test
	void testConvertUsage_WithComplexScenario_ShouldConvertAllFieldsCorrectly() {
		List<UsageMetadata.TokensDetails> promptDetails = Arrays.asList(
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.IMAGE.name())
						.tokenCount(100)
						.build(),
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.AUDIO.name())
						.tokenCount(80)
						.build()
		);

		List<UsageMetadata.TokensDetails> candidatesDetails = Arrays.asList(
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.IMAGE.name())
						.tokenCount(60)
						.build()
		);

		List<UsageMetadata.TokensDetails> cacheDetails = Arrays.asList(
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.IMAGE.name())
						.tokenCount(30)
						.build(),
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.AUDIO.name())
						.tokenCount(20)
						.build()
		);

		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(300)
				.candidatesTokenCount(80)
				.thoughtsTokenCount(25)
				.totalTokenCount(405)
				.cachedContentTokenCount(50)
				.promptTokensDetails(promptDetails)
				.candidatesTokensDetails(candidatesDetails)
				.cacheTokensDetails(cacheDetails)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertEquals(300, result.getPrompt_tokens());
		assertEquals(105, result.getCompletion_tokens());
		assertEquals(405, result.getTotal_tokens());
		assertEquals(50, result.getCache_read_tokens());
		
		assertNotNull(result.getPrompt_tokens_details());
		assertEquals(50, result.getPrompt_tokens_details().getCached_tokens());
		assertEquals(130, result.getPrompt_tokens_details().getImage_tokens());
		assertEquals(100, result.getPrompt_tokens_details().getAudio_tokens());
		
		assertNotNull(result.getCompletion_tokens_details());
		assertEquals(60, result.getCompletion_tokens_details().getImage_tokens());
		assertEquals(0, result.getCompletion_tokens_details().getAudio_tokens());
		assertEquals(25, result.getCompletion_tokens_details().getReasoning_tokens());
	}

	@Test
	void testConvertUsage_WithEmptyPromptTokensDetails_ShouldCreateEmptyPromptTokensDetails() {
		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(100)
				.candidatesTokenCount(50)
				.totalTokenCount(150)
				.promptTokensDetails(new ArrayList<>())
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertNotNull(result.getPrompt_tokens_details());
		assertEquals(0, result.getPrompt_tokens_details().getImage_tokens());
		assertEquals(0, result.getPrompt_tokens_details().getAudio_tokens());
	}

	@Test
	void testConvertUsage_WithOnlyTextModalityInPromptDetails_ShouldCreateEmptyPromptTokensDetails() {
		List<UsageMetadata.TokensDetails> promptDetails = Arrays.asList(
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.TEXT.name())
						.tokenCount(100)
						.build()
		);

		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(100)
				.candidatesTokenCount(50)
				.totalTokenCount(150)
				.promptTokensDetails(promptDetails)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertNotNull(result.getPrompt_tokens_details());
		assertEquals(0, result.getPrompt_tokens_details().getImage_tokens());
		assertEquals(0, result.getPrompt_tokens_details().getAudio_tokens());
	}

	@Test
	void testConvertUsage_WithOnlyTextModalityInCandidatesDetails_ShouldNotSetCompletionTokensDetails() {
		List<UsageMetadata.TokensDetails> candidatesDetails = Arrays.asList(
				UsageMetadata.TokensDetails.builder()
						.modality(UsageMetadata.Modality.TEXT.name())
						.tokenCount(50)
						.build()
		);

		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(100)
				.candidatesTokenCount(50)
				.totalTokenCount(150)
				.candidatesTokensDetails(candidatesDetails)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertNull(result.getCompletion_tokens_details());
	}

	@Test
	void testConvertUsage_WithNullTokenCounts_ShouldDefaultToZero() {
		UsageMetadata metadata = UsageMetadata.builder()
				.promptTokenCount(null)
				.candidatesTokenCount(null)
				.totalTokenCount(null)
				.build();

		CompletionResponse.TokenUsage result = VertexConverter.convertUsage(metadata);

		assertNotNull(result);
		assertEquals(0, result.getPrompt_tokens());
		assertEquals(0, result.getCompletion_tokens());
		assertEquals(0, result.getTotal_tokens());
	}
}
