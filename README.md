# Bella OpenAPI

[![Static Badge](https://img.shields.io/badge/docs-more-yellow?style=flat-square)](https://doc.bella.top/)
[![Static Badge](https://img.shields.io/badge/wiki-deep-blue?style=flat-square)](https://deepwiki.com/LianjiaTech/bella-openapi)

中文 | [English](./README_EN.md) | [了解更多](https://doc.bella.top/) | [加入社区贡献](./contributor-guide.md)



Bella OpenAPI是一个提供了丰富的AI调用能力的API网关，可类比openrouter，与之不同的是除了提供聊天补全(chat-completion)能力外，还提供了文本向量化(text-embedding)、语音识别(ASR)、语音合成(TTS)、文生图、图生图等多种AI能力，同时集成了计费、限流和资源管理功能。且集成的所有能力都经过了大规模生产环境的验证。

- 我们部署了线上体验版的Bella OpenAPI，您可以登录访问[Bella OpenAPI](https://api.bella.top)
- 如需快速启动并体验服务，请阅读：[快速启动](#快速启动)
- 想了解详细的环境变量配置和启动部署细节，请阅读：[配置详情](https://doc.bella.top/docs/bella-openapi/configuration-details) 和 [启动与部署详情](https://doc.bella.top/docs/bella-openapi/startup-deployment-details)

## 目录

- [核心功能](#核心功能)
  - [AI能力点](#AI能力点)
  - [元数据管理](#元数据管理)
  - [统一登录服务](#统一登录服务)
  - [计费与限流](#计费与限流)
- [系统优势](#系统优势)
  - [大规模的生产环境验证](#大规模的生产环境验证)
  - [丰富的能力](#丰富的能力)
  - [优秀的特性](#优秀的特性)
  - [统一的元数据管理](#统一的元数据管理)
  - [高性能缓存设计](#高性能缓存设计)
  - [高性能日志消费框架](#高性能日志消费框架)
  - [高效的分布式限流](#高效的分布式限流)
  - [统一的错误处理](#统一的错误处理)
  - [安全可靠](#安全可靠)
  - [可扩展性](#可扩展性)
  - [Java友好的技术栈](#java友好的技术栈)
  - [便捷的体验](#便捷的体验)
- [快速启动](#快速启动)
- [常见问题与解决方案](#常见问题与解决方案)
- [相关文档](#相关文档)

## 核心功能

### AI能力点

- **文本处理**
  - **聊天补全**：提供对话补全能力，同时支持OpenAI的ChatCompletion API和Anthropic的Message API
  - **文本嵌入**：生成文本的向量表示，用于语义搜索和相似度计算

- **语音服务**
  - **实时语音识别**：支持流式语音识别和一句话语音识别，适用于实时交互场景
  - **文件转写**：支持上传音频文件进行离线转写
  - **语音合成**：将文本转换为自然语音输出，支持流式
  - **实时对话**：支持用户通过麦克风进行实时语音输入，系统自动识别语音内容，调用大模型生成回复，并将回复内容转换为语音输出

- **图像服务**
  - **图生图**：对图片进行编辑，生成新的图片（敬请期待）
  - **文生图**：根据文本生成图片

### 元数据管理

- **多层级结构**：采用Category-Endpoint-Model-Channel四层结构
  - **Category（类别）**：API服务的顶层分类，如语音服务、文本服务等
  - **Endpoint（端点）**：具体的API功能入口，如实时语音识别、聊天补全等
  - **Model（模型）**：支持各个端点的AI模型，如不同的语音识别模型、大语言模型等等
  - **Channel（通道）**：具体的服务提供方实现，包含供应商、协议和配置信息

- **灵活的路由机制**：基于用户请求和配置，智能选择最合适的服务通道
- **可视化管理界面**：提供直观的Web界面进行元数据配置和管理

### 统一登录服务

- **多种认证方式**：支持OAuth 2.0、CAS单点登录和API Key认证
- **会话管理**：基于Redis的分布式会话存储
- **用户权限**：细粒度的权限控制和管理

### 计费与限流

- **API Key管理**：支持层级化的API Key结构
- **配额控制**：按月度配额管理API使用量
- **限流机制**：基于Redis的分布式限流实现

## 系统优势

### 大规模的生产环境验证

- **极高的稳定性**：已服务于贝壳找房全线业务，服务日均调用量1.5亿，经受住了大规模的生产环境考验
- **丰富的业务场景**：业务场景覆盖广泛，可以适应生产环境中的绝大多数情况
- **广泛的使用场景**：已服务于贝壳找房全线业务，经受住了大规模的生产环境考验

### 丰富的能力

- **全面的AI能力**：支持聊天补全、文本向量化、语音识别（实时、离线、一句话）、语音合成（支持流式）、文生图、图生图等多种AI能力
- **Mock能力**：内置能力点mock功能，可用于单元测试和压力测试

### 优秀的特性

- **Function Call支持**：为不支持functioncall的LLM扩展了functioncall特性
- **路由策略**：优秀的路由策略，确保高峰期能力点渠道的最大化处理能力
- **请求队列**：支持队列特性，确保高峰期请求有序处理
- **备用模型**：支持备用模型机制（敬请期待），提高服务可用性
- **最大等待时间**：支持设置最大等待时间（敬请期待），优化用户体验

### 统一的元数据管理

- **灵活的多层级结构**：采用Category-Endpoint-Model-Channel四层结构，使系统具有高度的可扩展性和灵活性
- **集中式配置**：所有API服务的配置集中管理，便于运维和监控
- **动态路由**：基于用户请求和配置，智能选择最合适的服务通道，提高服务质量

### 高性能缓存设计

- **多级缓存架构**：结合Redisson、Caffeine和JetCache，实现本地缓存与分布式缓存的协同工作
- **高吞吐量**：本地缓存减少网络开销，分布式缓存确保集群一致性
- **自动失效机制**：智能的缓存失效策略，平衡数据一致性和性能


### 高性能日志消费框架

- **基于Disruptor的异步处理**：采用高性能的Disruptor环形缓冲区实现日志事件的异步处理，大幅降低系统延迟
- **多处理器并行架构**：支持多个事件处理器并行工作，同时处理计费、指标收集和限流控制
- **无锁设计**：使用无锁队列和SleepingWaitStrategy策略，减少线程竞争，提高吞吐量
- **优雅的异常处理**：集成专用的异常处理器，确保日志处理错误不影响主业务流程

### 高效的分布式限流和渠道性能监控

- **低耦合性**：基于日志实现，与主逻辑解耦，各个能力点只需按照规则上报日志即可自定义性能监测的维度
- **基于Lua脚本的原子操作**：使用Redis+Lua脚本实现分布式限流，保证原子性和一致性
- **滑动窗口算法**：采用精确的滑动窗口算法实现限流和监控，减少存储资源使用
- **多级缓存设计**：结合本地缓存和分布式缓存，优化限流性能，减少网络开销
- **并发请求控制**：对于试用APIKEY，精确跟踪并控制每个API Key的并发请求数，防止资源过度占用
- **自动过期机制**：智能设置过期时间，避免资源泄漏，确保系统长期稳定运行

### 统一的错误处理

- **一致的用户体验**：统一的错误提示格式和展示方式
- **细粒度错误分类**：区分服务不可用和其他错误类型，提供更精准的反馈
- **友好的错误提示**：针对不同错误类型使用不同的视觉样式，提高用户体验

### 安全可靠

- **多层次认证**：支持OAuth 2.0、CAS单点登录和API Key认证
- **细粒度权限控制**：基于角色的访问控制，确保资源安全
- **完善的审计日志**：记录关键操作，便于追踪和问题排查
- **生产环境验证**：经过大规模的生产环境的验证，稳定可靠

### 可扩展性

- **微服务架构**：基于Spring Boot的微服务设计，便于横向扩展
- **容器化部署**：Docker和Docker Compose支持，简化部署和扩展
- **第三方服务集成**：灵活的通道机制，轻松集成各种AI服务提供商

### Java友好的技术栈

- **Spring Boot生态**：基于Spring Boot框架，对Java开发者友好
- **丰富的工具链**：集成了常用的Java开发工具和库
- **完善的文档**：提供详细的API文档和开发指南

### 便捷的体验

- **提供免费的云端体验服务**：您可以登录访问[Bella OpenAPI](https://api.bella.top)直接体验所有能力
- **docker启动无需编译**：提供了便捷的启动方式，通过启动脚本自动拉取镜像，无需编译
- **便捷的启动配置**：启动脚本提供了丰富的启动参数，您可以根据需要配置，无需修改配置文件


## 快速启动

本项目使用Docker Compose来启动所有服务，包括后端API、前端Web、MySQL和Redis。

### 前提条件

- 安装 [Docker](https://www.docker.com/get-started)
- 安装 [Docker Compose](https://docs.docker.com/compose/install/)
- 执行目录必须在bella-openapi项目的根目录下

### 启动服务

如果本地不存在镜像，会拉取远端镜像

```bash
./start.sh 
./start.sh --github-oauth CLIENT_ID:CLIENT_SECRET --google-oauth CLIENT_ID:CLIENT_SECRET --server URL （通过启动参数配置oauth登录服务和服务域名）
```
注意：如果需要配置用户登录，方法见：[OAuth配置](https://doc.bella.top/docs/bella-openapi/configuration-details#oauth配置)

- 启动服务后会自动检查是否存在系统ak，如果不存在则执行生成系统ak和管理员授权
- 如果不想在启动时进行管理员授权（仍会检查系统ak是否需要生成），可以使用`--skip-auth`参数：

```bash
./start.sh --skip-auth
```

常用选项:
- `-b, --build`: 修改代码后重新构建服务
- `--github-oauth CLIENT_ID:CLIENT_SECRET`: 配置GitHub OAuth登录
- `--google-oauth CLIENT_ID:CLIENT_SECRET`: 配置Google OAuth登录
- `--server URL`: 配置服务域名
- `--skip-auth`: 跳过管理员授权流程

更多详细的启动选项和配置说明，请参阅 [启动与部署详情](https://doc.bella.top/docs/bella-openapi/startup-deployment-details)。

## 常见问题与解决方案

### 1. 前端使用development环境时运行启动脚本编译失败

next.js在dev环境做了预加载相关的优化，如果一定需要使用dev环境，推荐使用next.dev单独启动web服务

### 2. 初始化并启动系统，清除原有数据（开发、测试环境）

1. 删除数据库：`docker exec -it bella-openapi-mysql mysql -uroot -p123456 -e "drop database bella_openapi;"` (如非默用户名和密码，请替换为您的用户名和密码)
2. 停止服务：`docker-compose down`
3. 删除mysql数据：`rm -rf ./mysql`
4. 删除redis数据：`rm -rf ./redis`
5. 重新构建并启动：`./start.sh -b`

## 相关文档

- [配置详情](https://doc.bella.top/docs/bella-openapi/configuration-details) - 详细介绍环境变量配置、数据库配置、缓存配置、Apollo配置和登录服务配置
- [启动与部署详情](https://doc.bella.top/docs/bella-openapi/startup-deployment-details) - 详细介绍启动服务、环境变量配置、服务管理和系统初始化等内容

---
*最后更新: 2025-03-31*
