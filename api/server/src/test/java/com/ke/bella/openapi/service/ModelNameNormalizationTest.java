package com.ke.bella.openapi.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnit4.class)
public class ModelNameNormalizationTest {

    private static final Pattern DIGIT_UNDERSCORE_DIGIT = Pattern.compile("(\\d)_(?=\\d)");

    private String normalize(String modelName) {
        return DIGIT_UNDERSCORE_DIGIT.matcher(modelName).replaceAll("$1.");
    }

    @Test
    public void testDigitUnderscoreReplacedWithDot() {
        assertThat(normalize("claude_opus_4_7")).isEqualTo("claude_opus_4.7");
    }

    @Test
    public void testMultiSegmentVersionNumber() {
        assertThat(normalize("some_model_1_2_3")).isEqualTo("some_model_1.2.3");
    }

    @Test
    public void testAlreadyCorrectFormat() {
        assertThat(normalize("claude_opus_4.7")).isEqualTo("claude_opus_4.7");
    }

    @Test
    public void testNoDigitUnderscoreNoChange() {
        assertThat(normalize("gpt-4o")).isEqualTo("gpt-4o");
    }

    @Test
    public void testNonDigitUnderscoreNotAffected() {
        assertThat(normalize("my_model_name")).isEqualTo("my_model_name");
    }

    @Test
    public void testHyphenatedWithDigitUnderscore() {
        assertThat(normalize("claude-3_5-sonnet")).isEqualTo("claude-3.5-sonnet");
    }

    @Test
    public void testMixedFormat() {
        assertThat(normalize("model_v2_1_beta")).isEqualTo("model_v2.1_beta");
    }
}
