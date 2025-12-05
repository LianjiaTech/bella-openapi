# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **Bella OpenAPI Web** - a Next.js 14 web application that serves as an API management and testing console for OpenAPI endpoints. It provides a comprehensive interface for testing, monitoring, and managing various AI/ML APIs including chat completions, audio processing, and embeddings.

## Common Commands

### Development
```bash
npm run dev          # Start development server (http://localhost:3000)
npm run build        # Build for production
npm run start        # Start production server
npm run lint         # Run ESLint linting
```

### Docker (Multi-stage build with optimizations)
```bash
# Build with build args for API host
docker build --build-arg NEXT_PUBLIC_API_HOST=your-api-host -t bella-openapi-web .
docker run -p 3000:3000 bella-openapi-web

# Development with volume mounting
docker run -v $(pwd):/app/web -p 3000:3000 bella-openapi-web
```

## Architecture Overview

### Technology Stack
- **Framework**: Next.js 14 with App Router and React Server Components
- **Language**: TypeScript with strict type checking
- **Styling**: Tailwind CSS + shadcn/ui components (New York style)
- **State Management**: React Context API with custom providers
- **HTTP Client**: Axios with custom interceptors and authentication
- **Forms**: React Hook Form with resolvers
- **Data Tables**: TanStack React Table
- **Charts**: Recharts for data visualization

### Key Directory Structure
```
/src
├── app/                    # Next.js App Router
│   ├── api/               # API routes (logs, metrics, config)
│   ├── playground/v1/     # API testing interfaces
│   │   ├── audio/         # Audio processing APIs
│   │   ├── chat/          # Chat completion APIs
│   │   └── embeddings/    # Embedding APIs
│   ├── apikey/            # API key management
│   ├── monitor/           # Monitoring dashboard
│   ├── meta/              # API metadata & main console
│   └── logs/              # Logging interface
├── components/            # React components (feature-organized)
├── lib/                   # Utilities and configurations
│   ├── api/              # API client functions
│   ├── context/          # React contexts (UserProvider, etc.)
│   └── types/            # TypeScript definitions
└── hooks/                # Custom React hooks
```

### Core Features
- **API Console**: Browse and test OpenAPI endpoints via `/meta`
- **Playground**: Interactive testing for Chat, Audio, and Embedding APIs
- **API Key Management**: Create and manage API keys with quotas
- **Real-time Monitoring**: Metrics dashboard and comprehensive logging
- **Multi-tenant Architecture**: Configurable tenant and workflow support

### Configuration & Environment
The application uses Next.js standalone output mode and requires these environment variables:

**Public (Build-time)**:
- `NEXT_PUBLIC_API_HOST` - Backend API host
- `NEXT_PUBLIC_APIKEY_QUOTA_APPLY_URL` - URL for API key quota applications
- `NEXT_PUBLIC_SAFETY_APPLY_URL` - URL for safety feature applications
- `NEXT_PUBLIC_AGENT_URL` - Agent service URL

**Server-side (Runtime)**:
- `WORKFLOW_URL`, `WORKFLOW_API_KEY` - Workflow service configuration
- `ES_URL`, `ES_API_KEY` - ElasticSearch for logging
- `TENANT_ID` - Multi-tenant identifier
- `METRICS_WORKFLOW_ID`, `LOGS_TRACE_WORKFLOW_ID`, `SERVICE_WORKFLOW_ID` - Workflow identifiers

### Key Patterns
- **Provider Pattern**: Global state via UserProvider and ThemeProvider contexts
- **Custom Axios Instance**: Centralized API client with authentication and error handling interceptors  
- **Feature-based Components**: Components organized by feature area (apikey/, meta/, playground/, etc.)
- **Type-safe APIs**: Comprehensive TypeScript types for all API interactions
- **shadcn/ui Integration**: Consistent design system with Radix UI primitives (New York style)
- **Standalone Output**: Docker-optimized production builds with PM2 process management

### Key Architecture Components

**Authentication Flow**:
- `src/lib/context/user-context.tsx` - Global user state management
- `src/lib/api/openapi.ts` - Axios instance with 401 redirect handling
- `X-BELLA-CONSOLE` header for backend identification

**API Client Structure**:
- `src/lib/api/openapi.ts` - Main HTTP client with interceptors
- `src/lib/api/meta.ts` - Metadata and model management APIs
- `src/lib/api/apikey.ts` - API key management functions
- `src/lib/api/workflow.ts` - Workflow service integration

**Component Organization**:
- Feature-based: `components/apikey/`, `components/meta/`, `components/playground/`
- UI primitives: `components/ui/` (shadcn/ui components)
- Business logic: Audio processing classes in `components/playground/`

### Path Aliases
- `@/*` maps to `./src/*` for clean imports

## Development Notes

### Key Routes & Pages
- Root (`/`) redirects to `/meta` - the main API console interface
- `/playground/v1/` - Interactive API testing interfaces (chat, audio, embeddings)  
- `/apikey/` - API key management and quota tracking
- `/monitor/` - Real-time metrics and monitoring dashboard
- `/logs/` - Comprehensive logging interface with trace capabilities

### Audio Processing Architecture
- Real-time audio processing with WebSocket connections
- Multiple recorder implementations: `RealtimeAudioRecorder`, `FlashAudioRecorder`
- PCM audio playback and device selection utilities
- Voice synthesis and ASR (Automatic Speech Recognition) support

### API Response Handling
- Standardized response format with `code: 200` success indicator
- Automatic login redirect on 401 responses via `X-Redirect-Login` header
- Error boundary handling throughout the application

### Deployment Features
- Multi-stage Docker build with Chinese mirror optimizations
- PM2 process management in production
- Standalone Next.js output for optimized Docker images
- Timezone configuration (Asia/Shanghai) in containers