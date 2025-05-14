# Bella OpenAPI 配置详情

本文档详细介绍了 Bella OpenAPI 的配置管理，包括环境变量配置、数据库配置、缓存配置、Apollo配置、登录服务配置和代理配置。

## 目录

- [环境变量配置](#环境变量配置)
  - [环境变量优先级](#环境变量优先级)
  - [web构建时环境变量](#web构建时环境变量)
  - [web运行时环境变量](#web运行时环境变量)
- [数据库配置](#数据库配置)
- [缓存配置](#缓存配置)
- [Apollo配置](#apollo配置)
- [登录服务配置](#登录服务配置)
  - [session配置](#session配置)
  - [登录类型配置](#登录类型配置)
  - [CAS配置](#cas配置)
  - [OAuth配置](#oauth配置)
    - [GitHub OAuth配置攻略](#github-oauth配置攻略)
- [代理配置](#代理配置)
  - [系统属性配置](#系统属性配置)
  - [代理类型](#代理类型)
  - [代理域名](#代理域名)

## 环境变量配置

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

**Next.js构建方法：**

```bash
# 在web目录下执行
cd web

# 安装依赖
npm install

# 使用开发环境变量构建
NODE_ENV=development npm run build

# 使用生产环境变量构建
NODE_ENV=production npm run build
```

### web运行时环境变量

运行时环境变量通过docker-compose.yml的environment部分设置：

```yaml
environment:
  - NODE_ENV=development
  - DEPLOY_ENV=development
```

这些变量会在容器运行时生效，但对于已经构建好的静态文件，只有以NEXT_PUBLIC_开头的环境变量才能在客户端访问。

**Next.js运行方法：**

```bash
# 在web目录下执行

# 开发模式运行（带热重载）
npm run dev

# 以生产模式运行已构建的应用
npm run start

# 使用自定义端口运行
PORT=3001 npm run start
```

## 数据库配置

MySQL数据库配置示例（application.yml）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/bella_openapi?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: bella_user
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
```

## 缓存配置

**使用redisson + caffeine + jetcache**

缓存配置示例（application.yml）：

```yaml
spring:
  redis:
    redisson:
      config: |
        singleServerConfig:
          address: "redis://localhost:6379"
        codec: !<org.redisson.client.codec.StringCodec> {}
jetcache:
  statIntervalMinutes: 0
  areaInCacheName: false
  hidePackages: com.ke.bella.openapi
  local:
    default:
      type: caffeine
      limit: 100
      keyConvertor: jackson
      expireAfterAccessInMillis: 0
      expireAfterWriteInMillis: 120000
  remote:
    default:
      type: redisson
      redissonClient: redissonClient
      broadcastChannel: BellaOpenapiBroadcastChannel
      keyConvertor: jackson
      expireAfterAccessInMillis: 0
      expireAfterWriteInMillis: 600000
      keyPrefix: bella-openapi-
```

## Apollo配置

**默认不启用，如果需要可以按照如下配置, 并且在启动时设置环境变量：** 
**-Dapollo.enabled=true**

Apollo动态配置管理示例（application.yml）：

```yaml
apollo:
  bootstrap:
    enabled: true
    namespaces: application,bella.openapi
  meta: http://apollo-config:8080
```

## 登录服务配置

**提供CAS和OAuth两种登录方式，可通过环境变量或配置文件进行配置，使用启动脚本时，亦可通过启动脚本参数进行配置**

### session配置

```yaml
bella:
  session:
    cookie-name: bella_openapi_sessionId
    max-inactive-interval: 60
    cookie-max-age: -1
    cookie-domain: localhost # 改为自己的域名，脚本启动时会自动配置
    cookie-context-path: /
```

### 登录类型配置

通过设置`bella.login.type`环境变量或在配置文件中指定登录类型：

```yaml
bella:
  login:
    type: cas  # 可选值: cas, oauth
```

### CAS配置

CAS单点登录配置示例：

```yaml
bella:
  cas:
    client-support: true  # 是否支持客户端
    source: company_name  # 来源标识，默认为cas
    server-url-prefix: https://your-cas-server.com/  # CAS服务器URL前缀
    server-login-url: https://your-cas-server.com/login  # CAS登录URL
    client-host: http://your-app-host:8080  # 客户端主机
    client-index-url: http://your-frontend-url/apikey  # 客户端首页URL
```

### OAuth配置

OAuth登录配置示例：

```yaml
bella:
  oauth:
    client-index: http://localhost:3000  # 客户端首页URL
    providers:  # 支持多个OAuth提供商
      google:  # Google OAuth配置
        enabled: true
        client-id: your-google-client-id
        client-secret: your-google-client-secret
        auth-uri: https://accounts.google.com/o/oauth2/v2/auth
        token-uri: https://oauth2.googleapis.com/token
        user-info-uri: https://www.googleapis.com/oauth2/v3/userinfo
        scope: profile email
      github:  # GitHub OAuth配置
        enabled: true
        client-id: your-github-client-id
        client-secret: your-github-client-secret
        scope: read:user user:email
        authUri: https://github.com/login/oauth/authorize
        tokenUri: https://github.com/login/oauth/access_token
        userInfoUri: https://api.github.com/user
```

### GitHub OAuth配置攻略

Google OAuth配置可参照此方式，登录 [Google Cloud Console](https://console.cloud.google.com/)进行配置

要配置GitHub OAuth登录，请按照以下步骤操作：

1. **创建GitHub OAuth应用**：
   - 登录到您的GitHub账户
   - 访问 [GitHub Developer Settings](https://github.com/settings/developers)
   - 点击 "OAuth Apps" 选项卡
   - 点击 "New OAuth App" 按钮

2. **填写应用信息**：
   - **Application name**：填写您的应用名称，例如 "Bella OpenAPI"
   - **Homepage URL**：填写您的应用主页URL，例如 `http://localhost:3000`
   - **Application description**：（可选）填写应用描述
   - **Authorization callback URL**：填写回调URL，必须与配置文件中的`redirect`为域名，假设为`http://localhost:8080`， 那么url为 `/openapi/oauth/callback/github`， 如果google则为`http://localhost:8080/openapi/oauth/callback/google`
   - 点击 "Register application" 按钮

3. **获取Client ID和Client Secret**：
   - 注册成功后，您将看到应用详情页面
   - 记录下 "Client ID"
   - 点击 "Generate a new client secret" 生成Client Secret
   - 立即复制并保存Client Secret，因为它只会显示一次

4. **更新配置文件**：
   - 在应用的配置文件中填入获取的Client ID和Client Secret：
   ```yaml
   bella:
     oauth:
       providers:
         github:
           enabled: true
           client-id: 您的GitHub Client ID  # 例如：89a6d5f8c7b3e2a1d0f9
           client-secret: 您的GitHub Client Secret  # 例如：3e7d9c8b5a4f2e1d0c9b8a7f6e5d4c3b2a1f0e9d
           scope: read:user user:email
           authUri: https://github.com/login/oauth/authorize
           tokenUri: https://github.com/login/oauth/access_token
           userInfoUri: https://api.github.com/user
   ```

5. **注意事项**：
   - 确保`redirect-uri`与GitHub开发者设置中的回调URL完全匹配
   - 在生产环境中，请使用HTTPS URL
   - 如果您的应用部署在不同的域名或端口，请相应地更新配置
   - GitHub OAuth默认包含用户的公开信息，如需访问邮箱，需要添加`user:email`权限

6. **测试配置**：
   - 启动应用后，访问登录页面
   - 点击"使用GitHub账号登录"按钮
   - 您将被重定向到GitHub授权页面
   - 授权后，您将被重定向回应用，完成登录流程

### 环境变量配置

可以通过环境变量覆盖配置文件中的设置：

```bash
# 登录类型
export BELLA_LOGIN_TYPE=oauth

# CAS配置
export BELLA_CAS_SERVER_URL_PREFIX=https://your-cas-server.com/
export BELLA_CAS_SERVER_LOGIN_URL=https://your-cas-server.com/login

# OAuth配置
export BELLA_OAUTH_LOGIN_PAGE_URL=http://your-frontend-url/login
export BELLA_OAUTH_CLIENT_INDEX=http://your-frontend-url

# 启动应用时指定配置文件
java -jar bella-openapi.jar --spring.profiles.active=dev
```

## 代理配置

### 系统属性配置

可以通过系统属性配置代理服务器：

```bash
-Dbella.proxy.host=127.0.0.1
-Dbella.proxy.type=http
-Dbella.proxy.port=8118
-Dbella.proxy.domains=github.com,google.com
```

### 代理类型

支持两种代理类型：http和socks。

### 代理域名

- 可以配置域名过滤规则，指定哪些域名需要代理：
- 如果只配置代理，不配置域名则代理全部域名

---
*最后更新: 2025-03-31*