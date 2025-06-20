# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
Bella OpenAPI is a multi-module Maven project that provides an OpenAPI gateway for AI services. It acts as a unified interface supporting multiple AI providers (OpenAI, AWS Bedrock, Alibaba Cloud, Huoshan, etc.) through a pluggable adapter pattern.

## Module Architecture
- **sdk/**: Core SDK with protocol definitions, DTOs, and client interfaces
- **spi/**: Service Provider Interface module with authentication and session management (CAS/OAuth)
- **server/**: Main Spring Boot application with REST endpoints and business logic

## Build & Development Commands

### Basic Maven Commands
```bash
# Build all modules
mvn clean compile

# Run tests
mvn test

# Run tests for specific module
mvn test -pl server
mvn test -pl sdk
mvn test -pl spi

# Package without tests
mvn package -DskipTests

# Install to local repository
mvn install -DskipTests

# Clean build from scratch
mvn clean package -DskipTests
```

### Development Scripts
```bash
# Build the application (creates release artifacts)
./build.sh

# Run the application (with optimized JVM settings: G1GC, 2GB heap)
./run.sh

# Generate jOOQ code from database schema
mvn jooq:generate -pl server
```

### Testing Commands
```bash
# Run unit tests with coverage
mvn test

# Run specific test class
mvn test -Dtest=ChatControllerTest

# Run tests with specific profile
mvn test -Dspring.profiles.active=ut
```

## Key Architecture Patterns

### Protocol Adapter Pattern
The core architecture uses the **AdaptorManager** pattern to route requests to different AI providers:
- `AdaptorManager` manages protocol adapters for each endpoint
- `IProtocolAdaptor` interface defines how to communicate with each provider
- Adapters are organized by endpoint type (completion, embedding, tts, asr, etc.)

### Multi-Layer Architecture
1. **Controllers** (`endpoints/`): REST API endpoints
2. **Interceptors** (`intercept/`): Cross-cutting concerns (auth, quotas, metrics)
3. **Protocol Layer** (`protocol/`): Provider-specific adapters
4. **Repository Layer** (`db/repo/`): Database access with jOOQ

### Database Integration
- Uses jOOQ for database access with code generation
- Generated POJOs and Records in `server/src/codegen/`
- Database schema initialization scripts in `server/sql/`

## Configuration
- Main config: `server/src/main/resources/application.yml`
- Multi-environment support with profile-specific configs (`application-docker.yml`, `application-ut.yml`)
- Uses Redis for caching (Redisson) and JetCache for L1/L2 caching
- Apollo configuration center support (optional)

## Caching & Performance

### Multi-Level Caching Strategy
- **L1 Cache**: Caffeine (local, 2-minute TTL, 100 item limit)
- **L2 Cache**: Redis via Redisson (distributed, 10-minute TTL)  
- **Cache Invalidation**: `BellaOpenapiBroadcastChannel` for distributed cache invalidation
- **JetCache Integration**: Unified caching interface with automatic serialization

### High-Performance Features
- **Disruptor Framework**: Async logging for high-throughput scenarios
- **G1 Garbage Collector**: Optimized JVM settings in `./run.sh`
- **Connection Pooling**: OkHttp with configurable connection pools
- **Rate Limiting**: Redis + Lua scripts with sliding window algorithm (`server/src/main/resources/lua/`)

### Distributed Rate Limiting
- **RPM Limiting**: `rpm.lua` script for requests per minute
- **Concurrent Limiting**: `concurrent.lua` for active request limiting  
- **Sliding Window**: Advanced algorithm for accurate rate limiting across distributed instances

## Testing
- Test configuration: `application-ut.yml`
- API test files in `server/src/test/resources/`
- Mock implementations available for each protocol adapter

## Key Directories
- `server/src/main/java/com/ke/bella/openapi/endpoints/`: REST controllers
- `server/src/main/java/com/ke/bella/openapi/protocol/`: Provider adapters
- `server/src/main/java/com/ke/bella/openapi/intercept/`: Request/response interceptors
- `sdk/src/main/java/com/ke/bella/openapi/protocol/`: Protocol DTOs and interfaces
- `server/src/main/resources/lua/`: Lua scripts for Redis operations

## Request Processing Flow
The typical request flow follows this pattern:
1. **Endpoint Controller** receives HTTP request and validates basic parameters
2. **Interceptors** handle authentication, authorization, quotas, and safety checks
3. **Channel Router** selects appropriate provider/channel based on load balancing and cost
4. **Protocol Adapter** transforms request to provider-specific format
5. **Cost Handler** calculates and records usage metrics
6. **Response Processing** handles streaming/non-streaming responses and metrics collection

## Channel and Provider Management
- **Channels** represent specific provider instances with their own configurations
- **Models** define available AI models and their capabilities per provider
- **API Keys** manage authentication and usage quotas per space/user
- **Spaces** provide multi-tenant isolation with separate configurations

## Key Technologies & Dependencies

### Core Framework Stack
- **Spring Boot 2.3.12** with Spring Cloud Hoxton.SR12
- **Java 8** runtime environment
- **Maven Multi-Module** project structure

### Database & Persistence
- **jOOQ 3.14.15** for type-safe database operations with code generation
- **HikariCP** connection pooling
- **Database Migrations**: SQL scripts in `server/sql/`

### Caching & Performance
- **Redisson 3.9.1** for Redis integration and distributed operations
- **JetCache** for multi-level caching (L1: Caffeine, L2: Redis)
- **Disruptor 3.4.4** for high-performance async logging

### HTTP & Integration
- **OkHttp 4.12.0** for external HTTP client operations
- **AWS SDK 2.31.65** for AWS Bedrock integration
- **SpringDoc OpenAPI** for API documentation

## Monitoring & Observability

### Metrics & Health
- **Micrometer + Prometheus**: Metrics available at `/actuator/prometheus`
- **Spring Actuator**: Health checks and application insights
- **Structured Logging**: JSON format with request tracing via Logback

### Performance Monitoring
- **Real-time Cost Tracking**: Usage calculation and reporting per request
- **Request Tracing**: Full request lifecycle logging with unique trace IDs
- **Cache Hit Rates**: Multi-level cache performance metrics

## Debugging & Development Tips

### Common Debugging Scenarios
- **Protocol Adapter Issues**: Check `AdaptorManager` registration and provider-specific logs
- **Rate Limiting Problems**: Examine Redis Lua script execution in `server/src/main/resources/lua/`
- **Database Query Issues**: Enable jOOQ SQL logging in `application.yml`
- **Caching Problems**: Monitor JetCache statistics and Redis connectivity

### Useful Application Properties
```yaml
# Enable SQL logging for debugging
logging.level.org.jooq.tools.LoggerListener: DEBUG

# Cache debugging
jetcache.statIntervalMinutes: 1

# Request/Response logging
logging.level.com.ke.bella.openapi.intercept: DEBUG
```

## Development Notes
- When adding new AI providers, implement `IProtocolAdaptor` and register with `AdaptorManager`
- Protocol-specific DTOs go in SDK module, implementation in server module
- Cost calculation and metrics collection are handled through dedicated handlers
- Some endpoints support both streaming and non-streaming responses
- **jOOQ Code Regeneration**: Run `mvn jooq:generate -pl server` after database schema changes
- **Multi-Tenant Testing**: Use different API keys to test space isolation
- **Redis Lua Scripts**: Modify scripts in `server/src/main/resources/lua/` for custom rate limiting logic
