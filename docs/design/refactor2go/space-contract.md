# Task 1 / Space 契约盘点

## 1. 范围说明

本文从总表中拆出 `Space` 能力点，覆盖 `/v1/space/*` 在 Go 重构前必须保持兼容的协议、不变量、回归基线与验证方式。

主索引文档：[`docs/design/refactor2go-task1-contract-inventory.md`](../refactor2go-task1-contract-inventory.md)

## 2. 协议

### 2.1 `POST /v1/space/create` 输入协议

#### 2.1.1 认证方式

- 这是 Bella 管理面接口

#### 2.1.2 Content-Type

- 请求默认是 `application/json`

#### 2.1.3 请求体结构

- 顶层是 JSON object
- 核心字段：
  - `spaceName`
  - `spaceDescription`
  - `spaceCode`
  - `ownerUid`
  - `ownerName`
- 顶层字段层次当前至少继续兼容：
  - `spaceName`
  - `spaceDescription`
  - `spaceCode`
  - `ownerUid`
  - `ownerName`

### 2.2 `POST /v1/space/create` 输出协议

#### 2.2.1 成功响应

- 成功响应继续统一走 BellaAPI envelope
- 典型响应仍应兼容：
  ```json
  { "code": 200, "timestamp": 1710000000000, "data": "space_x" }
  ```
- BellaAPI envelope 当前至少继续兼容：
  - `code`
  - `message`
  - `timestamp`
  - `data`
  - `stacktrace`
- `data` 当前继续返回新创建的 `spaceCode` 字符串

#### 2.2.2 错误响应

- 空间编码冲突或参数非法时继续返回 BellaAPI 统一错误体

### 2.3 `POST /v1/space/name/update` 输入协议

#### 2.3.1 认证方式

- 这是 Bella 管理面接口

#### 2.3.2 Content-Type

- 请求默认是 `application/json`

#### 2.3.3 请求体结构

- 顶层是 JSON object
- 核心字段：
  - `spaceCode`
  - `spaceName`
- 顶层字段层次当前至少继续兼容：
  - `spaceCode`
  - `spaceName`

### 2.4 `POST /v1/space/name/update` 输出协议

#### 2.4.1 成功响应

- 成功响应继续统一走 BellaAPI envelope
- 典型响应仍应兼容：
  ```json
  { "code": 200, "timestamp": 1710000000000, "data": true }
  ```

#### 2.4.2 错误响应

- 空间不存在或参数非法时继续返回 BellaAPI 统一错误体

### 2.5 `GET /v1/space/get` 输入协议

#### 2.5.1 认证方式

- 这是 Bella 管理面接口

#### 2.5.2 Query 参数

- 核心参数：
  - `spaceCode`

### 2.6 `GET /v1/space/get` 输出协议

#### 2.6.1 成功响应

- 成功响应继续统一走 BellaAPI envelope
- `data` 继续返回 `Space` 对象
- `Space` 核心字段：
  - `spaceCode`
  - `spaceName`
  - `ownerUid`
- `Space` 字段层次当前至少继续兼容：
  - `spaceCode`
  - `spaceName`
  - `ownerUid`

#### 2.6.2 错误响应

- 空间不存在时继续返回 BellaAPI 统一错误体

### 2.7 `GET /v1/space/list` 输入协议

#### 2.7.1 认证方式

- 这是 Bella 管理面接口

#### 2.7.2 Query 参数

- 核心参数：
  - `spaceCodes`
- `spaceCodes` 当前继续以 query list 形式传入，而不是 JSON body

### 2.8 `GET /v1/space/list` 输出协议

#### 2.8.1 成功响应

- 成功响应继续统一走 BellaAPI envelope
- `data` 继续返回 `Space[]`
- 单项核心字段：
  - `spaceCode`
  - `spaceName`
  - `ownerUid`
- `Space[]` 单项字段层次继续与 `GET /v1/space/get` 的 `Space` 对象保持一致

#### 2.8.2 错误响应

- 查询失败时继续返回 BellaAPI 统一错误体

### 2.9 `POST /v1/space/owner/change` 输入协议

#### 2.9.1 认证方式

- 这是 Bella 管理面接口

#### 2.9.2 Content-Type

- 请求默认是 `application/json`

#### 2.9.3 请求体结构

- 顶层是 JSON object
- 核心字段：
  - `spaceCode`
  - `ownerUid`
- 顶层字段层次当前至少继续兼容：
  - `spaceCode`
  - `ownerUid`

### 2.10 `POST /v1/space/owner/change` 输出协议

#### 2.10.1 成功响应

- 成功响应继续统一走 BellaAPI envelope
- 典型响应仍应兼容：
  ```json
  { "code": 200, "timestamp": 1710000000000, "data": true }
  ```

#### 2.10.2 错误响应

- 非法转让、空间不存在或新 owner 不满足条件时继续返回 BellaAPI 统一错误体

### 2.11 `POST /v1/space/role/create` 输入协议

#### 2.11.1 认证方式

- 这是 Bella 管理面接口

#### 2.11.2 Content-Type

- 请求默认是 `application/json`

#### 2.11.3 请求体结构

- 顶层是 JSON object
- 核心字段：
  - `spaceCode`
  - `roles`
- `roles[]` 核心字段：
  - `roleCode`
  - `roleName`
  - `roleDesc`
- 顶层字段层次当前至少继续兼容：
  - `spaceCode`
  - `roles[]`
- `roles[]` 单项字段层次当前至少继续兼容：
  - `roleCode`
  - `roleName`
  - `roleDesc`

### 2.12 `POST /v1/space/role/create` 输出协议

#### 2.12.1 成功响应

- 成功响应继续统一走 BellaAPI envelope
- 典型响应仍应兼容：
  ```json
  { "code": 200, "timestamp": 1710000000000, "data": true }
  ```

#### 2.12.2 错误响应

- 空间不存在或角色参数非法时继续返回 BellaAPI 统一错误体

### 2.13 `GET /v1/space/role/list` 输入协议

#### 2.13.1 认证方式

- 这是 Bella 管理面接口

#### 2.13.2 Query 参数

- 核心参数：
  - `memberUid`

### 2.14 `GET /v1/space/role/list` 输出协议

#### 2.14.1 成功响应

- 成功响应继续统一走 BellaAPI envelope
- `data` 继续返回 `RoleWithSpace[]`
- 单项核心字段：
  - `roleCode`
  - `spaceCode`
  - `spaceName`
- `RoleWithSpace` 字段层次当前至少继续兼容：
  - `roleCode`
  - `spaceCode`
  - `spaceName`

#### 2.14.2 错误响应

- 查询失败时继续返回 BellaAPI 统一错误体

### 2.15 `POST /v1/space/member/create` 输入协议

#### 2.15.1 认证方式

- 这是 Bella 管理面接口

#### 2.15.2 Content-Type

- 请求默认是 `application/json`

#### 2.15.3 请求体结构

- 顶层是 JSON object
- 核心字段：
  - `spaceCode`
  - `roleCode`
  - `members`
- `members[]` 核心字段：
  - `memberUid`
  - `memberName`
- 顶层字段层次当前至少继续兼容：
  - `spaceCode`
  - `roleCode`
  - `members[]`
- `members[]` 单项字段层次当前至少继续兼容：
  - `memberUid`
  - `memberName`

### 2.16 `POST /v1/space/member/create` 输出协议

#### 2.16.1 成功响应

- 成功响应继续统一走 BellaAPI envelope
- 典型响应仍应兼容：
  ```json
  { "code": 200, "timestamp": 1710000000000, "data": true }
  ```

#### 2.16.2 错误响应

- 重复成员、空间不存在或参数非法时继续返回 BellaAPI 统一错误体

### 2.17 `POST /v1/space/member/remove` 输入协议

#### 2.17.1 认证方式

- 这是 Bella 管理面接口

#### 2.17.2 Content-Type

- 请求默认是 `application/json`

#### 2.17.3 请求体结构

- 顶层是 JSON object
- 核心字段：
  - `memberUid`
  - `spaceCode`
- 顶层字段层次当前至少继续兼容：
  - `memberUid`
  - `spaceCode`

### 2.18 `POST /v1/space/member/remove` 输出协议

#### 2.18.1 成功响应

- 成功响应继续统一走 BellaAPI envelope
- 典型响应仍应兼容：
  ```json
  { "code": 200, "timestamp": 1710000000000, "data": true }
  ```

#### 2.18.2 错误响应

- 成员不存在或空间不存在时继续返回 BellaAPI 统一错误体

### 2.19 `POST /v1/space/member/update` 输入协议

#### 2.19.1 认证方式

- 这是 Bella 管理面接口

#### 2.19.2 Content-Type

- 请求默认是 `application/json`

#### 2.19.3 请求体结构

- 顶层是 JSON object
- 核心字段：
  - `memberUid`
  - `spaceCode`
  - `roleCode`
- 顶层字段层次当前至少继续兼容：
  - `memberUid`
  - `spaceCode`
  - `roleCode`

### 2.20 `POST /v1/space/member/update` 输出协议

#### 2.20.1 成功响应

- 成功响应继续统一走 BellaAPI envelope
- 典型响应仍应兼容：
  ```json
  { "code": 200, "timestamp": 1710000000000, "data": true }
  ```

#### 2.20.2 错误响应

- 成员不存在、角色非法或空间不存在时继续返回 BellaAPI 统一错误体

### 2.21 `POST /v1/space/member/exit` 输入协议

#### 2.21.1 认证方式

- 这是 Bella 管理面接口

#### 2.21.2 Content-Type

- 请求默认是 `application/json`

#### 2.21.3 请求体结构

- 顶层是 JSON object
- 核心字段：
  - `memberUid`
  - `spaceCode`
- 顶层字段层次当前至少继续兼容：
  - `memberUid`
  - `spaceCode`

### 2.22 `POST /v1/space/member/exit` 输出协议

#### 2.22.1 成功响应

- 成功响应继续统一走 BellaAPI envelope
- 典型响应仍应兼容：
  ```json
  { "code": 200, "timestamp": 1710000000000, "data": true }
  ```

#### 2.22.2 错误响应

- 成员不存在或空间不存在时继续返回 BellaAPI 统一错误体

### 2.23 `GET /v1/space/member/list` 输入协议

#### 2.23.1 认证方式

- 这是 Bella 管理面接口

#### 2.23.2 Query 参数

- 核心参数：
  - `spaceCode`

### 2.24 `GET /v1/space/member/list` 输出协议

#### 2.24.1 成功响应

- 成功响应继续统一走 BellaAPI envelope
- `data` 继续返回 `Member[]`
- 单项核心字段：
  - `spaceCode`
  - `roleCode`
  - `memberName`
  - `memberUid`
- `Member` 字段层次当前至少继续兼容：
  - `spaceCode`
  - `roleCode`
  - `memberName`
  - `memberUid`

#### 2.24.2 错误响应

- 查询失败时继续返回 BellaAPI 统一错误体

### 2.25 `GET /v1/space/member/role` 输入协议

#### 2.25.1 认证方式

- 这是 Bella 管理面接口

#### 2.25.2 Query 参数

- 核心参数：
  - `memberUid`
  - `spaceCode`

### 2.26 `GET /v1/space/member/role` 输出协议

#### 2.26.1 成功响应

- 成功响应继续统一走 BellaAPI envelope
- `data` 继续返回 `RoleWithSpace`
- 核心字段：
  - `roleCode`
  - `spaceCode`
  - `spaceName`
- 返回对象字段层次继续与 `GET /v1/space/role/list` 中的 `RoleWithSpace` 单项保持一致

#### 2.26.2 错误响应

- 成员不存在、空间不存在或查询失败时继续返回 BellaAPI 统一错误体

## 3. 不能改变的行为

- **空间创建规则**
  - create 在未显式提供 `spaceCode` 时，当前继续自动生成一个新 code；若调用方提供的 `spaceCode` 已存在，继续直接报错
- **成员与角色规则**
  - create member 当前继续拒绝重复添加同一成员
  - remove / exit 当前继续要求目标成员已经存在于该空间中；不存在时继续报错，而不是静默成功
  - role create / member update 当前继续把角色变更视为显式写操作，不做隐式 owner/admin 推导
- **空间转让规则**
  - change owner 当前继续只允许现有 owner 发起
  - 新 owner 当前继续必须已经在该空间内；不满足时继续报错，而不是自动补成员
  - 转让成功后，原 owner 继续降为 `admin`，新 owner 继续被设置为 `owner`
- **个人空间语义**
  - `role/list` 当前继续默认包含一条“个人空间”记录，`spaceCode=memberUid`，角色为 `owner`
  - `member/role` 在 `memberUid == spaceCode` 时，当前继续直接返回“个人空间 owner”语义，而不是再查成员表

## 4. 回归验证基线

- space 创建、查询、成员、角色与 owner change 的 BellaAPI envelope 保持一致
- 成员存在性、重复校验、owner change 与个人空间语义保持一致

## 5. 验证方式

- 回放 create/get/list/member/role/change-owner 等正常与异常样例
- 对比 Java 与 Go 的 envelope、角色变化、个人空间返回与错误边界
