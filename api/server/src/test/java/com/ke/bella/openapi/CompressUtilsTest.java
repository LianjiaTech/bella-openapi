package com.ke.bella.openapi;

import com.ke.bella.openapi.utils.CompressUtils;
import org.junit.Assert;
import org.junit.Test;

public class CompressUtilsTest {

    @Test
    public void testCompressAndDecompress() {
        String original = "Hello, this is a test string for gzip compression.";
        String compressed = CompressUtils.compress(original);
        Assert.assertNotNull(compressed);
        Assert.assertNotEquals(original, compressed);

        String decompressed = CompressUtils.decompress(compressed);
        Assert.assertEquals(original, decompressed);
    }

    @Test
    public void testEmptyString() {
        Assert.assertEquals("", CompressUtils.compress(""));
        Assert.assertEquals("", CompressUtils.decompress(""));
    }

    @Test
    public void testNullInput() {
        Assert.assertNull(CompressUtils.compress(null));
        Assert.assertNull(CompressUtils.decompress(null));
    }

    @Test
    public void testChineseContent() {
        String original = "这是一段中文测试内容，包含特殊字符：！@#￥%……&*（）";
        String compressed = CompressUtils.compress(original);
        String decompressed = CompressUtils.decompress(compressed);
        Assert.assertEquals(original, decompressed);
    }

    @Test
    public void testLargeContent() {
        // Generate a large JSON-like string (~100KB)
        StringBuilder sb = new StringBuilder();
        sb.append("{\"messages\":[");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"role\":\"user\",\"content\":\"This is message number ").append(i)
              .append(" with some repeated content to simulate a real request payload.\"}");
        }
        sb.append("]}");
        String original = sb.toString();

        String compressed = CompressUtils.compress(original);
        String decompressed = CompressUtils.decompress(compressed);
        Assert.assertEquals(original, decompressed);

        // Verify compression ratio is significant for repetitive JSON
        double ratio = (double) compressed.length() / original.length();
        Assert.assertTrue("Compression ratio should be less than 0.5 for repetitive JSON, got: " + ratio, ratio < 0.5);
    }
}
