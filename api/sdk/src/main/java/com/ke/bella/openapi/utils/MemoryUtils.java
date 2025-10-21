package com.ke.bella.openapi.utils;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;

import lombok.extern.slf4j.Slf4j;

/**
 * 内存相关工具类
 * 提供内存大小获取和计算功能
 */
@Slf4j
public class MemoryUtils {

    /**
     * 获取可用于线程栈的内存大小（MB）
     *
     * 计算策略：
     * 1. 优先使用 MaxDirectMemorySize（-XX:MaxDirectMemorySize 参数）
     * 2. 如果未设置，则计算：机器物理内存 - 堆内存 - 方法区内存 - 系统预留内存
     * 3. 降级方案：使用堆内存的一半作为估算
     *
     * @return 可用内存大小（MB）
     */
    public static long getAvailableMemoryMB() {
        // 1. 优先尝试获取 MaxDirectMemorySize（-XX:MaxDirectMemorySize 参数）
        // 如果用户显式设置了这个参数，说明希望精确控制直接内存大小
        long maxDirectMemoryMB = getMaxDirectMemoryMB();
        if (maxDirectMemoryMB > 0) {
            log.info("[MemoryUtils] 使用 MaxDirectMemorySize: {} MB", maxDirectMemoryMB);
            return maxDirectMemoryMB;
        }

        // 2. 尝试计算：物理内存 - 堆内存 - 方法区 - 系统预留
        long physicalMemoryMB = getPhysicalMemoryMB();
        if (physicalMemoryMB > 0) {
            long jvmMaxMemoryMB = getJvmMaxMemoryMB();
            long metaspaceMB = getMaxMetaspaceMB();
            long systemReservedMB = 1024; // 系统预留内存（约1GB）

            // 计算可用于线程栈的内存
            long availableMemoryMB = physicalMemoryMB - jvmMaxMemoryMB - metaspaceMB - systemReservedMB;

            if (availableMemoryMB > 512) {
                log.info("[MemoryUtils] 计算可用内存 - 物理内存: {} MB, 堆内存: {} MB, 方法区: {} MB, 系统预留: {} MB, 可用: {} MB",
                        physicalMemoryMB, jvmMaxMemoryMB, metaspaceMB, systemReservedMB, availableMemoryMB);
                return availableMemoryMB;
            } else {
                return 512;
            }
        }

        // 3. 降级方案：使用 JVM 堆内存的一半作为估算
        long jvmMaxMemoryMB = getJvmMaxMemoryMB();
        log.info("[MemoryUtils] 无法计算精确可用内存，使用堆内存的一半作为估算: {} MB", jvmMaxMemoryMB / 2);
        return jvmMaxMemoryMB / 2;
    }

    /**
     * 获取最大方法区/元空间大小（MB）
     *
     * @return 方法区大小（MB），默认256MB
     */
    public static long getMaxMetaspaceMB() {
        try {
            // 尝试通过 JMX 获取 MaxMetaspaceSize
            java.lang.management.MemoryPoolMXBean metaspacePool = null;
            for (java.lang.management.MemoryPoolMXBean pool :
                    java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) {
                if ("Metaspace".equals(pool.getName()) ||
                    "Compressed Class Space".equals(pool.getName())) {
                    metaspacePool = pool;
                    break;
                }
            }

            if (metaspacePool != null && metaspacePool.getUsage().getMax() > 0) {
                return metaspacePool.getUsage().getMax() / (1024 * 1024);
            }
        } catch (Exception e) {
            // 忽略异常
        }

        // 默认值：Java 8+ 默认 MaxMetaspaceSize 是无限制的，但通常实际使用不会超过256MB
        return 256;
    }

    /**
     * 获取机器物理内存大小（MB）
     *
     * @return 物理内存大小（MB），如果无法获取则返回 -1
     */
    public static long getPhysicalMemoryMB() {
        try {
            java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean =
                    (com.sun.management.OperatingSystemMXBean) osBean;
                return sunOsBean.getTotalPhysicalMemorySize() / (1024 * 1024);
            }
        } catch (Exception e) {
            log.warn("[MemoryUtils] 无法获取物理内存: {}", e.getMessage());
        }
        return -1;
    }

    /**
     * 获取 JVM 堆内存大小（MB）
     *
     * @return 堆内存大小（MB）
     */
    public static long getJvmMaxMemoryMB() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }

    /**
     * 获取 MaxDirectMemorySize（MB）
     *
     * @return 直接内存大小（MB），如果无法获取则返回 -1
     */
    public static long getMaxDirectMemoryMB() {
        try {
            Class<?> vmClass = Class.forName("sun.misc.VM");
            Method maxDirectMemoryMethod = vmClass.getMethod("maxDirectMemory");
            Long maxDirectMemory = (Long) maxDirectMemoryMethod.invoke(null);

            if (maxDirectMemory != null && maxDirectMemory > 0) {
                return maxDirectMemory / (1024 * 1024);
            }
        } catch (Exception e) {
            log.warn("[MemoryUtils] 无法获取 MaxDirectMemorySize: {}", e.getMessage());
        }
        return -1;
    }
}
