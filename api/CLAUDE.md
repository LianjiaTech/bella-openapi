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

# Package without tests
mvn package -DskipTests

# Install to local repository
mvn install
```

### Development Scripts
```bash
# Build the application
./build.sh

# Run the application
./run.sh
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
- Multi-environment support with profile-specific configs
- Uses Redis for caching (Redisson) and JetCache for L1/L2 caching
- Apollo configuration center support (optional)

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

## Development Notes
- When adding new AI providers, implement `IProtocolAdaptor` and register with `AdaptorManager`
- Protocol-specific DTOs go in SDK module, implementation in server module
- Cost calculation and metrics collection are handled through dedicated handlers
- Some endpoints support both streaming and non-streaming responses
