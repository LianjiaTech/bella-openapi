package com.ke.bella.openapi.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import lombok.Getter;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * 基于 Netty 池化 DirectBuffer 的 RequestBody 实现
 * 使用 Netty PooledByteBufAllocator 管理堆外内存，减少 GC 压力
 * 优势：
 * 1. 使用 Netty 池化堆外内存（PooledDirectBuffer），大幅减少内存分配开销
 * 2. 自动扩展缓冲区，无需预估大小
 * 3. 支持手动释放，将缓冲区归还到池中复用
 * 4. 避免 byte[] 中间产物，直接序列化到堆外内存
 * 5. 适合高频调用场景（大量图片数据和思维链请求）
 */
public class ByteBufferRequestBody extends RequestBody {

    private static final Logger LOGGER = LogManager.getLogger(ByteBufferRequestBody.class);

    private final MediaType mediaType;
    private ByteBuffer buffer;           // NIO ByteBuffer view for OkHttp
    private ByteBuf pooledByteBuf;       // Netty pooled buffer for release
    /**
     * -- GETTER --
     * 判断是否已释放
     */
    @Getter
    private volatile boolean released = false;

    /**
     * 私有构造函数：持有 ByteBuffer 和 ByteBuf 引用
     *
     * @param mediaType 媒体类型
     * @param buffer    NIO ByteBuffer view (for OkHttp)
     * @param byteBuf   Netty pooled ByteBuf (for release)
     */
    private ByteBufferRequestBody(MediaType mediaType, ByteBuffer buffer, ByteBuf byteBuf) {
        this.mediaType = mediaType;
        this.buffer = buffer;
        this.pooledByteBuf = byteBuf;
    }

    /**
     * 从对象创建 ByteBufferRequestBody，使用 Netty 池化 DirectBuffer
     * 自动扩展缓冲区，无需预估大小
     *
     * @param mediaType 媒体类型
     * @param obj       要序列化的对象
     * 
     * @return ByteBufferRequestBody
     */
    public static ByteBufferRequestBody fromObject(MediaType mediaType, Object obj) {
        // 使用 Netty 池化 DirectBuffer，初始大小 256 字节，自动扩展
        ByteBuf pooledBuf = PooledByteBufAllocator.DEFAULT.directBuffer(256);

        try {
            // 直接序列化到池化 DirectBuffer，避免 byte[] 中间产物
            ByteBufOutputStream outputStream = new ByteBufOutputStream(pooledBuf);
            JsonGenerator generator = JacksonUtils.MAPPER.getFactory()
                    .createGenerator((OutputStream) outputStream);
            JacksonUtils.MAPPER.writeValue(generator, obj);
            generator.close();

            // 获取 NIO ByteBuffer 视图（零拷贝）
            ByteBuffer nioBuffer = pooledBuf.nioBuffer();

            return new ByteBufferRequestBody(mediaType, nioBuffer, pooledBuf);

        } catch (IOException e) {
            // 序列化失败时立即释放 ByteBuf
            pooledBuf.release();
            LOGGER.error("Failed to serialize object to ByteBuffer: " + e.getMessage(), e);
            throw new RuntimeException("Serialization failed", e);
        }
    }

    @Override
    public MediaType contentType() {
        return mediaType;
    }

    @Override
    public long contentLength() {
        if(released) {
            return 0;
        }
        return buffer.remaining();
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        if(released) {
            throw new IllegalStateException("ByteBuffer has been released");
        }

        // 重置 position 到 0，支持 OkHttp 重试机制
        // OkHttp 在网络失败时会重用同一个 RequestBody，多次调用 writeTo()
        buffer.rewind();

        // 创建临时数组用于传输数据
        // 对于图片编辑等大请求（通常 > 1MB），使用 16KB 缓冲区可以减少循环次数
        byte[] tmp = new byte[Math.min(16384, buffer.remaining())];

        while (buffer.hasRemaining()) {
            int length = Math.min(tmp.length, buffer.remaining());
            buffer.get(tmp, 0, length);
            sink.write(tmp, 0, length);
        }

        // 注意：读取完成后 position 已到达 limit
        // 如果 OkHttp 重试，下次 writeTo() 开始时会再次 rewind()
    }

    /**
     * 手动释放 ByteBuffer 和 Netty ByteBuf
     * 将池化 ByteBuf 归还到 Netty 池中，供后续请求复用
     * 优势：
     * 1. 池化复用，避免频繁分配堆外内存
     * 2. 减少 GC 压力，因为 ByteBuf 不需要 GC 回收
     * 3. 降低系统调用开销（申请堆外内存需要 syscall）
     */
    public void release() {
        if(!released && pooledByteBuf != null) {
            released = true;
            buffer = null;
            pooledByteBuf.release();  // 归还到 Netty 池中
            pooledByteBuf = null;
        }
    }

}
