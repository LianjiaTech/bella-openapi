package com.ke.bella.openapi.utils;

import org.springframework.util.AntPathMatcher;

/**
 * Author: Stan Sai Date: 2024/8/5 16:05 description:
 */
public class MatchUtils {
    private static final AntPathMatcher matcher = new AntPathMatcher();

    /**
     * 判断字符串是否以文字开头
     *
     * @param input 要检查的字符串
     *
     * @return 如果字符串以文字开头，则返回true；否则返回false
     */
    public static boolean isTextStart(String input) {
        if(input == null || input.isEmpty()) {
            return false;
        }

        char firstChar = input.charAt(0);
        return Character.isLetter(firstChar);
    }

    /**
     * 判断字符串中是否全是文字
     *
     * @param input 要检查的字符串
     *
     * @return 如果字符串中全是文字，则返回true；否则返回false
     */
    public static boolean isAllText(String input) {
        if(input == null || input.isEmpty()) {
            return false;
        }
        for (char c : input.toCharArray()) {
            if(!Character.isLetter(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断字符串是否是“{}”或“{ }”，即“{”和“}”之间可以有多个空格或没有空格
     *
     * @param input 要检查的字符串
     *
     * @return 如果字符串符合条件，则返回true；否则返回false
     */
    public static boolean isBracesWithSpaces(String input) {
        if(input == null) {
            return false;
        }

        // 使用正则表达式匹配
        return input.matches("\\{\\s*\\}");
    }

    /**
     * 判断字符串是否是以 http:// 或 https:// 开头的 URL
     *
     * @param input 要检查的字符串
     *
     * @return 如果字符串是以 http:// 或 https:// 开头的 URL，则返回 true；否则返回 false
     */
    public static boolean isValidURL(String input) {
        if(input == null || input.isEmpty()) {
            return false;
        }

        // 使用正则表达式匹配
        return input.matches("^(http://|https://).+");
    }

    public static boolean matchUrl(String pattern, String url) {
        return matcher.match(pattern, url);
    }
}
