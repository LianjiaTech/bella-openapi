#!/bin/bash

echo "停止 Bella OpenAPI 服务..."

# 停止服务
docker-compose stop api web

echo "服务已停止"
