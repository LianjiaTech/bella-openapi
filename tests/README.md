# Playwright Tests

本目录存放 Bella OpenAPI 的 Playwright E2E 测试。

## 登录模式

测试支持两种登录黄金路径，都会生成同一个认证状态文件：
- `apikey`：使用 `system-apikey.txt` 中的系统密钥登录
- `cas`：通过 CAS 登录页输入测试账号密码登录

认证状态统一保存到：
- `tests/.auth/user.json`

业务测试只复用这个文件，不关心具体登录方式。

## 环境变量

### 通用
- `BASE_URL`：测试目标地址，默认 Docker 模式下为 `http://localhost`
- `AUTH_MODE`：登录模式，可选 `apikey` 或 `cas`，默认 `apikey`

### API Key 模式
- 依赖项目根目录的 `system-apikey.txt`

### CAS 模式
- `CAS_USERNAME`：CAS 测试账号
- `CAS_PASSWORD`：CAS 测试密码
- `CAS_SUCCESS_URL`：可选，登录成功后用于精确校验的回跳地址

## 运行方式

### 本地运行
- 默认 API Key 登录：`npx playwright test`
- CAS 登录：`AUTH_MODE=cas CAS_USERNAME=<user> CAS_PASSWORD=<password> npx playwright test`

### Docker 运行
- 默认 API Key 登录：`make test-docker`
- CAS 登录：`AUTH_MODE=cas CAS_USERNAME=<user> CAS_PASSWORD=<password> make test-docker`
- 便捷命令：`CAS_USERNAME=<user> CAS_PASSWORD=<password> make test-docker-cas`

## 调试登录初始化

只跑 API Key setup：
- `npx playwright test tests/login-flow/api-key-login.spec.ts`

只跑 CAS setup：
- `AUTH_MODE=cas CAS_USERNAME=<user> CAS_PASSWORD=<password> npx playwright test tests/login-flow/cas-login.spec.ts`

## 常见问题

- `AUTH_MODE=cas` 但未提供 `CAS_USERNAME` 或 `CAS_PASSWORD`：Docker 脚本会直接失败。
- CAS 登录成功但未回到业务页：检查 `CAS_SUCCESS_URL` 是否与实际回跳一致。
- 业务用例复用旧登录态失败：删除 `tests/.auth/user.json` 后重新执行 setup。
