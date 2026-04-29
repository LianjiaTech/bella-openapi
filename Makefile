# Makefile for Bella OpenAPI
#
# 职责:
# - 提供统一的项目管理命令入口
# - 简化 Docker 测试环境的使用
# - 支持常见的开发和测试场景
#
# 设计说明:
# 1. 测试命令分为本地测试和 Docker 测试两类
# 2. Docker 测试支持缓存管理和重建选项
# 3. 使用 .PHONY 标记所有非文件目标
# 4. 提供清晰的帮助信息

.PHONY: help test test-local test-docker test-docker-cas test-docker-rebuild test-docker-clean-cache test-docker-clean-volumes docker-test-status docker-clean-all

# 默认目标:显示帮助信息
help:
	@echo "Bella OpenAPI - 可用命令"
	@echo ""
	@echo "测试相关:"
	@echo "  make test                      宿主机运行 Playwright 测试(使用本地 Chrome)"
	@echo "  make test-local                容器内运行 Playwright 测试(使用容器 Chromium,连接宿主机 dev server)"
	@echo "  make test-docker               Docker 环境运行测试(完整环境,使用缓存)"
	@echo "  make test-docker-cas           Docker 环境运行测试(CAS 登录模式)"
	@echo "  make test-docker-rebuild       Docker 环境运行测试(强制重建镜像)"
	@echo "  make test-docker-clean-cache   清除 Docker 构建缓存"
	@echo "  make test-docker-clean-volumes 清除 node_modules 卷并重新安装依赖"
	@echo "  make test-docker-status        显示 Docker 测试环境缓存状态"
	@echo ""
	@echo "Docker 管理:"
	@echo "  make docker-clean-all          清除所有 Docker 资源(镜像+卷+缓存)"
	@echo ""
	@echo "通用:"
	@echo "  make help                      显示此帮助信息"

# ============================================================
# 测试命令
# ============================================================

# 宿主机运行 Playwright 测试(使用本地 Chrome)
test:
	@echo "运行宿主机 Playwright 测试(使用本地 Chrome)..."
	npx playwright test

# 容器内运行 Playwright 测试(使用容器 Chromium,连接宿主机 dev server)
test-local:
	@echo "容器内运行 Playwright 测试(使用容器 Chromium)..."
	@echo "前置条件: 请先启动前端 dev server (cd web_v2 && npm run dev)"
	@echo ""
	docker-compose -f tests/docker-compose.local.yml run --rm playwright-local

# Docker 环境运行测试(使用缓存,推荐)
test-docker:
	@echo "Docker 环境运行测试(使用缓存)..."
	./tests/run-docker-tests.sh

# Docker 环境运行测试(CAS 登录模式)
test-docker-cas:
	@echo "Docker 环境运行测试(CAS 登录模式)..."
	AUTH_MODE=cas ./tests/run-docker-tests.sh

# Docker 环境运行测试(强制重建镜像,不使用缓存)
test-docker-rebuild:
	@echo "Docker 环境运行测试(强制重建镜像)..."
	./tests/run-docker-tests.sh --rebuild

# 清除 Docker 构建缓存
test-docker-clean-cache:
	@echo "清除 Docker 构建缓存..."
	./tests/run-docker-tests.sh --clean-cache
	@echo "构建缓存已清除"

# 清除 node_modules 卷(重新安装依赖)
test-docker-clean-volumes:
	@echo "清除 node_modules 卷..."
	./tests/run-docker-tests.sh --clean-volumes
	@echo "node_modules 卷已清除,下次运行将重新安装依赖"

# 显示 Docker 测试环境状态
test-docker-status:
	@echo "Docker 测试环境缓存状态:"
	@echo ""
	@echo "1. node_modules 卷:"
	@if docker volume inspect tests_playwright_node_modules > /dev/null 2>&1; then \
		echo "   ✓ 已存在"; \
		docker volume inspect tests_playwright_node_modules --format '   大小: {{.Options}}' 2>/dev/null || echo "   (无法获取大小信息)"; \
	else \
		echo "   ✗ 不存在"; \
	fi
	@echo ""
	@echo "2. 测试镜像:"
	@if docker image inspect bella-openapi-playwright-test:latest > /dev/null 2>&1; then \
		echo "   ✓ 已存在"; \
		docker image inspect bella-openapi-playwright-test:latest --format '   大小: {{.Size}}' | awk '{print "   大小: " $$2/1024/1024 "MB"}'; \
	else \
		echo "   ✗ 不存在"; \
	fi
	@echo ""
	@echo "3. Docker 构建缓存:"
	@docker system df | grep "Build Cache" || echo "   无法获取缓存信息"

# ============================================================
# Docker 管理命令
# ============================================================

# 清除所有 Docker 测试资源(镜像+卷+缓存)
docker-clean-all:
	@echo "警告: 将清除所有 Docker 测试资源(镜像、卷、缓存)"
	@read -p "确认继续? [y/N] " confirm; \
	if [ "$$confirm" = "y" ] || [ "$$confirm" = "Y" ]; then \
		echo "停止并删除容器..."; \
		docker-compose -f tests/docker-compose.test.yml down || true; \
		echo "删除测试镜像..."; \
		docker rmi bella-openapi-playwright-test:latest || true; \
		echo "删除 node_modules 卷..."; \
		docker volume rm tests_playwright_node_modules || true; \
		echo "清除 Docker 构建缓存..."; \
		docker builder prune -f; \
		echo "✓ 所有 Docker 测试资源已清除"; \
	else \
		echo "操作已取消"; \
	fi

# ============================================================
# 高级用法示例(注释形式)
# ============================================================

# 运行特定测试文件:
#   ./tests/run-docker-tests.sh tests/seed.spec.ts

# 运行测试并显示浏览器:
#   ./tests/run-docker-tests.sh --headed

# 默认测试本地环境 (http://host.docker.internal:3000)
# 如需测试其他环境,可通过环境变量覆盖:
#   BASE_URL=http://bi.off.ke.com:3000 make test-docker
#   AUTH_MODE=cas CAS_USERNAME=<user> CAS_PASSWORD=<password> make test-docker

# 调试模式:
#   ./tests/run-docker-tests.sh --debug