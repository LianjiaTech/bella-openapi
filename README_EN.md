# Bella OpenAPI

[![Static Badge](https://img.shields.io/badge/docs-more-yellow?style=flat-square)](https://doc.bella.top/en)
[![Static Badge](https://img.shields.io/badge/wiki-deep-blue?style=flat-square)](https://deepwiki.com/LianjiaTech/bella-openapi)

English | [中文](./README.md) | [learmore](https://doc.bella.top/en)

Bella OpenAPI is an API gateway providing rich AI capabilities, comparable to openrouter. Unlike openrouter, in addition to chat-completion capabilities, it also offers text-embedding, automatic speech recognition (ASR), text-to-speech (TTS), text-to-image, image-to-image, and many other AI capabilities, while integrating billing, rate limiting, and resource management functionalities. All integrated capabilities have been validated in large-scale production environments.

- We have deployed an online experience version of Bella OpenAPI. You can log in and access [Bella OpenAPI](https://api.bella.top)
- For quick startup and service experience, please read: [Quick Start](#quick-start)
- To understand detailed environment variable configuration and startup deployment details, please read: [Configuration Details](https://bella-top.github.io/bella-docs/en/docs/configuration-details) and [Startup and Deployment Details](https://bella-top.github.io/bella-docs/en/docs/startup-deployment-details)

## Table of Contents

- [Core Functionalities](#core-functionalities)
  - [AI Capabilities](#ai-capabilities)
  - [Metadata Management](#metadata-management)
  - [Unified Login Service](#unified-login-service)
  - [Billing and Rate Limiting](#billing-and-rate-limiting)
- [System Advantages](#system-advantages)
  - [Large-scale Production Environment Validation](#large-scale-production-environment-validation)
  - [Rich Capabilities](#rich-capabilities)
  - [Excellent Features](#excellent-features)
  - [Unified Metadata Management](#unified-metadata-management)
  - [High-performance Cache Design](#high-performance-cache-design)
  - [High-performance Log Processing Framework](#high-performance-log-processing-framework)
  - [Efficient Distributed Rate Limiting](#efficient-distributed-rate-limiting)
  - [Unified Error Handling](#unified-error-handling)
  - [Secure and Reliable](#secure-and-reliable)
  - [Scalability](#scalability)
  - [Java-friendly Technology Stack](#java-friendly-technology-stack)
  - [Convenient Experience](#convenient-experience)
- [Quick Start](#quick-start)
- [FAQ and Solutions](#faq-and-solutions)
- [Related Documentation](#related-documentation)

## Core Functionalities

### AI Capabilities

- **Text Processing**
  - **Chat Completion**: Provides conversational completion capability
  - **Text Embedding**: Generates vector representations of text for semantic search and similarity calculations

- **Voice Services**
  - **Real-time Speech Recognition**: Supports streaming speech recognition and one-shot speech recognition for real-time interaction scenarios
  - **Audio Transcription**: Supports uploading audio files for offline transcription
  - **Text-to-Speech**: Converts text to natural voice output, with streaming support
  - **Real-time Conversation**: Supports users' real-time voice input via microphone, automatically recognizes speech content, calls the large model to generate replies, and converts the reply content to speech output

- **Image Services**
  - **Image-to-Image**: Edits images to create new images (coming soon)
  - **Text-to-Image**: Generates images from text descriptions (coming soon)

### Metadata Management

- **Multi-level Structure**: Adopts a Category-Endpoint-Model-Channel four-layer structure
  - **Category**: Top-level classification of API services, such as voice services, text services, etc.
  - **Endpoint**: Specific API functionality entry points, such as real-time speech recognition, chat completion, etc.
  - **Model**: AI models supporting each endpoint, such as different speech recognition models, large language models, etc.
  - **Channel**: Specific service provider implementations, including vendor, protocol, and configuration information

- **Flexible Routing Mechanism**: Intelligently selects the most appropriate service channel based on user requests and configuration
- **Visual Management Interface**: Provides an intuitive web interface for metadata configuration and management

### Unified Login Service

- **Multiple Authentication Methods**: Supports OAuth 2.0, CAS single sign-on, and API Key authentication
- **Session Management**: Redis-based distributed session storage
- **User Permissions**: Fine-grained permission control and management

### Billing and Rate Limiting

- **API Key Management**: Supports hierarchical API Key structure
- **Quota Control**: Manages API usage by monthly quotas
- **Rate Limiting Mechanism**: Redis-based distributed rate limiting implementation

## System Advantages

### Large-scale Production Environment Validation

- **Extreme Stability**: Already serving all Beike's business lines, with daily average of 150 million API calls, withstanding large-scale production environment testing
- **Rich Business Scenarios**: Wide coverage of business scenarios, adaptable to most production environment situations
- **Extensive Use Cases**: Already serving all Beike's business lines, withstanding large-scale production environment testing

### Rich Capabilities

- **Comprehensive AI Capabilities**: Supports chat completion, text embedding, speech recognition (real-time, offline, one-shot), text-to-speech (with streaming support), text-to-image, image-to-image, and many other AI capabilities
- **Mock Capability**: Built-in capability point mocking functionality, useful for unit testing and stress testing

### Excellent Features

- **Function Call Support**: Extends function call features to LLMs that don't natively support function calls
- **Routing Strategy**: Excellent routing strategy ensures maximum processing capacity for capability channels during peak periods
- **Request Queue**: Supports queue features ensuring orderly processing of requests during peak periods
- **Backup Models**: Supports backup model mechanisms (coming soon) to improve service availability
- **Maximum Wait Time**: Supports setting maximum wait times (coming soon) to optimize user experience

### Unified Metadata Management

- **Flexible Multi-level Structure**: Adopts Category-Endpoint-Model-Channel four-layer structure, giving the system high scalability and flexibility
- **Centralized Configuration**: All API service configurations are centrally managed for easy maintenance and monitoring
- **Dynamic Routing**: Intelligently selects the most appropriate service channel based on user requests and configuration to improve service quality

### High-performance Cache Design

- **Multi-level Cache Architecture**: Combines Redisson, Caffeine, and JetCache to implement collaborative work between local cache and distributed cache
- **High Throughput**: Local cache reduces network overhead, distributed cache ensures cluster consistency
- **Automatic Expiration Mechanism**: Intelligent cache expiration strategy balances data consistency and performance

### High-performance Log Processing Framework

- **Disruptor-based Asynchronous Processing**: Uses high-performance Disruptor ring buffer to implement asynchronous processing of log events, significantly reducing system latency
- **Multi-processor Parallel Architecture**: Supports multiple event processors working in parallel, simultaneously handling billing, metrics collection, and rate limiting
- **Lock-free Design**: Uses lock-free queues and SleepingWaitStrategy, reducing thread contention and increasing throughput
- **Elegant Exception Handling**: Integrates dedicated exception handlers to ensure log processing errors don't affect the main business process

### Efficient Distributed Rate Limiting and Channel Performance Monitoring

- **Low Coupling**: Implemented based on logs, decoupled from main logic; each capability point only needs to report logs according to rules to customize performance monitoring dimensions
- **Lua Script-based Atomic Operations**: Uses Redis+Lua scripts to implement distributed rate limiting, ensuring atomicity and consistency
- **Sliding Window Algorithm**: Uses precise sliding window algorithm for rate limiting and monitoring, reducing storage resource usage
- **Multi-level Cache Design**: Combines local cache and distributed cache to optimize rate limiting performance and reduce network overhead
- **Concurrent Request Control**: For trial API keys, precisely tracks and controls the number of concurrent requests for each API key, preventing excessive resource occupation
- **Automatic Expiration Mechanism**: Intelligently sets expiration times to avoid resource leaks and ensure long-term stable system operation

### Unified Error Handling

- **Consistent User Experience**: Unified error prompt format and display method
- **Fine-grained Error Classification**: Distinguishes between service unavailability and other error types, providing more precise feedback
- **Friendly Error Prompts**: Uses different visual styles for different error types to improve user experience

### Secure and Reliable

- **Multi-level Authentication**: Supports OAuth 2.0, CAS single sign-on, and API Key authentication
- **Fine-grained Permission Control**: Role-based access control ensures resource security
- **Complete Audit Logs**: Records key operations for tracking and troubleshooting
- **Production Environment Validation**: Verified in large-scale production environments, stable and reliable

### Scalability

- **Microservice Architecture**: Spring Boot-based microservice design for horizontal scaling
- **Containerized Deployment**: Docker and Docker Compose support, simplifying deployment and expansion
- **Third-party Service Integration**: Flexible channel mechanism for easy integration with various AI service providers

### Java-friendly Technology Stack

- **Spring Boot Ecosystem**: Based on Spring Boot framework, friendly to Java developers
- **Rich Toolchain**: Integrates common Java development tools and libraries
- **Comprehensive Documentation**: Provides detailed API documentation and development guides

### Convenient Experience

- **Free Cloud Experience Service**: You can log in and access [Bella OpenAPI](https://api.bella.top) to directly experience all capabilities
- **Docker Startup Without Compilation**: Provides convenient startup method, automatically pulls images through startup script, no compilation required
- **Convenient Startup Configuration**: Startup script provides rich startup parameters, you can configure as needed without modifying configuration files

## Quick Start

This project uses Docker Compose to start all services, including backend API, frontend Web, MySQL, and Redis.

### Prerequisites

- Install [Docker](https://www.docker.com/get-started)
- Install [Docker Compose](https://docs.docker.com/compose/install/)
- Execution directory must be in the root directory of the bella-openapi project

### Starting the Service

If the image doesn't exist locally, it will pull from the remote repository

```bash
./start.sh 
./start.sh --github-oauth CLIENT_ID:CLIENT_SECRET --google-oauth CLIENT_ID:CLIENT_SECRET --server URL (configure oauth login service and service domain through startup parameters)
```
Note: If you need to configure user login, see: [OAuth Configuration](https://bella-top.github.io/bella-docs/en/docs/configuration-details#oauth-configuration)

- After starting the service, it will automatically check if a system ak exists; if not, it will generate a system ak and administrator authorization
- If you don't want to perform administrator authorization at startup (it will still check if the system ak needs to be generated), you can use the `--skip-auth` parameter:

```bash
./start.sh --skip-auth
```

Common options:
- `-b, --build`: Rebuild services after code modification
- `--github-oauth CLIENT_ID:CLIENT_SECRET`: Configure GitHub OAuth login
- `--google-oauth CLIENT_ID:CLIENT_SECRET`: Configure Google OAuth login
- `--server URL`: Configure service domain
- `--skip-auth`: Skip administrator authorization process

For more detailed startup options and configuration instructions, please refer to [Startup and Deployment Details](https://bella-top.github.io/bella-docs/en/docs/startup-deployment-details).

## FAQ and Solutions

### 1. Frontend compilation fails when using development environment with the startup script

Next.js has implemented preloading optimizations in the dev environment. If you must use the dev environment, it's recommended to use next.dev to start the web service separately.

### 2. Initialize and start the system, clearing existing data (development, testing environment)

1. Delete database: `docker exec -it bella-openapi-mysql mysql -uroot -p123456 -e "drop database bella_openapi;"` (replace with your username and password if they're different from default)
2. Stop services: `docker-compose down`
3. Delete MySQL data: `rm -rf ./mysql`
4. Delete Redis data: `rm -rf ./redis`
5. Rebuild and restart: `./start.sh -b`

## Related Documentation

- [Configuration Details](https://bella-top.github.io/bella-docs/en/docs/configuration-details) - Detailed introduction to environment variable configuration, database configuration, cache configuration, Apollo configuration, and login service configuration
- [Startup and Deployment Details](https://bella-top.github.io/bella-docs/en/docs/startup-deployment-details) - Detailed introduction to starting services, environment variable configuration, service management, and system initialization

---
*Last updated: 2025-03-31*