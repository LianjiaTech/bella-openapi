# Bella OpenAPI Startup and Deployment Details

This document provides detailed information about the startup and deployment process of Bella OpenAPI, including prerequisites, service startup, environment variable configuration, service management, and system initialization.

## Table of Contents

- [Project Overview](#project-overview)
  - [Project Structure](#project-structure)
  - [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Starting the Service](#starting-the-service)
  - [Startup Options](#startup-options)
  - [Startup Examples](#startup-examples)
- [Docker Compose Environment Variable Configuration](#docker-compose-environment-variable-configuration)
  - [Environment Variable Priority](#environment-variable-priority)
  - [Web Build-time Environment Variables](#web-build-time-environment-variables)
  - [Web Runtime Environment Variables](#web-runtime-environment-variables)
- [Service Management](#service-management)
  - [Stopping the Service](#stopping-the-service)
  - [Viewing Logs](#viewing-logs)
  - [Restarting Services](#restarting-services)
- [System Initialization](#system-initialization)
  - [Generating System API Key](#generating-system-api-key)
    - [Usage](#usage)
    - [Script Features](#script-features)
    - [Important Notes](#important-notes)
    - [Example Output](#example-output)
  - [Authorizing Administrators](#authorizing-administrators)
    - [Authorization Process](#authorization-process)
    - [Manual Authorization](#manual-authorization)
- [Docker Image Management](#docker-image-management)
  - [Pushing Docker Images](#pushing-docker-images)
    - [Building and Pushing Images](#building-and-pushing-images)
    - [Deploying on Production Servers](#deploying-on-production-servers)
    - [Using Specific Image Versions](#using-specific-image-versions)
    - [Important Considerations](#important-considerations)

## Project Overview

Bella OpenAPI is a comprehensive AI open API platform providing the following main components:

- **Project Structure**:
  - `api/`: Backend API service based on the Spring Boot framework
  - `web/`: Frontend web application based on React and Next.js

- **Technology Stack**:
  - Backend: Java, Spring Boot, MySQL, Redis
  - Frontend: React, Next.js
  - Deployment: Docker, Docker Compose (requiring Docker Compose version 1.13.0+)
  - Gateway: Nginx (optional, automatically created in the container when using the startup script)

## Prerequisites

- Install [Docker](https://www.docker.com/get-started)
- Install [Docker Compose](https://docs.docker.com/compose/install/)
- Commands must be executed from the root directory of the bella-openapi project

## Starting the Service

When starting the service, you typically need to configure user login. See: [GitHub OAuth Configuration Guide](./configuration-details.md#github-oauth-configuration-guide)

```bash
./start.sh [options] (if no local image exists, it will pull from remote repository)
```
Note: On Windows, please use Git Bash to execute commands

Options:
- `-b, --build`: Rebuild services
- `-r, --rebuild`: Force rebuild services (without using cache)
- `-e, --env ENV`: Specify environment (dev, test, prod)
- `-h, --help`: Display help information
- `--skip-auth`: Skip authorization step
- `--server URL`: Configure service domain name
- `--github-oauth CLIENT_ID:CLIENT_SECRET`: Configure GitHub OAuth login
- `--google-oauth CLIENT_ID:CLIENT_SECRET`: Configure Google OAuth login
- `--cas-server URL`: Configure CAS server URL
- `--cas-login URL`: Configure CAS login URL
- `--proxy-host HOST`: Configure proxy server hostname or IP address
- `--proxy-port PORT`: Configure proxy server port
- `--proxy-type TYPE`: Configure proxy type (socks or http)
- `--proxy-domains DOMAINS`: Configure domains that need to be accessed through the proxy, separate multiple domains with commas
- `--version VERSION`: Specify image version
- `--push`: Push image to repository after building
- `--registry username`: Specify Docker repository (username)
- `--restart-web`: Restart only the frontend service
- `--restart-api`: Restart only the backend service
- `--nginx-port PORT`: Specify the port to which Nginx service is mapped, default is 80
- `--update-image`: Update image from remote repository, even if it exists locally
- `--service CONTAINER_NAME:DOMAIN:PORT`: Configure Nginx forwarding for additional services, format is service-name:domain:port, separate multiple services with commas, only supports services in the same Docker network, using CONTAINER_NAME for forwarding

Examples:
```bash
./start.sh                       # Start service without rebuilding, pulls remote image if no local image exists
./start.sh -b                    # Start service and rebuild (uses cache, faster for incremental modifications)
./start.sh -r                    # Start service and force rebuild (without using cache)
./start.sh -e test               # Start service in test environment
./start.sh --skip-auth           # Start service but skip authorization step
./start.sh --server http://example.com # Configure service domain name
./start.sh --proxy-host 127.0.0.1 --proxy-port 8118 --proxy-type http --proxy-domains github.com,google.com # Configure HTTP proxy
./start.sh --proxy-host proxy.com --proxy-port 80 --proxy-type socks # Configure SOCKS proxy without specifying domains

# If you haven't modified the source code and just want to pull the remote image and configure login methods and service domain, you can usually use the following commands:
# Without domain configuration, typically for local deployment:
./start.sh --github-oauth ${clientId}:${secret} --google-oauth ${clientId}:${secret} # Configure GitHub and Google OAuth login

# With domain configuration, typically for server deployment:
./start.sh --github-oauth ${clientId}:${secret} --google-oauth ${clientId}:${secret} --server http://example.com # Configure GitHub and Google OAuth login and service domain

# With domain and proxy configuration, for environments that need to access external services through a proxy:
./start.sh --github-oauth ${clientId}:${secret} --google-oauth ${clientId}:${secret} --server http://example.com --proxy-host 127.0.0.1 --proxy-port 8118 --proxy-type http --proxy-domains github.com,google.com

./start.sh --cas-server https://cas.example.com --cas-login https://cas.example.com/login --server http://example.com # Configure CAS login and service domain; if both CAS and OAuth login are configured, CAS login will be used
```

## Docker Compose Environment Variable Configuration

### Environment Variable Priority

In Docker environments, environment variables have the following priority from highest to lowest:
1. environment settings in docker-compose.yml
2. ENV directives in Dockerfile
3. .env files, yaml files

### Web Build-time Environment Variables

Build-time environment variables are passed to Dockerfile through the args section of docker-compose.yml:

```yaml
build:
  context: ./web
  args:
    - NODE_ENV=development
    - DEPLOY_ENV=development
    - SKIP_INSTALL=false
```

These variables affect the application build process. For example, Next.js will load different environment configuration files (.env.development, .env.production, etc.) based on the NODE_ENV value.

### Web Runtime Environment Variables

Runtime environment variables are set through the environment section of docker-compose.yml:

```yaml
environment:
  - NODE_ENV=development
  - DEPLOY_ENV=development
```

These variables take effect when the container is running, but for pre-built static files, only environment variables starting with NEXT_PUBLIC_ can be accessed on the client side.

## Service Management

### Stopping the Service

```bash
./stop.sh
```
Or directly use Docker Compose command:

```bash
docker-compose down
```

### Viewing Logs

```bash
# View logs for all services
docker-compose logs

# View logs for specific services
docker-compose logs api  # Backend API service
docker-compose logs web  # Frontend web application
docker-compose logs mysql
docker-compose logs redis

# View logs in real-time
docker-compose logs -f
```

### Restarting Services

You can use the start.sh script to restart specific services:

```bash
# Restart frontend service
./start.sh --restart-web

# Restart backend service
./start.sh --restart-api --github-oauth ${clientId}:${secret} --google-oauth ${clientId}:${secret} --server http://example.com
```

You can also use Docker Compose commands directly:

```bash
# Restart frontend service
docker-compose restart web

# View frontend logs
docker-compose logs -f web
```

## System Initialization

### Generating System API Key

During system initialization, a system-level API Key needs to be generated for managing and accessing all API resources. We provide a script to automatically generate the system API Key and write it to the database.
The ./start.sh script will automatically generate this when starting the service. If you need to generate it manually, use the ./generate-system-apikey.sh script.

#### Usage

##### Execute Directly in Docker Environment
./generate-system-apikey.sh

##### Use Custom Database Connection Parameters
DB_USER=bella_user DB_PASS=123456 ./generate-system-apikey.sh

#### Script Features

1. Check if a system API Key already exists in the database
2. If it doesn't exist, generate a new system API Key and write it to the database
3. If it already exists, display information about the existing system API Key
4. Save the API Key information to the `system-apikey.txt` file

#### Important Notes

- The system API Key has the highest privileges; please keep it secure
- The API Key is displayed only once when generated; afterward, only a masked version can be seen
- If you lose the API Key, you need to generate a new one; if it's a system AK, you need to invalidate it and generate a new one
- The generated API Key has the `all` role and can access all endpoints
- The script needs to run in a Docker environment and will automatically connect to the MySQL container

#### Example Output

```
Generating system API key...
Checking if system API key already exists in the database...
Inserting new system API key into the database...
Successfully generated and inserted system API key into the database.
API Key Code: ak-026e84f5-8a1c-4243-a800-d44581f0f1b7
API Key: 9be9d54d-d4ae-4510-8819-a62a4e69e57b
API Key SHA-256: d58ed6447aa8da22d6fa3064d242f8f8dd74a6df4a1663084f4003b2d559b9ea
API Key Display: 9b****e57b
API key details have been saved to system-apikey.txt
Important: Please keep this information secure, the API key will only be displayed once!
Completed.
```

### Authorizing Administrators

After system initialization, administrator users need to be authorized. Administrator users can manage API Keys, user permissions, and other system resources.

#### Authorization Process

1. First, start the service and generate a system API Key
   ```bash
   ./start.sh
   ```
   If a new system API Key is generated, it will automatically enter the administrator authorization process. If you don't want to authorize administrators during startup (system will still check if a system API Key needs to be generated), you can use the `--skip-auth` parameter:
   ```bash
   ./start.sh --skip-auth
   ```
   
   Note: If an API Key already exists in the system, the script will automatically skip the administrator authorization step. Only when generating a new API Key for the first time will it ask whether you need to authorize an administrator.

2. The startup script will ask if you need to authorize an administrator
   - If you choose "yes", the script will guide you through the entire authorization process
   - If you choose "no", you can manually run the authorization script later
   - If you used the `--skip-auth` parameter, the inquiry step will be skipped

3. Follow the prompts to log in to the frontend page to get the user ID or email
   - Visit http://localhost:3000
   - Log in using a third-party account (such as Google, GitHub, etc.)
   - Click on the avatar in the upper right corner to view personal information and get the user ID or email

4. The authorization script will start automatically; follow the prompts to enter user information
   - You can choose to authorize using user ID or email
   - Follow the script prompts to enter relevant information

5. If you need to authorize an administrator later, you can run:
   ```bash
   ./authorize-admin.sh
   ```
6. After authorization, click on the avatar to log out of the system and log in again to have administrator privileges

#### Manual Authorization

You can also use curl commands to authorize manually:

##### Authorize Using User ID
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

##### Authorize Using Email
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

## Docker Image Management

### Pushing Docker Images

To facilitate deployment in production environments, you can build and push Docker images to Docker Hub or other Docker repositories locally, and then directly pull and start these images on the server without having to build them on the server.

#### Building and Pushing Images

```bash
# Build and push images to Docker Hub
./start.sh --build --push --registry username --version v1.0.0
```

Parameter explanation:
- `--build`: Build Docker images
- `--push`: Push images to repository after building
- `--registry username`: Specify Docker Hub username
- `--version v1.0.0`: Specify image version number (default is v1.0.0)

After executing this command, the script will:
1. Build Docker images for API and Web services
2. Tag the images as `username/bella-openapi-api:v1.0.0` and `username/bella-openapi-web:v1.0.0`
3. Also tag them as `username/bella-openapi-api:latest` and `username/bella-openapi-web:latest`
4. Push these images to Docker Hub

#### Deploying on Production Servers

On production servers, you can directly use the pushed images to start services:

```bash
# Pull and start pushed images
./start.sh --registry username --version v1.0.0
```

This will pull the specified version of images from Docker Hub and start the services without having to build them on the server, greatly reducing deployment time and resource consumption.

#### Using Specific Image Versions

If you need to use a specific version of images, you can specify it using the `--version` parameter:

```bash
# Use a specific version of images and configure your own login options
./start.sh --registry username --version v1.1.0 --github-oauth CLIENT_ID:CLIENT_SECRET --google-oauth CLIENT_ID:CLIENT_SECRET --server http://example.com
```

#### Important Considerations

1. Make sure you are logged in to Docker Hub:
   ```bash
   docker login
   ```

2. Before pushing images, ensure you have sufficient permissions to access the specified Docker Hub repository

3. In production environments, it is recommended to use specific version numbers rather than the `latest` tag to ensure consistency and traceability of deployments

4. If you are using a private Docker repository, adjust the `--registry` parameter accordingly

---
*Last updated: 2025-03-31*