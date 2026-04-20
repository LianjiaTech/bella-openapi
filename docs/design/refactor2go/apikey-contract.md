# Task 1 / Apikey 契约盘点

## 1. 范围说明

本文从总表中拆出 `Apikey` 能力点，覆盖查询类接口在 Go 重构前必须保持兼容的协议、不变量、回归基线与验证方式。

主索引文档：[`docs/design/refactor2go-task1-contract-inventory.md`](../refactor2go-task1-contract-inventory.md)

## 2. 协议

### 2.1 `GET /v1/apikey/whoami` 输入协议

#### 2.1.1 认证方式

- 这是 Bella 管理面接口，继续走 BellaAPI / console 管理面鉴权语义

#### 2.1.2 Query 参数

- 当前无必填业务 query 参数

#### 2.1.3 请求语义

- 请求继续基于当前实际生效身份返回调用方视图
- 若前置链路已发生 delegated auth，查询身份继续以切换后的 AK 视图为准

### 2.2 `GET /v1/apikey/whoami` 输出协议

#### 2.2.1 Content-Type

- 响应默认是 `application/json;charset=UTF-8`

#### 2.2.2 成功响应

- 成功响应继续统一走 BellaAPI envelope
- `data` 当前继续返回 `ApikeyInfo` 视图对象，而不是字符串或简化对象
- 典型响应仍应兼容：
  ```json
  {
    "code": 200,
    "timestamp": 1710000000000,
    "data": {
      "apikey": "sk-xxx",
      "code": "ak_x",
      "serviceId": "bella",
      "akSha": "sha_x",
      "akDisplay": "sk-***",
      "name": "demo",
      "ownerType": "user",
      "ownerCode": "u_x",
      "ownerName": "Alice",
      "roleCode": "all",
      "safetySceneCode": "default",
      "safetyLevel": 1,
      "monthQuota": 1000,
      "rolePath": {
        "included": ["/v1/chat/completions"],
        "excluded": []
      },
      "status": "ACTIVE",
      "remark": "",
      "qpsLimit": 10,
      "managerCode": "u_mgr",
      "managerName": "Bob"
    }
  }
  ```
- BellaAPI envelope 当前至少继续兼容：
  - `code`
  - `message`
  - `timestamp`
  - `data`
  - `stacktrace`
- 其中：
  - `code`：成功时固定为 `200`
  - `message`：成功时通常为空
  - `timestamp`：毫秒级时间戳
  - `stacktrace`：成功时通常为空，仅服务端异常时才出现
- `ApikeyInfo` 当前至少继续兼容这些字段：
  - `apikey`
  - `code`
  - `serviceId`
  - `akSha`
  - `akDisplay`
  - `name`
  - `outEntityCode`
  - `parentCode`
  - `ownerType`
  - `ownerCode`
  - `ownerName`
  - `roleCode`
  - `safetySceneCode`
  - `safetyLevel`
  - `monthQuota`
  - `rolePath`
  - `status`
  - `remark`
  - `userId`
  - `parentInfo`
  - `qpsLimit`
  - `managerCode`
  - `managerName`
- `rolePath` 当前至少继续兼容：
  - `included`
  - `excluded`

#### 2.2.3 错误响应

- 鉴权缺失、身份无效或上下文装载失败时，继续返回 BellaAPI 统一错误体
- 错误响应 envelope 当前至少继续兼容：
  - `code`
  - `message`
  - `timestamp`
  - `data`
  - `stacktrace`
- 其中：
  - `code`：当前至少继续兼容 `400`、`500` 等 HTTP 语义码
  - `message`：错误说明
  - `data`：错误时通常为空
  - `stacktrace`：仅 `500` 场景继续可能返回

## 3. 不能改变的行为

- **查询语义**
  - `whoami` 继续返回当前实际生效的调用身份；若存在 delegated auth，这里返回的也应是切换后的身份视图

## 4. 回归验证基线

- whoami 的 BellaAPI envelope 保持一致
- `whoami` 与 delegated auth 的身份视图保持一致

## 5. 验证方式

- 回放 whoami 样例
- 对比 Java 与 Go 的 envelope、身份视图与错误响应
