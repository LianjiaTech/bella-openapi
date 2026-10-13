package com.ke.bella.openapi.endpoints;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.BellaAPI;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.db.log.RocksDBLogReader;
import com.ke.bella.openapi.db.log.RocksDBLogRepo;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.service.ApikeyService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@BellaAPI
@RestController
@RequestMapping("/v1/log")
@Tag(name = "日志")
@Slf4j
public class LogController {
    @Autowired
    private EndpointLogger logger;
    @Autowired
    private ApikeyService apikeyService;
    @Autowired(required = false)
    private RocksDBLogReader rocksDBLogReader;
    @Autowired(required = false)
    private RocksDBLogRepo rocksDBLogRepo;

    @PostMapping
    public Boolean record(@RequestBody EndpointProcessData processData) {
        Assert.hasText(processData.getEndpoint(), "endpoint can not be null");
        Assert.hasText(processData.getAkSha(), "akSha sha can not be null");
        Assert.hasText(processData.getBellaTraceId(), "bella trace id can not be null");
        ApikeyInfo apikeyInfo = apikeyService.queryBySha(processData.getAkSha(), true);
        if(apikeyInfo == null) {
            log.warn("用户的Apikey不存在, akSha={}, bellaTraceId={}, endpoint={}",
                    processData.getAkSha(), processData.getBellaTraceId(), processData.getEndpoint());
            throw new BizParamCheckException("用户的Apikey不存在");
        }
        processData.setApikeyInfo(apikeyInfo);
        processData.setInnerLog(false);
        logger.log(processData);
        return true;
    }

    @GetMapping("/detail")
    public ResponseEntity<Map<String, Object>> detail(
            @RequestParam String requestId,
            @RequestParam String shardPath) {
        if (rocksDBLogReader == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> data = rocksDBLogReader.readByShardPath(requestId, shardPath);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }

    @PostMapping("/benchmark")
    public ResponseEntity<BenchmarkResult> benchmark(
            @RequestParam(defaultValue = "10000") int count,
            @RequestParam(defaultValue = "10240") int valueSize) {
        if (rocksDBLogRepo == null) {
            return ResponseEntity.notFound().build();
        }
        if (count > 100000) {
            count = 100000;
        }

        String[] keys = new String[count];
        byte[] payload = new byte[valueSize];
        ThreadLocalRandom.current().nextBytes(payload);
        String valueTemplate = new String(payload);

        long writeStart = System.nanoTime();
        for (int i = 0; i < count; i++) {
            String key = UUID.randomUUID().toString();
            keys[i] = key;
            EndpointProcessData testData = new EndpointProcessData();
            testData.setRequestId("__bench_" + key);
            testData.setRequest(valueTemplate);
            rocksDBLogRepo.record(testData);
        }
        long writeTotalNs = System.nanoTime() - writeStart;
        long writeTotalMs = writeTotalNs / 1_000_000;

        rocksDBLogRepo.flush();

        int readCount = Math.min(100, count);
        long readStart = System.nanoTime();
        long maxReadNs = 0;
        String currentShardPath = rocksDBLogRepo.getCurrentShardPath();
        for (int i = 0; i < readCount; i++) {
            int idx = ThreadLocalRandom.current().nextInt(count);
            long t0 = System.nanoTime();
            if (currentShardPath != null) {
                rocksDBLogReader.readByShardPath("__bench_" + keys[idx], currentShardPath);
            }
            long elapsed = System.nanoTime() - t0;
            if (elapsed > maxReadNs) maxReadNs = elapsed;
        }
        long readTotalNs = System.nanoTime() - readStart;

        BenchmarkResult result = new BenchmarkResult(
                count,
                writeTotalMs,
                writeTotalMs > 0 ? (count * 1000L / writeTotalMs) : count,
                (writeTotalNs / count) / 1000,
                readCount,
                readTotalNs / 1_000_000,
                (readTotalNs / readCount) / 1000,
                maxReadNs / 1000,
                valueSize,
                properties()
        );
        return ResponseEntity.ok(result);
    }

    private String properties() {
        return rocksDBLogRepo != null ? rocksDBLogRepo.getCurrentShardPath() : "N/A";
    }

    @Data
    @AllArgsConstructor
    public static class BenchmarkResult {
        private int writeCount;
        private long writeTotalMs;
        private long writeOpsPerSec;
        private long writeAvgLatencyUs;
        private int readCount;
        private long readTotalMs;
        private long readAvgLatencyUs;
        private long readP99LatencyUs;
        private int valueSizeBytes;
        private String storageBackend;
    }
}
