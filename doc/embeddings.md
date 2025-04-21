# 文本向量接口文档

## 目录

- [接口描述](#接口描述)
- [请求](#请求)
  - [HTTP 请求](#http-请求)
  - [请求体](#请求体)
- [响应](#响应)
  - [响应参数](#响应参数)
- [错误码](#错误码)
- [示例](#示例)
  - [请求示例](#请求示例)
  - [响应示例](#响应示例)

## 接口描述

创建表示输入文本的嵌入向量。嵌入向量可用于文本相似度比较和语义搜索等应用场景。

## 请求

### HTTP 请求

```http
POST /v1/embeddings
```

### 请求体

| 参数 | 类型 | 必填 | 描述 |
| --- | --- | --- | --- |
| model | string | 是 | 要使用的模型 ID |
| input | string/array | 是 | 要嵌入的输入文本，编码为字符串或标记数组。要在单个请求中嵌入多个输入，请传递字符串数组或标记数组的数组。输入不得超过模型的最大输入标记数（具体数值需要根据模型类型确定），不能为空字符串，任何数组的维度必须小于等于 2048 |
| dimensions | integer | 否 | 结果输出嵌入应具有的维度数。仅在支持的模型中生效 |
| encoding_format | string | 否 | 返回嵌入的格式。可以是 `float`（默认）或 `base64` |
| user | string | 否 | 表示最终用户的唯一标识符 |

#### input 参数说明

input 参数支持以下几种类型：

1. 字符串：将被转换为嵌入的字符串
   ```json
   "input": "The food was delicious and the waiter..."
   ```

2. 字符串数组：将被转换为嵌入的字符串数组
   ```json
   "input": ["The food was delicious", "The service was excellent"]
   ```

3. 整数数组：将被转换为嵌入的整数数组（标记 ID）
   ```json
   "input": [10231, 383, 9931, 290...]
   ```

4. 整数数组的数组：将被转换为嵌入的整数数组的数组
   ```json
   "input": [[10231, 383, 9931], [293, 192, 331...]]
   ```

## 响应

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "embedding": [
        0.0023064255,
        -0.009327292,
        ... (1536 个浮点数，适用于 ada-002 模型)
        -0.0028842222
      ],
      "index": 0
    }
  ],
  "model": "text-embedding-ada-002",
  "usage": {
    "prompt_tokens": 8,
    "total_tokens": 8
  }
}
```

### 响应参数

| 参数 | 类型 | 描述 |
| --- | --- | --- |
| object | string | 对象类型，通常为 "list" |
| data | array | 嵌入数据对象的数组 |
| model | string | 使用的模型 |
| usage | object | 令牌使用统计 |

#### EmbeddingData 对象

| 参数 | 类型 | 描述 |
| --- | --- | --- |
| object | string | 对象类型，通常为 "embedding" |
| embedding | array | 嵌入向量，表示为浮点数数组 |
| index | integer | 嵌入在输入数组中的索引 |

#### Usage 对象

| 参数 | 类型 | 描述 |
| --- | --- | --- |
| prompt_tokens | integer | 提示使用的令牌数 |
| total_tokens | integer | 总共使用的令牌数 |

## 错误码

| 错误码 | 描述 |
| --- | --- |
| 400 | 请求参数错误 |
| 401 | 认证失败，无效的 API 密钥 |
| 403 | 权限不足，API 密钥没有权限访问请求的资源 |
| 404 | 请求的资源不存在 |
| 429 | 请求过多，超出速率限制 |
| 500 | 服务器内部错误 |
| 503 | 服务暂时不可用 |

## 示例

### 请求示例

#### 单个文本嵌入

```json
{
  "input": "The food was delicious and the waiter was very friendly.",
  "model": "text-embedding-ada-002",
  "encoding_format": "float"
}
```

#### 多个文本嵌入

```json
{
  "input": [
    "The food was delicious and the waiter was very friendly.",
    "The restaurant ambiance was elegant and sophisticated."
  ],
  "model": "text-embedding-ada-002",
  "encoding_format": "float"
}
```

#### 指定维度的嵌入

```json
{
  "input": "The food was delicious and the waiter was very friendly.",
  "model": "text-embedding-3-small",
  "dimensions": 256,
  "encoding_format": "float"
}
```

### 响应示例

#### 单个文本嵌入响应

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "embedding": [
        0.0023064255,
        -0.009327292,
        ... (省略部分向量值)
        -0.0028842222
      ],
      "index": 0
    }
  ],
  "model": "text-embedding-ada-002",
  "usage": {
    "prompt_tokens": 10,
    "total_tokens": 10
  }
}
```

#### 多个文本嵌入响应

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "embedding": [
        0.0023064255,
        -0.009327292,
        ... (省略部分向量值)
        -0.0028842222
      ],
      "index": 0
    },
    {
      "object": "embedding",
      "embedding": [
        0.0018349291,
        -0.007542568,
        ... (省略部分向量值)
        -0.0031582705
      ],
      "index": 1
    }
  ],
  "model": "text-embedding-ada-002",
  "usage": {
    "prompt_tokens": 18,
    "total_tokens": 18
  }
}
```

## 应用场景

文本嵌入可用于多种应用场景，包括但不限于：

1. **语义搜索**：将查询和文档转换为嵌入向量，然后计算它们之间的相似度
2. **文本分类**：使用嵌入向量作为机器学习模型的特征
3. **聚类分析**：对文本进行聚类，发现相似的文本组
4. **推荐系统**：基于内容的推荐，通过计算项目描述的嵌入相似度
5. **异常检测**：识别与正常文本模式显著不同的文本

## 最佳实践

1. **选择合适的模型**：不同的模型在性能和成本之间有不同的权衡
2. **批量处理**：当需要嵌入多个文本时，使用数组输入而不是多次调用 API
3. **缓存结果**：对于不变的文本，缓存嵌入结果以减少 API 调用
4. **规范化**：在比较嵌入之前，考虑对向量进行规范化
5. **维度选择**：对于高版本的模型，可以选择较低的维度以减少存储需求，同时保持良好的性能