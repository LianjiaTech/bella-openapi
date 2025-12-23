package com.ke.bella.openapi.configuration;

import java.util.List;

import javax.annotation.PreDestroy;

import com.ke.bella.queue.QueueClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ke.bella.openapi.db.log.LogRepo;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.cost.CostCounter;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.log.CostLogHandler;
import com.ke.bella.openapi.protocol.log.LimiterLogHandler;
import com.ke.bella.openapi.protocol.log.LogEvent;
import com.ke.bella.openapi.protocol.log.LogExceptionHandler;
import com.ke.bella.openapi.protocol.log.LogRecordHandler;
import com.ke.bella.openapi.protocol.log.MetricsLogHandler;
import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.ke.bella.openapi.server.OpenapiProperties;
import com.ke.bella.openapi.service.ApikeyService;
import com.ke.bella.openapi.service.EndpointService;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;


@EnableConfigurationProperties(OpenapiProperties.class)
@Configuration
public class BellaAutoConf {
    private final SleepingWaitStrategy sleepingWaitStrategy = new SleepingWaitStrategy();
    private Disruptor<LogEvent> logDisruptor;
    private CostCounter costCounter;
    @Autowired
    private MetricsManager metricsManager;
    @Autowired
    private LimiterManager limiterManager;
    @Bean
    public AdaptorManager adaptorManager(@Autowired List<IProtocolAdaptor> adaptors) {
        AdaptorManager manager = new AdaptorManager();
        adaptors.forEach(adaptor -> manager.register(adaptor.endpoint(), adaptor));
        return manager;
    }

    @Bean
    public CostCounter.CostRecorder costRecorder(@Autowired ApikeyService service) {
        return service::recordCost;
    }

    @Bean
    public CostLogHandler.CostScripFetcher costScripFetcher(@Autowired EndpointService service) {
        return service::fetchCostScript;
    }

    @Bean
    public CostCounter costCounter(CostCounter.CostRecorder costRecorder) {
        costCounter = new CostCounter(costRecorder);
        return costCounter;
    }

    @Bean
    public RingBuffer<LogEvent> logRingBuffer(List<LogRepo> logRepos, CostCounter costCounter, CostLogHandler.CostScripFetcher costScripFetcher) {
        Disruptor<LogEvent> disruptor = new Disruptor<>(LogEvent::new, 1024,
                DaemonThreadFactory.INSTANCE, ProducerType.MULTI, sleepingWaitStrategy);
        disruptor.handleEventsWith(new CostLogHandler(costCounter, costScripFetcher)).then(new LogRecordHandler(logRepos));
        disruptor.handleEventsWith(new MetricsLogHandler(metricsManager), new LimiterLogHandler(limiterManager));
        disruptor.setDefaultExceptionHandler(new LogExceptionHandler());
        disruptor.start();
        logDisruptor = disruptor;
        return disruptor.getRingBuffer();
    }

    @Bean
    public QueueClient queueClient(@Autowired OpenapiProperties openapiProperties) {
        return QueueClient.getInstance(openapiProperties.getHost());
    }

    @PreDestroy
    public void shutdownDisruptors() {
        if(logDisruptor != null) {
            logDisruptor.shutdown();
        }
        if(costCounter != null) {
            costCounter.flush();
        }
    }
}
