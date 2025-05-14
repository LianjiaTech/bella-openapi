# Bella OpenAPI 启动与部署详情

本文档详细介绍了 Bella OpenAPI 的启动和部署流程，包括前提条件、启动服务、环境变量配置、服务管理和系统初始化等内容。

## 目录

- [项目概述](#项目概述)
  - [项目结构](#项目结构)
  - [技术栈](#技术栈)
- [前提条件](#前提条件)
- [启动服务](#启动服务)
  - [启动选项](#启动选项)
  - [启动示例](#启动示例)
- [docker-compose环境变量配置](#docker-compose环境变量配置)
  - [环境变量优先级](#环境变量优先级)
  - [web构建时环境变量](#web构建时环境变量)
  - [web运行时环境变量](#web运行时环境变量)
- [服务管理](#服务管理)
  - [停止服务](#停止服务)
  - [查看日志](#查看日志)
  - [重启服务](#重启服务)
- [系统初始化](#系统初始化)
  - [生成系统API Key](#生成系统api-key)
    - [使用方法](#使用方法)
    - [脚本功能](#脚本功能)
    - [重要说明](#重要说明)
    - [示例输出](#示例输出)
  - [授权管理员](#授权管理员)
    - [授权流程](#授权流程)
    - [手动授权](#手动授权)
- [Docker 镜像管理](#docker-镜像管理)
  - [推送 Docker 镜像](#推送-docker-镜像)
    - [构建并推送镜像](#构建并推送镜像)
    - [在生产服务器上部署](#在生产服务器上部署)
    - [使用特定版本的镜像](#使用特定版本的镜像)
    - [注意事项](#注意事项)

## 项目概述

Bella OpenAPI是一个综合性的AI开放API平台，提供以下主要组件：

- **项目结构**：
  - `api/`: 后端API服务，基于Spring Boot框架
  - `web/`: 前端Web应用，基于React和Next.js

- **技术栈**：
  - 后端：Java、Spring Boot、MySQL、Redis
  - 前端：React、Next.js
  - 部署：Docker、Docker Compose，需要 Docker Compose 1.13.0+ 版本
  - 网关：Nginx（可选，使用脚本启动时会自动在容器中创建）

## 前提条件

- 安装 [Docker](https://www.docker.com/get-started)
- 安装 [Docker Compose](https://docs.docker.com/compose/install/)
- 执行目录必须在bella-openapi项目的根目录下

## 启动服务

启动服务时通常需要配置用户登录，方法见：[GitHub OAuth配置攻略](./configuration-details.md#github-oauth配置攻略)

```bash
./start.sh [选项] （如果本地不存在镜像，会拉取远端镜像）
```
注意：windows下请使用git bash工具执行命令

选项:
- `-b, --build`: 重新构建服务
- `-r, --rebuild`: 强制重新构建服务（不使用缓存）
- `-e, --env ENV`: 指定环境（dev, test, prod）
- `-h, --help`: 显示帮助信息
- `--skip-auth`: 跳过授权步骤
- `--server URL`: 配置服务域名
- `--github-oauth CLIENT_ID:CLIENT_SECRET`: 配置GitHub OAuth登录
- `--google-oauth CLIENT_ID:CLIENT_SECRET`: 配置Google OAuth登录
- `--cas-server URL`: 配置CAS服务器URL
- `--cas-login URL`: 配置CAS登录URL
- `--proxy-host HOST`: 配置代理服务器主机名或IP地址
- `--proxy-port PORT`: 配置代理服务器端口
- `--proxy-type TYPE`: 配置代理类型（socks 或 http）
- `--proxy-domains DOMAINS`: 配置需要通过代理访问的域名，多个域名用逗号分隔
- `--version VERSION`: 指定镜像版本
- `--push`: 构建后推送镜像到仓库
- `--registry username`: 指定docker仓库 (username)
- `--restart-web`: 仅重启前端服务
- `--restart-api`: 仅重启后端服务
- `--nginx-port PORT`: 指定Nginx服务映射到的端口，默认为80
- `--update-image`: 从远程仓库更新镜像，即使本地已存在
- `--service CONTAINER_NAME:DOMAIN:PORT`: 配置额外服务的Nginx转发，格式为服务名:域名:端口，多个服务用逗号分隔，只支持同一docker网络下的服务，使用CONTAINER_NAME转发

示例:
```bash
./start.sh                       # 启动服务，不重新构建，如果本地不存在镜像，会拉取远端镜像
./start.sh -b                    # 启动服务并重新构建（会使用缓存，增量修改编译较快）
./start.sh -r                    # 启动服务并强制重新构建（不使用缓存）
./start.sh -e test               # 以测试环境启动服务
./start.sh --skip-auth           # 启动服务但跳过授权步骤
./start.sh --server http://example.com #配置服务域名
./start.sh --proxy-host 127.0.0.1 --proxy-port 8118 --proxy-type http --proxy-domains github.com,google.com # 配置HTTP代理
./start.sh --proxy-host proxy.com --proxy-port 80 --proxy-type socks # 配置SOCKS代理，不指定特定域名

# 没有修改源码，只想拉取远端镜像，并配置登录方式和服务域名，通常使用以下命令即可：
#不配置域名，通常用于本地部署：
./start.sh --github-oauth ${clientId}:${secret} --google-oauth ${clientId}:${secret} #配置github和google的oauth登录

#配置域名，通常用于服务端部署：
./start.sh --github-oauth ${clientId}:${secret} --google-oauth ${clientId}:${secret} --server http://example.com #配置github和google的oauth登录以及服务域名

#配置域名和代理，用于需要通过代理访问外部服务的环境：
./start.sh --github-oauth ${clientId}:${secret} --google-oauth ${clientId}:${secret} --server http://example.com --proxy-host 127.0.0.1 --proxy-port 8118 --proxy-type http --proxy-domains github.com,google.com

./start.sh --cas-server https://cas.example.com --cas-login https://cas.example.com/login --server http://example.com #配置CAS登录和服务域名,如果既配置cas登录又配置oauth登录，登录时会使用cas登录
```

## docker-compose环境变量配置

### 环境变量优先级

在Docker环境中，环境变量的优先级从高到低为：
1. docker-compose.yml中的environment设置
2. Dockerfile中的ENV指令
3. .env文件、 yaml文件

### web构建时环境变量

构建时环境变量通过docker-compose.yml的args部分传递给Dockerfile：

```yaml
build:
  context: ./web
  args:
    - NODE_ENV=development
    - DEPLOY_ENV=development
    - SKIP_INSTALL=false
```

这些变量会影响应用的构建过程，例如Next.js会根据NODE_ENV的值加载不同的环境配置文件（.env.development, .env.production等）。

### web运行时环境变量

运行时环境变量通过docker-compose.yml的environment部分设置：

```yaml
environment:
  - NODE_ENV=development
  - DEPLOY_ENV=development
```

这些变量会在容器运行时生效，但对于已经构建好的静态文件，只有以NEXT_PUBLIC_开头的环境变量才能在客户端访问。

## 停止服务

```bash
./stop.sh
```
或者直接使用Docker Compose命令：

```bash
docker-compose down
```

## 查看日志

```bash
# 查看所有服务的日志
docker-compose logs

# 查看特定服务的日志
docker-compose logs api  # 后端API服务
docker-compose logs web  # 前端Web应用
docker-compose logs mysql
docker-compose logs redis

# 实时查看日志
docker-compose logs -f
```

## 重启服务

可以使用start.sh脚本重启特定服务：

```bash
# 重启前端服务
./start.sh --restart-web

# 重启后端服务
./start.sh --restart-api --github-oauth ${clientId}:${secret} --google-oauth ${clientId}:${secret} --server http://example.com
```

也可以直接使用Docker Compose命令：

```bash
# 重启前端服务
docker-compose restart web

# 查看前端日志
docker-compose logs -f web
```

## 系统初始化

### 生成系统API Key

系统初始化时需要生成一个系统级别的API Key，用于管理和访问所有API资源。我们提供了一个脚本来自动生成系统API Key并将其写入数据库。
./start.sh启动服务时会自动生成。如需手动生成，请使用./generate-system-apikey.sh脚本。

#### 使用方法

##### 直接在Docker环境中执行
./generate-system-apikey.sh

##### 使用自定义数据库连接参数
DB_USER=bella_user DB_PASS=123456 ./generate-system-apikey.sh

#### 脚本功能

1. 检查数据库中是否已存在系统API Key
2. 如果不存在，生成一个新的系统API Key并写入数据库
3. 如果已存在，显示现有系统API Key的信息
4. 将API Key信息保存到`system-apikey.txt`文件中

#### 重要说明

- 系统API Key具有最高权限，请妥善保管
- API Key只会在生成时显示一次，之后只能看到掩码版本
- 如果丢失API Key，需要重新生成一个新的，如果是系统AK，需要将其设置为失效，并重新生成
- 生成的API Key具有`all`角色，可以访问所有端点
- 脚本需要在Docker环境中运行，会自动连接到MySQL容器

#### 示例输出

```
正在生成系统API key...
检查数据库中是否已存在系统API key...
在数据库中插入新的系统API key...
成功生成并插入系统API key到数据库。
API Key Code: ak-026e84f5-8a1c-4243-a800-d44581f0f1b7
API Key: 9be9d54d-d4ae-4510-8819-a62a4e69e57b
API Key SHA-256: d58ed6447aa8da22d6fa3064d242f8f8dd74a6df4a1663084f4003b2d559b9ea
API Key Display: 9b****e57b
API key详细信息已保存到 system-apikey.txt
重要: 请妥善保管此信息，API key只会显示一次！
完成。
```

### 授权管理员

系统初始化后，需要授权管理员用户。管理员用户可以管理API Key、用户权限等系统资源。

#### 授权流程

1. 首先启动服务并生成系统API Key
   ```bash
   ./start.sh
   ```
   如果新生成系统ak时会自动进入管理员授权流程，如果不想在启动时进行管理员授权（仍会检查系统ak是否需要生成），可以使用`--skip-auth`参数：
   ```bash
   ./start.sh --skip-auth
   ```
   
   注意：如果系统中已存在API Key，脚本会自动跳过管理员授权步骤。只有在首次生成新的API Key时才会询问是否需要授权管理员。

2. 启动脚本会询问您是否需要授权管理员
   - 如果选择"是"，脚本会引导您完成整个授权流程
   - 如果选择"否"，您可以稍后手动运行授权脚本
   - 如果使用了 `--skip-auth` 参数，则会跳过询问步骤

3. 按照提示登录前端页面获取用户ID或邮箱
   - 访问 http://localhost:3000
   - 使用第三方账号登录（如Google、GitHub等）
   - 点击右上角头像，查看个人信息获取用户ID或邮箱

4. 授权脚本会自动启动，根据提示输入用户信息
   - 可以选择使用用户ID或邮箱进行授权
   - 按照脚本提示输入相关信息

5. 如果您稍后需要授权管理员，可以随时运行：
   ```bash
   ./authorize-admin.sh
   ```
6. 授权后点击头像登出系统，重新登录即可拥有管理员权限

#### 手动授权

也可以使用curl命令手动授权：

##### 使用用户ID授权
```bash
curl --location 'http://localhost:8080/console/userInfo/manager' \
--header 'Authorization: Bearer YOUR_SYSTEM_API_KEY' \
--header 'Content-Type: application/json' \
--data-raw '{
    "managerAk": "YOUR_SYSTEM_API_KEY_CODE",
    "userId": YOUR_USER_ID,
    "userName": "YOUR_USER_NAME"
}'
```

##### 使用邮箱授权
```bash
curl --location 'http://localhost:8080/console/userInfo/manager' \
--header 'Authorization: Bearer YOUR_SYSTEM_API_KEY' \
--header 'Content-Type: application/json' \
--data-raw '{
    "managerAk": "YOUR_SYSTEM_API_KEY_CODE",
    "source": "google",
    "email": "YOUR_EMAIL@gmail.com",
    "userId": 0,
    "userName": "YOUR_USER_NAME"
}'
```

## Docker 镜像管理

### 推送 Docker 镜像

为了便于在生产环境中部署，您可以在本地构建并推送 Docker 镜像到 Docker Hub 或其他 Docker 仓库，然后在服务器上直接拉取和启动这些镜像，而无需在服务器上进行构建。

#### 构建并推送镜像

```bash
# 构建并推送镜像到 Docker Hub
./start.sh --build --push --registry 用户名 --version v1.0.0
```

参数说明：
- `--build`: 构建 Docker 镜像
- `--push`: 构建完成后推送镜像到仓库
- `--registry 用户名`: 指定 Docker Hub 的用户名
- `--version v1.0.0`: 指定镜像版本号（默认为 v1.0.0）

执行此命令后，脚本会：
1. 构建 API 和 Web 服务的 Docker 镜像
2. 将镜像标记为 `用户名/bella-openapi-api:v1.0.0` 和 `用户名/bella-openapi-web:v1.0.0`
3. 同时标记为 `用户名/bella-openapi-api:latest` 和 `用户名/bella-openapi-web:latest`
4. 推送这些镜像到 Docker Hub

#### 在生产服务器上部署

在生产服务器上，您可以直接使用已推送的镜像启动服务：

```bash
# 拉取并启动已推送的镜像
./start.sh --registry 用户名 --version v1.0.0
```

这将从 Docker Hub 拉取指定版本的镜像并启动服务，无需在服务器上进行构建过程，大大减少了部署时间和资源消耗。

#### 使用特定版本的镜像

如果您需要使用特定版本的镜像，可以通过 `--version` 参数指定：

```bash
# 使用特定版本的镜像，并配置自己的登录选项
./start.sh --registry 用户名 --version v1.1.0 --github-oauth CLIENT_ID:CLIENT_SECRET --google-oauth CLIENT_ID:CLIENT_SECRET --server http://example.com
```

#### 注意事项

1. 确保您已登录到 Docker Hub：
   ```bash
   docker login
   ```

2. 推送镜像前，请确保您有足够的权限访问指定的 Docker Hub 仓库

3. 在生产环境中，建议使用具体的版本号而不是 `latest` 标签，以确保部署的一致性和可追溯性

4. 如果您使用私有 Docker 仓库，请相应地调整 `--registry` 参数

---
*最后更新: 2025-03-31*