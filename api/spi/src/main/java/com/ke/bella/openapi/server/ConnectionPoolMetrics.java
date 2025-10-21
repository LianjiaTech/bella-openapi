package com.ke.bella.openapi.server;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;

/**
 * OkHttp ConnectionPool 监控指标
 *
 * 监控指标:
 * - okhttp.connection.pool.total: 连接池中的总连接数
 * - okhttp.connection.pool.idle: 连接池中的空闲连接数
 * - okhttp.connection.pool.active: 连接池中的活跃连接数
 *
 * 查看具体指标值:
 * curl http://localhost:8080/actuator/metrics/okhttp.connection.pool.total
 * curl http://localhost:8080/actuator/metrics/okhttp.connection.pool.idle
 * curl http://localhost:8080/actuator/metrics/okhttp.connection.pool.active
 */
@Slf4j
public class ConnectionPoolMetrics implements MeterBinder {

    private final ConnectionPool connectionPool;
    private final String poolName;

    public ConnectionPoolMetrics(ConnectionPool connectionPool) {
        this(connectionPool, "default");
    }

    public ConnectionPoolMetrics(ConnectionPool connectionPool, String poolName) {
        this.connectionPool = connectionPool;
        this.poolName = poolName;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        // 总连接数
        Gauge.builder("okhttp.connection.pool.total", connectionPool, pool -> {
            try {
                return pool.connectionCount();
            } catch (Exception e) {
                log.warn("Failed to get connection count", e);
                return 0;
            }
        })
        .tag("pool", poolName)
        .description("Total number of connections in the pool")
        .register(registry);

        // 空闲连接数
        Gauge.builder("okhttp.connection.pool.idle", connectionPool, pool -> {
            try {
                return pool.idleConnectionCount();
            } catch (Exception e) {
                log.warn("Failed to get idle connection count", e);
                return 0;
            }
        })
        .tag("pool", poolName)
        .description("Number of idle connections in the pool")
        .register(registry);

        // 活跃连接数 (总连接数 - 空闲连接数)
        Gauge.builder("okhttp.connection.pool.active", connectionPool, pool -> {
            try {
                return pool.connectionCount() - pool.idleConnectionCount();
            } catch (Exception e) {
                log.warn("Failed to calculate active connections", e);
                return 0;
            }
        })
        .tag("pool", poolName)
        .description("Number of active connections in the pool")
        .register(registry);

        log.info("ConnectionPool metrics registered for pool: {}", poolName);
    }
}
