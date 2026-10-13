package com.ke.bella.openapi;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.GregorianCalendar;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ke.bella.openapi.utils.MatchUtils.isAllText;
import static com.ke.bella.openapi.utils.MatchUtils.isBracesWithSpaces;
import static com.ke.bella.openapi.utils.MatchUtils.isTextStart;
import static com.ke.bella.openapi.utils.MatchUtils.isValidURL;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Author: Stan Sai Date: 2024/8/5 12:44 description:
 */
@RunWith(JUnit4.class)
public class PatternTest {
    private static final LoadingCache<String, Pattern> cache = CacheBuilder.newBuilder()
            .build(new CacheLoader<String, Pattern>() {
                @Override
                public Pattern load(String key) {
                    String regex = StringUtils.replace(key, "*", "\\d+");
                    return Pattern.compile(regex);
                }
            });
    private static final Pattern versionPath = Pattern.compile(".*/v\\d+/.*");
    private static boolean mathPath(String match, String path) {
        try {
            Matcher matcher = cache.get(match).matcher(path);
            return matcher.matches();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test() {
        assertThat("/v1/completion".matches(".*/v\\d+/.*")).isTrue();
        assertThat("/v3/completion".matches(".*/v\\d+/.*")).isTrue();
        assertThat("/v1/asr".matches(".*/v\\d+/.*")).isTrue();
        assertThat("v1/completion".matches(".*/v\\d+/.*")).isFalse();
        assertThat("/v/completion".matches(".*/v\\d+/.*")).isFalse();
        assertThat(mathPath("/v*/completion", "/v1/completion")).isTrue();
        assertThat(mathPath("/v*/completion", "/v3/completion")).isTrue();
        assertThat(mathPath("/v*/speech", "/v1/speech/stream")).isFalse();
        assertThat(mathPath("/v*/completion", "/v1/file")).isFalse();
        assertThat(isTextStart("com131")).isTrue();
        assertThat(isTextStart("哈哈哈cnn1113")).isTrue();
        assertThat(isTextStart("1adhkj111哈哈")).isFalse();
        assertThat(isTextStart("@adhkj111哈哈")).isFalse();
        assertThat(isTextStart(" adhkj111哈哈")).isFalse();
        assertThat(isTextStart("  ")).isFalse();
        assertThat(isAllText("com")).isTrue();
        assertThat(isAllText("哈哈哈")).isTrue();
        assertThat(isAllText("com131")).isFalse();
        assertThat(isAllText("  ")).isFalse();
        assertThat(isAllText(" 哈哈哈")).isFalse();
        assertThat(isBracesWithSpaces("{}")).isTrue(); // true
        assertThat(isBracesWithSpaces("{ }")).isTrue(); // true
        assertThat(isBracesWithSpaces("{    }")).isTrue(); // true
        assertThat(isBracesWithSpaces("{a}")).isFalse(); // false
        assertThat(isBracesWithSpaces("{  a  }")).isFalse(); // false
        assertThat(isBracesWithSpaces("{")).isFalse(); // false
        assertThat(isBracesWithSpaces("}")).isFalse(); // false
        assertThat(isValidURL("http://example.com")).isTrue(); // true
        assertThat(isValidURL("https://example.com")).isTrue(); // true
        assertThat(isValidURL("ftp://example.com")).isFalse(); // false
        assertThat(isValidURL("example.com")).isFalse(); // false
        assertThat(isValidURL("")).isFalse(); // false
    }

    @Test
    public void test2() {
        System.out.println(String.format("%03d", Integer.parseInt("001") + 1));
        System.out.println(String.format("%03d", Integer.parseInt("999") + 1));
    }

    @Test
    public void test3() {
        ExpressionParser parser = new SpelExpressionParser();
        String spel = "T(String).format('%s:%s', '2222', '333')";
        System.out.println(parser.parseExpression(spel).getValue(String.class));
    }
}
