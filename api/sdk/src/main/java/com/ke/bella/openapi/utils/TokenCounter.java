package com.ke.bella.openapi.utils;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.apache.commons.lang3.tuple.Pair;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;

public class TokenCounter {
    private static final EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();

    /**
     * 不同模型的encodingType:
     * 
     * @see com.knuddels.jtokkit.api.ModelType
     * 
     * @param text
     * @param encodingType
     * 
     * @return
     */
    public static int tokenCount(String text, EncodingType encodingType) {
        Encoding encoding = registry.getEncoding(encodingType);
        return encoding.countTokens(text);
    }

    public static int imageToken(String imageStr, boolean lowResolution) {
        // gpt-4-vision模型，imageToken计算
        // !!! 注意，其他模型的imageToken计算方式可能不同，需要单独实现
        // 2024.05.23补充：4o token计算相同
        Pair<Integer, Integer> imgSize = null;
        if(imageStr.startsWith("http")) {
            imgSize = imageSizeFromUrl(imageStr);
        } else {
            imgSize = imageSizeFromB64(imageStr);
        }
        return imageToken(imgSize.getLeft(), imgSize.getRight(), lowResolution);
    }

    private static Pair<Integer, Integer> imageSizeFromB64(String base64DataUrl) {
        // 从base64DataUrl中提取base64Str：data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAyA...
        String base64Str = base64DataUrl.split(",")[1];
        // 将base64转化为InputStream
        byte[] decodedBytes = Base64.getDecoder().decode(base64Str);
        try (InputStream inputStream = new ByteArrayInputStream(decodedBytes)) {
            // 读取图片url，获取像素宽度，长度
            Image image = ImageIO.read(inputStream);
            // 获取图片的宽度
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            return Pair.of(width, height);
        } catch (Exception e) {
            return Pair.of(0, 0);
        }
    }

    private static Pair<Integer, Integer> imageSizeFromUrl(String imageUrl) {
        try (InputStream inputStream = new URL(imageUrl).openStream()) {
            // 读取图片url，获取像素宽度，长度
            Image image = ImageIO.read(inputStream);
            // 获取图片的宽度
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            return Pair.of(width, height);
        } catch (Exception e) {
            return Pair.of(0, 0);
        }
    }

    public static int imageToken(double widthpx, double heightpx, boolean lowResolution) {
        if(lowResolution) {
            // 如果图片使用低分辨率处理，则图像统一被压缩到512*512范围内，占用token：85
            return 85;
        } else {
            // 如果使用高分辨率处理，则首先使用压缩到512*512范围内的低分辨率进行图像处理，之后切割处理
            return imageTiles(widthpx, heightpx) * 170 + 85;
        }
    }

    /**
     * 计算图像被拆分的单元格
     *
     * @param widthpx
     * @param heightpx
     * 
     * @return
     */
    private static int imageTiles(double widthpx, double heightpx) {
        double max = Math.max(widthpx, heightpx);
        // 将图像缩放到2048*2048范围内
        if(max > 2048) {
            double scale = 2048 / max;
            widthpx = widthpx * scale;
            heightpx = heightpx * scale;
        }
        // 将图像短边缩放到不超过768
        double min = Math.min(widthpx, heightpx);
        if(min > 768) {
            double scale = 768 / min;
            widthpx = widthpx * scale;
            heightpx = heightpx * scale;
        }
        // 将图像切割为512*512单元格
        return (int) (Math.ceil(widthpx / 512) * Math.ceil(heightpx / 512));
    }
}
