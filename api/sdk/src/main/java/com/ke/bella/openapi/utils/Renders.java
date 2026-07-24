package com.ke.bella.openapi.utils;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class Renders {
    private static final PebbleEngine engine = new PebbleEngine.Builder()
            .loader(new ClasspathLoader())
            .autoEscaping(false)
            .newLineTrimming(true)
            .build();

    public static String render(String tmpl, Map<String, Object> context) {
        String text = tmpl;
        try {
            PebbleTemplate t = engine.getTemplate(tmpl);

            Writer writer = new StringWriter();
            t.evaluate(writer, context);

            text = writer.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("模版渲染失败: " + e.getMessage(), e);
        }

        return text;
    }
}
