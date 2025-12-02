# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Bella OpenAPI is an AI API gateway that provides unified access to multiple AI services (chat completion, embeddings, ASR, TTS, image generation). It's a production-grade system serving 150M+ daily API calls at Beike (KE Holdings). The project consists of a Java Spring Boot backend (api/) and a Next.js frontend (web/), deployed via Docker.

## Common Development Commands

### Docker Deployment (Recommended)
```bash
# Start all services with Docker
./start.sh

# Start with specific configurations
./start.sh --build                    # Rebuild services
./start.sh --rebuild                  # Force rebuild without cache
./start.sh --nginx-port 8080         # Use custom port
./start.sh --server https://domain.com  # Set server domain
./start.sh --skip-auth               # Skip admin authorization

# OAuth configuration
./start.sh --github-oauth CLIENT_ID:SECRET --google-oauth CLIENT_ID:SECRET

# Stop services
./stop.sh
```

### Backend API Development (Java/Maven)
```bash
cd api/

# Build the project
mvn clean compile
mvn clean package -DskipTests

# Generate jOOQ code from database
mvn jooq:generate -pl server

# Build script (compiles and prepares release)
./build.sh

# Run locally with optimized JVM settings
./run.sh
```

### Frontend Web Development (Next.js)
```bash
cd web/

# Development
npm run dev          # Start dev server (http://localhost:3000)
npm run build        # Build for production
npm run start        # Start production server
npm run lint         # Run linting
```

## Architecture Overview

### Multi-Module Maven Structure
- **api/sdk/**: Protocol definitions, DTOs, client interfaces
- **api/spi/**: Authentication and session management (OAuth, CAS)
- **api/server/**: Main Spring Boot application with REST endpoints

### Protocol Adapter Pattern
The core uses **AdaptorManager** to route requests to different AI providers:
- `IProtocolAdaptor` interface for each provider (OpenAI, AWS Bedrock, Alibaba Cloud, etc.)
- Adapters organized by endpoint type (completion, embedding, tts, asr)
- Channel-based routing with load balancing and cost optimization

### Key Request Flow
1. **Endpoint Controller** receives HTTP request
2. **Interceptors** handle auth, quotas, safety checks, concurrent limits
3. **Channel Router** selects provider based on load balancing/cost
4. **Protocol Adapter** transforms request to provider format
5. **Cost Handler** calculates and records usage metrics
6. **Response Processing** handles streaming/non-streaming responses

### Database & Caching
- **jOOQ** for database access with code generation
- **Multi-level caching**: Local (Caffeine) + Distributed (Redis/Redisson)
- **High-performance logging**: Disruptor-based async processing
- **Distributed rate limiting**: Redis + Lua scripts with sliding window

## Key Configuration Files

- `api/server/src/main/resources/application.yml` - Main backend config
- `web/src/config.ts` - Frontend API configuration
- `docker-compose.yml` - Multi-service orchestration
- Database initialization: `api/server/sql/*.sql`

## Important Directories

### Backend
- `api/server/src/main/java/com/ke/bella/openapi/endpoints/` - REST controllers
- `api/server/src/main/java/com/ke/bella/openapi/protocol/` - Provider adapters
- `api/server/src/main/java/com/ke/bella/openapi/intercept/` - Request interceptors
- `api/server/src/main/resources/lua/` - Redis Lua scripts for rate limiting

### Frontend
- `web/src/app/` - Next.js App Router pages
- `web/src/app/playground/v1/` - API testing interfaces (chat, audio, embeddings)
- `web/src/components/` - React components (feature-organized)
- `web/src/lib/api/` - API client functions

## Multi-tenant Architecture

- **Spaces**: Provide tenant isolation with separate API keys and configurations
- **API Keys**: Hierarchical structure with quotas and permissions
- **Models**: Define available AI models per provider with feature capabilities
- **Channels**: Provider instances with specific configurations

## Development Notes

### Adding New AI Providers
1. Implement `IProtocolAdaptor` interface in `api/server/src/main/java/com/ke/bella/openapi/protocol/`
2. Add protocol-specific DTOs in `api/sdk/src/main/java/com/ke/bella/openapi/protocol/`
3. Register adapter with `AdaptorManager`
4. Update frontend components in `web/src/components/` if needed

### Testing & Quality
Backend tests need to connect mysql database to run tests, so can't run backend tests in claude.
```bash
# Run backend tests
cd api/
mvn test                           # Run all tests
mvn test -pl server               # Run server module tests only
mvn test -Dtest=ChatControllerTest # Run specific test class

# Run frontend tests
cd web/
npm run lint                      # Run ESLint linting
```
- API tests in `api/server/src/test/resources/`
- Test configuration: `api/server/src/test/resources/application-ut.yml`
- Mock implementations available for each protocol adapter

### Environment Management
- Supports dev/test/prod environments with profile-specific configs
- Apollo configuration center integration (optional)
- Extensive proxy and OAuth configuration options

## Database & Code Generation

### jOOQ Code Generation
```bash
cd api/
mvn jooq:generate -pl server       # Generate jOOQ code from database schema
```
- Generated POJOs and Records in `api/server/src/codegen/java/`
- Database schema initialization scripts in `api/server/sql/`
- Re-run code generation after database schema changes

## Debugging & Troubleshooting

### Common Issues
- **Protocol Adapter Problems**: Check `AdaptorManager` registration and provider-specific logs
- **Rate Limiting Issues**: Examine Redis Lua script execution in `api/server/src/main/resources/lua/`
- **Database Connection Issues**: Verify Docker MySQL container health and connection settings
- **Frontend Development Mode**: Use `cd web/ && npm run dev` for hot reload (avoid Docker dev mode)

### Useful Debugging Settings
```yaml
# In application.yml for debugging
logging.level.org.jooq.tools.LoggerListener: DEBUG  # SQL logging
logging.level.com.ke.bella.openapi.intercept: DEBUG # Request/Response logging
jetcache.statIntervalMinutes: 1                     # Cache statistics
```

### always response with chinese