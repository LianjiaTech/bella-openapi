#!/bin/bash

##############################################################################
# Playwright Docker 测试运行脚本
#
# 职责:
# - 提供便捷的测试命令接口
# - 支持自定义测试地址和 Playwright 参数
# - 自动构建/更新测试容器
# - 管理 Docker 缓存和命名卷
# - 输出测试报告到宿主机目录
#
# 设计说明:
# 1. 使用环境变量 BASE_URL 支持用户自定义测试地址
# 2. 支持传递任意 Playwright 参数(如 --headed, --debug 等)
# 3. 支持缓存管理选项(--rebuild, --clean-cache, --clean-volumes)
# 4. 自动处理容器清理,避免资源泄漏
# 5. 提供详细的缓存状态信息
#
# 使用示例:
#   ./tests/run-docker-tests.sh                                    # 使用默认地址运行所有测试(利用缓存)
#   ./tests/run-docker-tests.sh http://localhost:3000              # 指定测试地址
#   ./tests/run-docker-tests.sh --headed --debug                   # 使用默认地址并传递多个参数
#   ./tests/run-docker-tests.sh --rebuild                          # 强制重新构建镜像(不使用缓存)
#   ./tests/run-docker-tests.sh --clean-cache                      # 清除 Docker 构建缓存
#   ./tests/run-docker-tests.sh --clean-volumes                    # 清除 node_modules 卷(重新安装依赖)
##############################################################################

set -e

# 启用 Docker BuildKit 以支持缓存挂载
# BuildKit 缓存挂载可以在镜像重建时保留 npm/yarn/pnpm 下载的包
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认测试地址 - 使用 host 网络模式访问宿主机服务
# 注意: 测试容器使用 network_mode: host,因此 localhost 指向宿主机
DEFAULT_BASE_URL="http://localhost"
AUTH_MODE="${AUTH_MODE:-apikey}"

# 解析参数
BASE_URL=""
PLAYWRIGHT_ARGS=()
REBUILD=false
CLEAN_CACHE=false
CLEAN_VOLUMES=false
NO_CACHE=false

for arg in "$@"; do
  case "$arg" in
    --rebuild)
      REBUILD=true
      NO_CACHE=true
      ;;
    --clean-cache)
      CLEAN_CACHE=true
      ;;
    --clean-volumes)
      CLEAN_VOLUMES=true
      ;;
    --help|-h)
      echo "Playwright Docker 测试运行脚本"
      echo ""
      echo "使用方法:"
      echo "  $0 [OPTIONS] [BASE_URL] [PLAYWRIGHT_ARGS...]"
      echo ""
      echo "选项:"
      echo "  --rebuild         强制重新构建镜像(不使用 Docker 缓存)"
      echo "  --clean-cache     清除 Docker 构建缓存"
      echo "  --clean-volumes   清除 node_modules 卷(重新安装依赖)"
      echo "  --help, -h        显示此帮助信息"
      echo ""
      echo "示例:"
      echo "  $0                                    # 正常运行(使用缓存)"
      echo "  $0 --rebuild                          # 强制重建镜像"
      echo "  $0 --clean-volumes                    # 清除依赖并重新安装"
      echo "  $0 http://localhost:3000 --headed     # 指定地址和参数"
      exit 0
      ;;
    http://*|https://*)
      BASE_URL="$arg"
      ;;
    *)
      PLAYWRIGHT_ARGS+=("$arg")
      ;;
  esac
done

# 使用默认地址(如果未指定)
if [ -z "$BASE_URL" ]; then
  BASE_URL="$DEFAULT_BASE_URL"
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Bella OpenAPI - Playwright Docker 测试${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${YELLOW}测试地址:${NC} $BASE_URL"
echo -e "${YELLOW}认证模式:${NC} $AUTH_MODE"
echo -e "${YELLOW}项目目录:${NC} $PROJECT_ROOT"

if [ ${#PLAYWRIGHT_ARGS[@]} -gt 0 ]; then
  echo -e "${YELLOW}Playwright 参数:${NC} ${PLAYWRIGHT_ARGS[*]}"
fi

echo ""

# 切换到项目根目录
cd "$PROJECT_ROOT"

# 检查 Docker 是否运行
if ! docker info > /dev/null 2>&1; then
  echo -e "${RED}错误: Docker 未运行,请先启动 Docker${NC}"
  exit 1
fi

# 确保登录态目录存在(用于持久化 storage-state.json)
AUTH_DIR="$PROJECT_ROOT/tests/.auth"
if [ ! -d "$AUTH_DIR" ]; then
  echo -e "${YELLOW}创建登录态目录: $AUTH_DIR${NC}"
  mkdir -p "$AUTH_DIR"
  echo -e "${GREEN}✓ 登录态目录已创建${NC}"
  echo ""
fi

# 清除 Docker 构建缓存
if [ "$CLEAN_CACHE" = true ]; then
  echo -e "${YELLOW}清除 Docker 构建缓存...${NC}"
  docker builder prune -f
  echo -e "${GREEN}✓ Docker 构建缓存已清除${NC}"
  echo ""
fi

# 清除 node_modules 卷
if [ "$CLEAN_VOLUMES" = true ]; then
  echo -e "${YELLOW}清除 node_modules 卷...${NC}"
  VOLUME_NAME="tests_playwright_node_modules"

  if docker volume inspect "$VOLUME_NAME" > /dev/null 2>&1; then
    docker volume rm "$VOLUME_NAME" || {
      echo -e "${RED}警告: 无法删除卷 $VOLUME_NAME (可能正在使用中)${NC}"
      echo -e "${YELLOW}尝试停止相关容器...${NC}"
      docker-compose -f tests/docker-compose.test.yml down
      docker volume rm "$VOLUME_NAME"
    }
    echo -e "${GREEN}✓ node_modules 卷已清除,下次运行将重新安装依赖${NC}"
  else
    echo -e "${BLUE}ℹ node_modules 卷不存在,跳过清除${NC}"
  fi
  echo ""
fi

# 显示当前缓存状态
echo -e "${BLUE}当前缓存状态:${NC}"
VOLUME_NAME="tests_playwright_node_modules"
if docker volume inspect "$VOLUME_NAME" > /dev/null 2>&1; then
  VOLUME_SIZE=$(docker system df -v | grep "$VOLUME_NAME" | awk '{print $3}' || echo "未知")
  echo -e "  ${GREEN}✓${NC} node_modules 卷已存在 (大小: $VOLUME_SIZE)"
else
  echo -e "  ${YELLOW}○${NC} node_modules 卷不存在,首次运行将创建"
fi

if [ "$AUTH_MODE" = "cas" ]; then
  if [ -z "${CAS_USERNAME:-}" ] || [ -z "${CAS_PASSWORD:-}" ]; then
    echo -e "${RED}错误: AUTH_MODE=cas 时必须提供 CAS_USERNAME 和 CAS_PASSWORD${NC}"
    exit 1
  fi
fi

# 检查镜像是否存在
if docker image inspect bella-openapi-playwright-test:latest > /dev/null 2>&1; then
  IMAGE_SIZE=$(docker image inspect bella-openapi-playwright-test:latest --format='{{.Size}}' | awk '{print $1/1024/1024 "MB"}')
  echo -e "  ${GREEN}✓${NC} 测试镜像已存在 (大小: $IMAGE_SIZE)"
else
  echo -e "  ${YELLOW}○${NC} 测试镜像不存在,将进行构建"
fi
echo ""

# 构建或更新测试容器
if [ "$REBUILD" = true ]; then
  echo -e "${YELLOW}强制重新构建测试容器(不使用缓存)...${NC}"
  docker-compose -f tests/docker-compose.test.yml build --no-cache
else
  echo -e "${YELLOW}检查并构建测试容器镜像...${NC}"
  docker-compose -f tests/docker-compose.test.yml build
fi

echo ""
echo -e "${GREEN}开始运行测试...${NC}"
echo ""

# 运行测试
export BASE_URL
export AUTH_MODE
export CAS_USERNAME
export CAS_PASSWORD
export CAS_SUCCESS_URL
if [ ${#PLAYWRIGHT_ARGS[@]} -gt 0 ]; then
  # 有额外参数时,覆盖默认命令
  docker-compose -f tests/docker-compose.test.yml run --rm playwright-test \
    npx playwright test --config=playwright.docker.config.ts "${PLAYWRIGHT_ARGS[@]}"
else
  # 无额外参数时,使用默认命令
  docker-compose -f tests/docker-compose.test.yml run --rm playwright-test
fi

TEST_EXIT_CODE=$?

echo ""
if [ $TEST_EXIT_CODE -eq 0 ]; then
  echo -e "${GREEN}✓ 测试完成!${NC}"
  echo -e "${YELLOW}测试报告:${NC} $PROJECT_ROOT/playwright-report/index.html"
  echo -e "${YELLOW}测试结果:${NC} $PROJECT_ROOT/test-results/"
else
  echo -e "${RED}✗ 测试失败 (退出码: $TEST_EXIT_CODE)${NC}"
  echo -e "${YELLOW}查看测试报告:${NC} $PROJECT_ROOT/playwright-report/index.html"
  echo -e "${YELLOW}查看测试结果:${NC} $PROJECT_ROOT/test-results/"
fi

echo ""

exit $TEST_EXIT_CODE