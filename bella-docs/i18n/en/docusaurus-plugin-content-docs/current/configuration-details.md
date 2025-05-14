# Bella OpenAPI Configuration Details

This document provides detailed information about Bella OpenAPI configuration management, including environment variables, database configuration, cache configuration, Apollo configuration, login service configuration, and proxy configuration.

## Table of Contents

- [Environment Variable Configuration](#environment-variable-configuration)
  - [Environment Variable Priority](#environment-variable-priority)
  - [Web Build-time Environment Variables](#web-build-time-environment-variables)
  - [Web Runtime Environment Variables](#web-runtime-environment-variables)
- [Database Configuration](#database-configuration)
- [Cache Configuration](#cache-configuration)
- [Apollo Configuration](#apollo-configuration)
- [Login Service Configuration](#login-service-configuration)
  - [Session Configuration](#session-configuration)
  - [Login Type Configuration](#login-type-configuration)
  - [CAS Configuration](#cas-configuration)
  - [OAuth Configuration](#oauth-configuration)
    - [GitHub OAuth Configuration Guide](#github-oauth-configuration-guide)
- [Proxy Configuration](#proxy-configuration)
  - [System Property Configuration](#system-property-configuration)
  - [Proxy Types](#proxy-types)
  - [Proxy Domains](#proxy-domains)

## Environment Variable Configuration

### Environment Variable Priority

In Docker environments, the priority of environment variables from highest to lowest is:
1. environment settings in docker-compose.yml
2. ENV directives in Dockerfile
3. .env files, yaml files

### Web Build-time Environment Variables

Build-time environment variables are passed to Dockerfile through the args section in docker-compose.yml:

```yaml
build:
  context: ./web
  args:
    - NODE_ENV=development
    - DEPLOY_ENV=development
    - SKIP_INSTALL=false
```

These variables affect the application build process. For example, Next.js will load different environment configuration files (.env.development, .env.production, etc.) based on the NODE_ENV value.

**Next.js Build Methods:**

```bash
# Execute in the web directory
cd web

# Install dependencies
npm install

# Build with development environment variables
NODE_ENV=development npm run build

# Build with production environment variables
NODE_ENV=production npm run build
```

### Web Runtime Environment Variables

Runtime environment variables are set through the environment section in docker-compose.yml:

```yaml
environment:
  - NODE_ENV=development
  - DEPLOY_ENV=development
```

These variables take effect when the container is running, but for pre-built static files, only environment variables prefixed with NEXT_PUBLIC_ can be accessed on the client side.

**Next.js Run Methods:**

```bash
# Execute in the web directory

# Run in development mode (with hot reloading)
npm run dev

# Run the built application in production mode
npm run start

# Run with a custom port
PORT=3001 npm run start
```

## Database Configuration

MySQL database configuration example (application.yml):

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

## Cache Configuration

**Using redisson + caffeine + jetcache**

Cache configuration example (application.yml):

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

## Apollo Configuration

**Disabled by default. If needed, configure as follows and set the environment variable at startup:**
**-Dapollo.enabled=true**

Apollo dynamic configuration management example (application.yml):

```yaml
apollo:
  bootstrap:
    enabled: true
    namespaces: application,bella.openapi
  meta: http://apollo-config:8080
```

## Login Service Configuration

**Provides both CAS and OAuth login methods. Can be configured via environment variables or configuration files. When using startup scripts, configuration can also be provided through script parameters.**

### Session Configuration

```yaml
bella:
  session:
    cookie-name: bella_openapi_sessionId
    max-inactive-interval: 60
    cookie-max-age: -1
    cookie-domain: localhost # Change to your domain, automatically configured when using startup script
    cookie-context-path: /
```

### Login Type Configuration

Set the login type by configuring the `bella.login.type` environment variable or in the configuration file:

```yaml
bella:
  login:
    type: cas  # Available options: cas, oauth
```

### CAS Configuration

CAS single sign-on configuration example:

```yaml
bella:
  cas:
    client-support: true  # Whether to support client
    source: company_name  # Source identifier, default is cas
    server-url-prefix: https://your-cas-server.com/  # CAS server URL prefix
    server-login-url: https://your-cas-server.com/login  # CAS login URL
    client-host: http://your-app-host:8080  # Client host
    client-index-url: http://your-frontend-url/apikey  # Client home page URL
```

### OAuth Configuration

OAuth login configuration example:

```yaml
bella:
  oauth:
    client-index: http://localhost:3000  # Client home page URL
    providers:  # Support for multiple OAuth providers
      google:  # Google OAuth configuration
        enabled: true
        client-id: your-google-client-id
        client-secret: your-google-client-secret
        auth-uri: https://accounts.google.com/o/oauth2/v2/auth
        token-uri: https://oauth2.googleapis.com/token
        user-info-uri: https://www.googleapis.com/oauth2/v3/userinfo
        scope: profile email
      github:  # GitHub OAuth configuration
        enabled: true
        client-id: your-github-client-id
        client-secret: your-github-client-secret
        scope: read:user user:email
        authUri: https://github.com/login/oauth/authorize
        tokenUri: https://github.com/login/oauth/access_token
        userInfoUri: https://api.github.com/user
```

### GitHub OAuth Configuration Guide

Google OAuth configuration can follow a similar approach. Log in to [Google Cloud Console](https://console.cloud.google.com/) to configure.

To configure GitHub OAuth login, follow these steps:

1. **Create a GitHub OAuth Application**:
   - Log in to your GitHub account
   - Visit [GitHub Developer Settings](https://github.com/settings/developers)
   - Click the "OAuth Apps" tab
   - Click the "New OAuth App" button

2. **Fill in Application Information**:
   - **Application name**: Enter your application name, e.g., "Bella OpenAPI"
   - **Homepage URL**: Enter your application homepage URL, e.g., `http://localhost:3000`
   - **Application description**: (Optional) Enter application description
   - **Authorization callback URL**: Enter the callback URL, which must match the domain in the configuration file. For example, if the domain is `http://localhost:8080`, then the URL should be `/openapi/oauth/callback/github`, or for Google, it would be `http://localhost:8080/openapi/oauth/callback/google`
   - Click the "Register application" button

3. **Get Client ID and Client Secret**:
   - After successful registration, you will see the application details page
   - Record the "Client ID"
   - Click "Generate a new client secret" to generate a Client Secret
   - Copy and save the Client Secret immediately, as it will only be displayed once

4. **Update Configuration File**:
   - Fill in the obtained Client ID and Client Secret in your application's configuration file:
   ```yaml
   bella:
     oauth:
       providers:
         github:
           enabled: true
           client-id: Your GitHub Client ID  # Example: 89a6d5f8c7b3e2a1d0f9
           client-secret: Your GitHub Client Secret  # Example: 3e7d9c8b5a4f2e1d0c9b8a7f6e5d4c3b2a1f0e9d
           scope: read:user user:email
           authUri: https://github.com/login/oauth/authorize
           tokenUri: https://github.com/login/oauth/access_token
           userInfoUri: https://api.github.com/user
   ```

5. **Important Notes**:
   - Ensure the `redirect-uri` exactly matches the callback URL in the GitHub developer settings
   - Use HTTPS URLs in production environments
   - If your application is deployed at a different domain or port, update the configuration accordingly
   - GitHub OAuth by default includes public user information; to access email, add the `user:email` permission

6. **Test Configuration**:
   - After starting the application, visit the login page
   - Click the "Login with GitHub" button
   - You will be redirected to the GitHub authorization page
   - After authorization, you will be redirected back to the application, completing the login process

### Environment Variable Configuration

You can override the settings in the configuration file with environment variables:

```bash
# Login type
export BELLA_LOGIN_TYPE=oauth

# CAS configuration
export BELLA_CAS_SERVER_URL_PREFIX=https://your-cas-server.com/
export BELLA_CAS_SERVER_LOGIN_URL=https://your-cas-server.com/login

# OAuth configuration
export BELLA_OAUTH_LOGIN_PAGE_URL=http://your-frontend-url/login
export BELLA_OAUTH_CLIENT_INDEX=http://your-frontend-url

# Start the application with a specific configuration file
java -jar bella-openapi.jar --spring.profiles.active=dev
```

## Proxy Configuration

### System Property Configuration

You can configure proxy servers using system properties:

```bash
-Dbella.proxy.host=127.0.0.1
-Dbella.proxy.type=http
-Dbella.proxy.port=8118
-Dbella.proxy.domains=github.com,google.com
```

### Proxy Types

Two proxy types are supported: http and socks.

### Proxy Domains

- You can configure domain filtering rules to specify which domains need proxying
- If you only configure the proxy without specifying domains, all domains will be proxied

---
*Last updated: 2025-03-31*