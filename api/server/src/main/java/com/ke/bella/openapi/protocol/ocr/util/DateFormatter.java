package com.ke.bella.openapi.protocol.ocr.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DateFormatter {

    public static String formatDate(String dateStr, String inputPattern, String outputPattern) {
        if(dateStr == null || dateStr.trim().isEmpty()) {
            return "";
        }

        try {
            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(inputPattern);
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(outputPattern);

            LocalDate date = LocalDate.parse(dateStr.trim(), inputFormatter);
            return date.format(outputFormatter);
        } catch (DateTimeParseException e) {
            log.error("日期格式转换失败: {} (inputPattern: {}, outputPattern: {})", dateStr, inputPattern, outputPattern, e);
            return "";
        }
    }
}
