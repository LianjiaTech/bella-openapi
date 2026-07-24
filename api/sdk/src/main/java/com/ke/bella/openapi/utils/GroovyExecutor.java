package com.ke.bella.openapi.utils;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

public class GroovyExecutor {

    // 缓存编译后的脚本
    private static final Cache<String, Script> scriptCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    private static final GroovyClassLoader classLoader = new GroovyClassLoader();

    // 用于执行限时任务的线程池
    private static final ExecutorService executorService = new ThreadPoolExecutor(0, 5, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(100));

    /**
     * 执行Groovy脚本
     *
     * @param scriptText 脚本文本
     * @param params     脚本参数
     * 
     * @return 脚本执行结果
     */
    public static Object executeScript(String scriptText, Map<String, Object> params) {
        // 获取编译后的脚本
        Script script = getCompiledScript(scriptText);

        // 创建绑定并设置参数
        Binding binding = createBinding(params);

        // 设置绑定并执行脚本
        script.setBinding(binding);
        return script.run();
    }

    /**
     * 测试Groovy脚本执行，带有时间和内存限制
     *
     * @param scriptText       脚本文本
     * @param params           脚本参数
     * @param timeoutMs        超时时间（毫秒）
     * @param memoryLimitBytes 内存限制（字节）
     * 
     * @return 脚本执行结果
     * 
     * @throws Exception 执行过程中的异常
     */
    public static Object testScript(String scriptText, Map<String, Object> params, long timeoutMs, long memoryLimitBytes) {
        // 获取编译后的脚本
        Script script = getCompiledScript(scriptText);

        // 创建绑定并设置参数
        Binding binding = createBinding(params);
        script.setBinding(binding);

        // 创建一个可以在单独线程中执行的任务
        Callable<Object> task = () -> {
            // 记录初始内存使用
            long initialMemory = getUsedMemory();

            // 执行脚本
            Object result = script.run();

            // 检查内存使用是否超过限制
            long usedMemory = getUsedMemory() - initialMemory;
            if(usedMemory > memoryLimitBytes) {
                throw new MemoryLimitExceededException(
                        "脚本超出内存限制: 使用了 " + usedMemory + " 字节, 限制为 " +
                                memoryLimitBytes + " 字节");
            }

            return result;
        };

        // 在单独的线程中执行任务，并设置超时
        Future<Object> future = executorService.submit(task);
        try {
            // 等待任务完成，但不超过指定的超时时间
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 超时后取消任务
            future.cancel(true);
            throw new RuntimeException("脚本执行超时，超过 " + timeoutMs + " 毫秒");
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取编译后的脚本
     */
    private static Script getCompiledScript(String scriptText) {
        return scriptCache.get(scriptText, key -> {
            GroovyShell shell = new GroovyShell(classLoader);
            return shell.parse(key);
        });
    }

    /**
     * 创建绑定并设置参数
     */
    private static Binding createBinding(Map<String, Object> params) {
        Binding binding = new Binding();
        if(params != null) {
            params.forEach(binding::setVariable);
        }
        return binding;
    }

    /**
     * 获取当前使用的内存量
     */
    private static long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * 脚本超时异常
     */
    public static class ScriptTimeoutException extends Exception {
        private static final long serialVersionUID = 1L;

        public ScriptTimeoutException(String message) {
            super(message);
        }
    }

    /**
     * 内存限制超出异常
     */
    public static class MemoryLimitExceededException extends Exception {
        private static final long serialVersionUID = 1L;

        public MemoryLimitExceededException(String message) {
            super(message);
        }
    }
}
