# Text Embeddings API Documentation

## Table of Contents

- [API Description](#api-description)
- [Request](#request)
  - [HTTP Request](#http-request)
  - [Request Body](#request-body)
- [Response](#response)
  - [Response Parameters](#response-parameters)
- [Error Codes](#error-codes)
- [Examples](#examples)
  - [Request Examples](#request-examples)
  - [Response Examples](#response-examples)

## API Description

Create embedding vectors that represent input text. Embedding vectors can be used for text similarity comparison, semantic search, and other applications.

## Request

### HTTP Request

```http
POST /v1/embeddings
```

### Request Body

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| model | string | Yes | The ID of the model to use |
| input | string/array | Yes | The input text to embed, encoded as a string or array of tokens. To embed multiple inputs in a single request, pass an array of strings or array of token arrays. Input must not exceed the model's maximum token count (specific value depends on model type), cannot be empty string, and any array dimension must be less than or equal to 2048 |
| dimensions | integer | No | The number of dimensions the resulting output embeddings should have. Only effective in supported models |
| encoding_format | string | No | The format to return the embeddings in. Can be `float` (default) or `base64` |
| user | string | No | A unique identifier representing the end-user |

#### Input Parameter Details

The input parameter supports the following types:

1. String: A string to be embedded
   ```json
   "input": "The food was delicious and the waiter..."
   ```

2. Array of strings: An array of strings to be embedded
   ```json
   "input": ["The food was delicious", "The service was excellent"]
   ```

3. Array of integers: An array of integers (token IDs) to be embedded
   ```json
   "input": [10231, 383, 9931, 290...]
   ```

4. Array of integer arrays: An array of arrays of integers to be embedded
   ```json
   "input": [[10231, 383, 9931], [293, 192, 331...]]
   ```

## Response

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "embedding": [
        0.0023064255,
        -0.009327292,
        ... (1536 floating point numbers for ada-002 model)
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

### Response Parameters

| Parameter | Type | Description |
| --- | --- | --- |
| object | string | Object type, typically "list" |
| data | array | Array of embedding data objects |
| model | string | Model used |
| usage | object | Token usage statistics |

#### EmbeddingData Object

| Parameter | Type | Description |
| --- | --- | --- |
| object | string | Object type, typically "embedding" |
| embedding | array | Embedding vector, represented as an array of floating point numbers |
| index | integer | Index of this embedding in the input array |

#### Usage Object

| Parameter | Type | Description |
| --- | --- | --- |
| prompt_tokens | integer | Number of tokens used in the prompt |
| total_tokens | integer | Total number of tokens used |

## Error Codes

| Error Code | Description |
| --- | --- |
| 400 | Bad request parameters |
| 401 | Authentication failed, invalid API key |
| 403 | Insufficient permissions, API key doesn't have permission to access requested resource |
| 404 | Requested resource not found |
| 429 | Too many requests, rate limit exceeded |
| 500 | Internal server error |
| 503 | Service temporarily unavailable |

## Examples

### Request Examples

#### Single Text Embedding

```json
{
  "input": "The food was delicious and the waiter was very friendly.",
  "model": "text-embedding-ada-002",
  "encoding_format": "float"
}
```

#### Multiple Text Embeddings

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

#### Embedding with Specified Dimensions

```json
{
  "input": "The food was delicious and the waiter was very friendly.",
  "model": "text-embedding-3-small",
  "dimensions": 256,
  "encoding_format": "float"
}
```

### Response Examples

#### Single Text Embedding Response

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "embedding": [
        0.0023064255,
        -0.009327292,
        ... (omitted vector values)
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

#### Multiple Text Embeddings Response

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "embedding": [
        0.0023064255,
        -0.009327292,
        ... (omitted vector values)
        -0.0028842222
      ],
      "index": 0
    },
    {
      "object": "embedding",
      "embedding": [
        0.0018349291,
        -0.007542568,
        ... (omitted vector values)
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

## Use Cases

Text embeddings can be used in various applications, including but not limited to:

1. **Semantic Search**: Convert queries and documents into embedding vectors and calculate their similarity
2. **Text Classification**: Use embedding vectors as features for machine learning models
3. **Clustering Analysis**: Cluster texts to discover similar text groups
4. **Recommendation Systems**: Content-based recommendations through calculating embedding similarity of item descriptions
5. **Anomaly Detection**: Identify texts that significantly differ from normal text patterns

## Best Practices

1. **Choose Appropriate Model**: Different models offer different trade-offs between performance and cost
2. **Batch Processing**: When embedding multiple texts, use array input instead of multiple API calls
3. **Cache Results**: Cache embedding results for unchanging texts to reduce API calls
4. **Normalization**: Consider normalizing vectors before comparison
5. **Dimension Selection**: For newer model versions, consider choosing lower dimensions to reduce storage requirements while maintaining good performance