package com.ke.bella.openapi.utils;

import lombok.Getter;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 基于 ByteBuffer 的 RequestBody 实现
 * 使用直接内存，减少 GC 压力，适用于大数据量的图片和思维链请求
 *
 * 优势：
 * 1. 使用堆外内存（DirectBuffer），减少 Young GC 和 Full GC
 * 2. 支持手动释放，避免内存长时间占用
 * 3. 适合处理大量图片数据和思维链等大对象
 */
public class ByteBufferRequestBody extends RequestBody {

    private final MediaType mediaType;
    private ByteBuffer buffer;
	/**
	 * -- GETTER --
	 *  判断是否已释放
	 */
	@Getter
	private volatile boolean released = false;

    /**
     * 创建基于 ByteBuffer 的 RequestBody
     *
     * @param mediaType 媒体类型
     * @param buffer ByteBuffer（建议使用 DirectByteBuffer）
     */
    public ByteBufferRequestBody(MediaType mediaType, ByteBuffer buffer) {
        this.mediaType = mediaType;
        this.buffer = buffer;
    }

    /**
     * 使用 Direct ByteBuffer 创建（推荐方式）
     *
     * @param mediaType 媒体类型
     * @param data 数据字节数组
     * @return ByteBufferRequestBody
     */
    public static ByteBufferRequestBody createDirect(MediaType mediaType, byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(data.length);
        buffer.put(data);
        buffer.flip();
        return new ByteBufferRequestBody(mediaType, buffer);
    }

    /**
     * 从 Jackson 序列化对象创建 Direct ByteBuffer RequestBody
     * 直接序列化到 DirectByteBuffer，避免堆内存分配
     *
     * @param mediaType 媒体类型
     * @param obj 要序列化的对象
     * @param size 序列化后的字节大小
     * @return ByteBufferRequestBody
     */
    public static ByteBufferRequestBody fromObject(MediaType mediaType, Object obj, int size) {
        // 使用提供的 size 直接序列化，避免二次估算
        ByteBuffer buffer = JacksonUtils.toByteBuffer(obj, size);
        return new ByteBufferRequestBody(mediaType, buffer);
    }

    @Override
    public MediaType contentType() {
        return mediaType;
    }

    @Override
    public long contentLength() {
        if (released) {
            return 0;
        }
        return buffer.remaining();
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        if (released) {
            throw new IllegalStateException("ByteBuffer has been released");
        }

        // 创建临时数组用于传输数据
        // 对于图片编辑等大请求（通常 > 1MB），使用 16KB 缓冲区可以减少循环次数
        byte[] tmp = new byte[Math.min(16384, buffer.remaining())];

        while (buffer.hasRemaining()) {
            int length = Math.min(tmp.length, buffer.remaining());
            buffer.get(tmp, 0, length);
            sink.write(tmp, 0, length);
        }

        // 注意：不再调用 rewind()
        // OkHttp 不会重试 RequestBody，每次请求都会创建新的 RequestBody
        // 如果调用 rewind()，会与 finally 块中的 release() 冲突
    }

    /**
     * 手动释放 ByteBuffer
     * 清除引用，让 GC 回收 DirectByteBuffer
     *
     * 注意：不使用反射调用 cleaner()，原因：
     * 1. Java 9+ 中 cleaner() 方法已被移除
     * 2. 反射访问内部 API 在高版本 Java 中会有警告
     * 3. 依赖 GC 自然回收是安全且推荐的做法
     * 4. DirectByteBuffer 的 Cleaner 会在 GC 时自动释放堆外内存
     */
    public void release() {
        if (!released && buffer != null) {
            released = true;
            buffer = null;  // 清除引用，让 GC 处理
        }
    }

}
