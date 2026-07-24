package com.ke.bella.openapi.simulation;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;

import lombok.Getter;

public class FunctionCallContentBuffer extends Reader {
    private StringBuilder sb = new StringBuilder();
    private int position = 0;
    private boolean writingCompleted = false;
    @Getter
    private List<StreamCompletionResponse> lasts = new ArrayList<>();
    @Getter
    private StreamCompletionResponse last;

    @Override
    public boolean ready() throws IOException {
        return position < sb.length();
    }

    @Override
    public synchronized int read(char[] cbuf, int off, int len) throws IOException {
        while (position >= sb.length() && !writingCompleted) {
            try {
                wait(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("解析 function call 时线程中断", e);
            }
        }

        if(position >= sb.length() && writingCompleted) {
            return -1; // 已经读取到末尾，并且写入完成
        }

        int charsToRead = Math.min(len, sb.length() - position);
        sb.getChars(position, position + charsToRead, cbuf, off);
        position += charsToRead;
        return charsToRead;
    }

    @Override
    public synchronized void close() throws IOException {
        // no-op
    }

    public synchronized void append(StreamCompletionResponse msg) {
        String delta = msg.content();
        sb.append(delta);
        notifyAll();

        last = msg;
        if(msg.getChoices().isEmpty() || StringUtils.isNoneEmpty(msg.finishReason())) {
            lasts.add(msg);
        }
    }

    public synchronized void finish() {
        writingCompleted = true;
        notifyAll();
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
