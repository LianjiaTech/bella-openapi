# Execution Plan: HuoShanV3 TTS speed 参数转换 (#862)

## 目标

在 `HuoShanV3Request.from(...)` 中补充 OpenAI 兼容参数 `speed` 到火山 V3 `audio_params.speech_rate` 的转换逻辑，使 `Doubao_Seed_TTS_2` 等使用 HuoShanV3Tts 协议的模型能通过顶层 `speed` 参数控制语速。

## 非目标

- 不修改其他 TTS 协议适配器（HuoShanRequest 等）
- 不修改 `TtsRequest` DTO 结构
- 不修改前端代码
- 不新增 API 接口
- 不修改 `extra_body` 透传逻辑的现有行为

## 验收标准

1. `speed: 1.5` 时，下游请求包含 `audio_params.speech_rate = 50`
2. `speed: 0.5` 时，下游请求包含 `audio_params.speech_rate = -50`
3. `speed: 2.0` 时，下游请求包含 `audio_params.speech_rate = 100`
4. 不传 `speed` 或 `speed: 1.0` 时，不设置 `speech_rate`（保持默认行为）
5. `extra_body.audio_params.speech_rate` 透传能力不受影响（透传值覆盖 speed 转换值）
6. `speech_rate` 被 clamp 到 [-50, 100] 区间

## 约束

- Java 8 兼容
- 映射公式：`speechRate = (int) Math.round((speed - 1.0) * 100)`，clamp 到 [-50, 100]
- 当 `speed == null` 或 `speed == 1.0` 时不设置 `speech_rate`，避免向下游发送无意义参数
- `extra_body.audio_params` 中的透传字段优先级高于 `speed` 转换值（现有 extra_body 处理在 speed 之后执行，会覆盖）

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `api/sdk/src/main/java/com/ke/bella/openapi/protocol/tts/HuoShanV3Request.java` | 修改 | 在 `from()` 方法中增加 `speed` → `speech_rate` 转换逻辑 |

## 实现思路

### Step 1: 在 `HuoShanV3Request.from()` 中增加 speed 转换

**文件**: `api/sdk/src/main/java/com/ke/bella/openapi/protocol/tts/HuoShanV3Request.java`

**操作**:

在 `AudioParams` 构建完成后、`extra_body["audio_params"]` 透传处理之前，插入以下逻辑：

```java
// Convert OpenAI-compatible speed to HuoShan V3 speech_rate
Double speed = ttsRequest.getSpeed();
if (speed != null && speed != 1.0) {
    int speechRate = (int) Math.round((speed - 1.0) * 100);
    speechRate = Math.max(-50, Math.min(100, speechRate));
    audioParams.setSpeechRate(speechRate);
}
```

**插入位置**: 在第 97 行（`.build()` 完成）之后，第 99 行（`extra_body["audio_params"]` 处理）之前。

**关键设计决策**:
- 先设置 `speechRate`，后处理 `extra_body`，确保透传参数优先级更高
- `speed == 1.0` 时不设置，避免覆盖模型默认语速
- 使用 `Double` 的 `!=` 比较（非 `equals`）可能存在精度问题，需使用 `Double.compare(speed, 1.0) != 0`

**最终代码**:

```java
Double speed = ttsRequest.getSpeed();
if (speed != null && Double.compare(speed, 1.0) != 0) {
    int speechRate = (int) Math.round((speed - 1.0) * 100);
    speechRate = Math.max(-50, Math.min(100, speechRate));
    audioParams.setSpeechRate(speechRate);
}
```

**验证**:
- `cd api && mvn clean compile` 编译通过
- 手动审查：确认代码逻辑与映射表一致

### Step 2: 验证 extra_body 优先级

**无需代码修改**，仅需确认逻辑正确性：

当前 `from()` 方法中 `extra_body["audio_params"]` 的透传处理（第 99-109 行）通过 `setAdditionalProperty` 设置额外属性。如果用户同时传了 `speed` 和 `extra_body.audio_params.speech_rate`：
- `speech_rate` 由 Step 1 设置到 `audioParams.speechRate` 字段
- `extra_body.audio_params.speech_rate` 由透传逻辑设置到 `additionalProperties` map 中

这两者在序列化时都会出现在 JSON 中。但由于 `@JsonProperty("speech_rate")` 字段和 `@JsonAnyGetter` 的 `additionalProperties` 可能冲突，需要确认：如果 `additionalProperties` 中包含 `speech_rate` key，Jackson 序列化时哪个优先。

**分析结果**: Jackson 在序列化时，显式字段（`@JsonProperty`）优先于 `@JsonAnyGetter` 中的同名 key。因此为确保 `extra_body` 透传优先，应在 extra_body 透传处理 `speech_rate` key 时覆盖 `audioParams.speechRate` 字段。

**修正方案**: 在 extra_body 透传循环中，如果遇到 `speech_rate` key，直接设置 `audioParams.setSpeechRate()`，而非放入 `additionalProperties`：

```java
if (ttsRequest.getExtra_body() != null && ttsRequest.getExtra_body().containsKey("audio_params")) {
    Object audioObj = ttsRequest.getExtra_body().get("audio_params");
    if (audioObj instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> audioMap = (Map<String, Object>) audioObj;
        for (Map.Entry<String, Object> entry : audioMap.entrySet()) {
            if ("speech_rate".equals(entry.getKey()) && entry.getValue() instanceof Number) {
                audioParams.setSpeechRate(((Number) entry.getValue()).intValue());
            } else {
                audioParams.setAdditionalProperty(entry.getKey(), entry.getValue());
            }
        }
    }
}
```

但这改变了现有 extra_body 透传的通用性。考虑到 Issue 验收标准要求"extra_body.audio_params.speech_rate 透传能力不受影响"，而当前透传本身就能正常工作（放入 additionalProperties 后序列化为 JSON 字段），**实际上不需要修改透传逻辑**。

**最终结论**: 由于 `@JsonInclude(NON_NULL)` + `speechRate` 字段为 `Integer` 类型：
- `speed` 转换设置 `audioParams.speechRate` → 序列化为 `"speech_rate": N`
- `extra_body` 透传如果也含 `speech_rate` → 放入 `additionalProperties` → `@JsonAnyGetter` 也输出 `"speech_rate": M`
- Jackson 不会产生重复 key（显式字段优先），所以 extra_body 的 `speech_rate` 如果放入 additionalProperties 实际上会被忽略

为确保 extra_body 透传的 `speech_rate` 能正确覆盖 speed 转换值，**应将 extra_body 中的 `speech_rate` 特殊处理为设置到字段**。因此最终实现为：

在 extra_body["audio_params"] 的遍历循环中，对 `speech_rate` key 做特殊处理：

```java
for (Map.Entry<String, Object> entry : audioMap.entrySet()) {
    if ("speech_rate".equals(entry.getKey()) && entry.getValue() instanceof Number) {
        audioParams.setSpeechRate(((Number) entry.getValue()).intValue());
    } else {
        audioParams.setAdditionalProperty(entry.getKey(), entry.getValue());
    }
}
```

**验证**: 编译通过；单元测试覆盖同时传 `speed` 和 `extra_body.audio_params.speech_rate` 的场景。

### Step 3: 编写单元测试

**文件**: `api/sdk/src/test/java/com/ke/bella/openapi/protocol/tts/HuoShanV3RequestTest.java`（新建）

**测试用例**:

1. `testSpeedConversion_1_5` — speed=1.5 → speechRate=50
2. `testSpeedConversion_0_5` — speed=0.5 → speechRate=-50
3. `testSpeedConversion_2_0` — speed=2.0 → speechRate=100
4. `testSpeedConversion_null` — speed=null → speechRate 不设置（null）
5. `testSpeedConversion_1_0` — speed=1.0 → speechRate 不设置（null）
6. `testSpeedConversion_clamp_upper` — speed=3.0 → speechRate=100（clamp 上限）
7. `testSpeedConversion_clamp_lower` — speed=0.0 → speechRate=-50（clamp 下限）
8. `testExtraBodySpeechRateOverridesSpeed` — speed=1.5 + extra_body.audio_params.speech_rate=80 → speechRate=80
9. `testExtraBodyOtherParamsPreserved` — extra_body.audio_params 中的其他字段仍正常透传

**验证**: `cd api && mvn test -pl sdk -Dtest=HuoShanV3RequestTest` 全部通过

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| `Double.compare(speed, 1.0)` 对浮点精度的处理 | 用户传入 `0.9999999` 可能意外触发 | 接受此行为，与其他 TTS 适配器保持一致 |
| `extra_body.audio_params.speech_rate` 透传与字段赋值冲突 | 透传 speech_rate 可能被忽略 | Step 2 中对 speech_rate key 做特殊处理，确保覆盖 |
| 火山 V3 API 对 speech_rate 范围的约束可能变化 | 超出范围的值被下游拒绝 | clamp 到文档规定的 [-50, 100]，如有变化可配置化 |

**依赖**: 无新增外部依赖。

## 验证方式

1. **编译验证**: `cd api && mvn clean compile` 通过
2. **单元测试**: `cd api && mvn test -pl sdk -Dtest=HuoShanV3RequestTest` 通过
3. **集成验证**: 本地启动服务，使用以下请求验证：
   ```bash
   curl -X POST /v1/audio/speech \
     -H "Content-Type: application/json" \
     -d '{"model":"Doubao_Seed_TTS_2","input":"测试语速","voice":"xxx","speed":1.5}'
   ```
   检查下游请求日志中 `audio_params.speech_rate = 50`
4. **回归验证**: 不传 `speed` 时行为不变，无额外参数下发
