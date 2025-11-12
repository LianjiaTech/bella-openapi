package com.ke.bella.openapi.console;

import com.ke.bella.openapi.annotations.BellaAPI;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.service.ApikeyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@BellaAPI
@RestController
@RequestMapping("/console/limiter/qps")
@Tag(name = "QPS限流管理")
public class LimiterConsoleController {
    @Autowired
    private LimiterManager limiterManager;

    @Autowired
    private ApikeyService apikeyService;

    @GetMapping("/ranking")
    public List<Map<String, Object>> ranking(@RequestParam(name = "top", required = false, defaultValue = "20") Integer top) {
        return limiterManager.listTopAkByQps(top != null ? top : 20);
    }

    @GetMapping("/{akCode}")
    public Map<String, Object> detail(@PathVariable("akCode") String akCode) {
        Assert.hasText(akCode, "akCode不可为空");
        Long rpm = limiterManager.getGlobalRequestCountPerMinute(akCode);
        Long limitQps = limiterManager.getGlobalQpsLimit(akCode);
        Map<String, Object> result = new HashMap<>();
        result.put("akCode", akCode);
        result.put("currentRpm", rpm);
        result.put("currentQps", rpm != null ? rpm / 60.0 : 0);
        result.put("limitQps", limitQps);
        return result;
    }

    @Setter
	@Getter
	public static class QpsLimitOp {
        private String akCode;
        private Long qpsLimit;

	}

    @PostMapping("/limit")
    public Boolean updateLimit(@RequestBody QpsLimitOp op) {
        Assert.hasText(op.getAkCode(), "akCode不可为空");
        Assert.notNull(op.getQpsLimit(), "qpsLimit不可为空");
        Assert.isTrue(op.getQpsLimit() > 0, "qpsLimit应大于0");
        limiterManager.updateGlobalQpsLimit(op.getAkCode(), op.getQpsLimit());
        return true;
    }

    // ========== QPS 配置管理接口（独立于监控） ==========

    /**
     * 查询指定 API Key 的 QPS 配置和当前状态
     * 不依赖 Redis 扫描，直接查询
     */
    @GetMapping("/config/{akCode}")
    public Map<String, Object> getQpsConfig(@PathVariable("akCode") String akCode) {
        Assert.hasText(akCode, "akCode不可为空");

        ApikeyInfo apikeyInfo = apikeyService.queryByCode(akCode, false);
        if (apikeyInfo == null) {
            throw new IllegalArgumentException("API Key 不存在: " + akCode);
        }

        Long rpm = limiterManager.getGlobalRequestCountPerMinute(akCode);
        Long limitQps = limiterManager.getGlobalQpsLimit(akCode);
        double currentQps = rpm != null ? rpm / 60.0 : 0.0;

        Map<String, Object> result = new HashMap<>();
        result.put("akCode", akCode);
        result.put("currentRpm", rpm != null ? rpm : 0L);
        result.put("currentQps", currentQps);
        result.put("limitQps", limitQps);
        result.put("hasTraffic", rpm != null && rpm > 0);

        return result;
    }

    /**
     * 更新 QPS 配置（独立接口）
     */
    @PostMapping("/config")
    public Boolean updateQpsConfig(@RequestBody QpsLimitOp op) {
        Assert.hasText(op.getAkCode(), "akCode不可为空");
        Assert.notNull(op.getQpsLimit(), "qpsLimit不可为空");
        Assert.isTrue(op.getQpsLimit() > 0, "qpsLimit应大于0");

        ApikeyInfo apikeyInfo = apikeyService.queryByCode(op.getAkCode(), false);
        if (apikeyInfo == null) {
            throw new IllegalArgumentException("API Key 不存在: " + op.getAkCode());
        }

        limiterManager.updateGlobalQpsLimit(op.getAkCode(), op.getQpsLimit());
        return true;
    }
}

