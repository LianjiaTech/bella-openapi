# Execution Plan: Fix CacheTest Hardcoded AK (#824)

## 目标

将 `CacheTest.java` 中硬编码的 API Key (`ak-0d0b8f35-4dcb-4019-8f05-a2a52f438cef`) 替换为明确的测试占位值，消除 gitleaks 敏感信息扫描告警。

## 非目标

- 不修改测试逻辑或行为
- 不重构 CacheTest 的整体结构
- 不涉及其他文件或业务代码修改
- 不添加外部配置文件加载逻辑（改动应保持最小化）

## 验收标准

1. `CacheTest.java` 中不再包含真实或疑似真实 AK 格式的字符串
2. 替换后的占位值格式清晰表明为测试用途（如 `ak-test-placeholder-for-cache-deletion`）
3. gitleaks 扫描不再对该文件产生告警
4. 代码可正常编译（`mvn clean compile`）

## 约束

- 仅修改 `api/server/src/test/java/com/ke/bella/openapi/CacheTest.java` 单个文件
- 占位值需保留 `ak-` 前缀以保证测试逻辑中格式一致性（如有格式校验）
- 不引入新依赖或配置文件

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/server/src/test/java/com/ke/bella/openapi/CacheTest.java` | 修改 | 替换第60行硬编码 AK |

## 实现思路

### Step 1: 替换硬编码 AK

- **文件**: `api/server/src/test/java/com/ke/bella/openapi/CacheTest.java`
- **位置**: 第60行
- **当前值**: `"ak-0d0b8f35-4dcb-4019-8f05-a2a52f438cef"`
- **替换为**: `"ak-test-placeholder-for-cache-deletion"`
- **原因**: 新值明确标识为测试占位符，不会匹配真实 AK 的 UUID 格式，从而避免 gitleaks 告警

### Step 2: 验证编译通过

- 执行 `mvn clean compile` 确认无编译错误
- 该方法 `deleteCache()` 为工具方法（无 `@Test` 注解），不会被自动执行，替换占位值不影响 CI

## 风险与依赖

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| 占位值仍触发 gitleaks | 低 | 低 | 占位值不含 UUID 格式，不匹配常见 secret pattern |
| 测试方法在本地手动执行时行为变化 | 低 | 低 | 该方法注释已说明"替换为真实 ak code"，属于手动调试用途，占位值不改变此使用模式 |

**依赖**: 无外部依赖

## 验证方式

1. `mvn clean compile` 编译通过
2. 使用 gitleaks 或 `grep -rn "ak-[0-9a-f\-]\{36\}"` 确认文件中不再包含 UUID 格式的 AK
3. 代码审查确认改动范围仅限预期
