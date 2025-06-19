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

### Docker
```bash
docker build -t bella-openapi-web .
docker run -p 3000:3000 bella-openapi-web
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
- `NEXT_PUBLIC_API_HOST` - Backend API host
- `WORKFLOW_URL`, `WORKFLOW_API_KEY` - Workflow service configuration
- `ES_URL`, `ES_API_KEY` - ElasticSearch for logging
- `TENANT_ID` - Multi-tenant identifier
- Various workflow IDs for metrics, logs, and services

### Key Patterns
- **Provider Pattern**: Global state via UserProvider and ThemeProvider
- **Custom Axios Instance**: Centralized API client with authentication interceptors
- **Feature-based Components**: Components organized by feature area
- **Type-safe APIs**: Comprehensive TypeScript types for all API interactions
- **shadcn/ui Integration**: Consistent design system with Radix UI primitives

### Path Aliases
- `@/*` maps to `./src/*` for clean imports

## Development Notes

- The main entry point routes to `/meta` which serves as the primary API console interface
- Audio features support real-time processing and various audio formats
- All API interactions go through the custom Axios instance with automatic authentication
- The application uses React Server Components where applicable
- Dark/light mode support is built-in via next-themes