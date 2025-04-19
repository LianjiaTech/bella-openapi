#!/bin/bash

# 帮助函数
show_help() {
    echo "用法: $0 [选项]"
    echo "选项:"
    echo "  -h, --help       显示帮助信息"
    echo "  -b, --build      重新构建服务"
    echo "  -r, --rebuild    强制重新构建服务（不使用缓存）"
    echo "  -e, --env ENV    指定环境（dev, test, prod）"
    echo "  -v, --version VERSION 指定镜像版本"
    echo "  --skip-auth      新生成系统API Key时跳过管理员授权步骤"
    echo "  --push           构建后推送镜像到仓库（自动设置--build，推送后不启动）"
    echo "  --update-image   从远程仓库更新镜像，即使本地已存在"
    echo "  --registry username   指定推送的docker仓库 (username)"
    echo "  --github-oauth CLIENT_ID:CLIENT_SECRET    配置GitHub OAuth"
    echo "  --google-oauth CLIENT_ID:CLIENT_SECRET    配置Google OAuth"
    echo "  --server URL                              配置服务域名，必须包含协议前缀 (例如: http://example.com 或 https://example.com)"
    echo "  --cas-server URL                          配置CAS服务器URL (例如: https://cas.example.com)"
    echo "  --cas-login URL                           配置CAS登录URL (例如: https://cas.example.com/login)"
    echo "  --proxy-host HOST                         配置代理服务器主机名或IP地址"
    echo "  --proxy-port PORT                         配置代理服务器端口"
    echo "  --proxy-type TYPE                         配置代理类型 (socks 或 http)"
    echo "  --proxy-domains DOMAINS                   配置需要通过代理访问的域名，多个域名用逗号分隔"
    echo "  --restart SERVICE                         重启指定服务，不重新编译 (例如: api 或 web)"
    echo "  --nginx-port PORT                         指定Nginx服务映射到的端口，默认为80"
    echo "  --services SERVICES                       配置动态服务，只支持同docker服务网络下部署的服务，使用容器名转发，格式为 '服务名1:域名1:端口1,服务名2:域名2:端口2'"
    echo ""
    echo "示例:"
    echo "  ./start.sh           启动服务（如果已存在编译文件则不重新构建）"
    echo "  ./start.sh --build   启动服务并重新构建"
    echo "  ./start.sh --rebuild 启动服务并强制重新构建"
    echo "  ./start.sh --proxy-host 127.0.0.1 --proxy-port 8118 --proxy-type http --proxy-domains github.com,google.com"
    echo "  ./start.sh -e dev    以开发环境启动服务"
    echo "  ./start.sh -b -e test 重新构建并以测试环境启动服务"
    echo "  ./start.sh -r -e prod 强制重新构建并以生产环境启动服务"
    echo "  ./start.sh --skip-auth      新生成系统API Key时跳过管理员授权步骤"
    echo "  ./start.sh --server https://example.com    配置服务域名"
    echo "  ./start.sh --github-oauth abc123:xyz789 --google-oauth def456:uvw321      配置oauth登录选项"
    echo "  ./start.sh --github-oauth abc123:xyz789 --server https://example.com   配置oauth登录选项和服务域名"
    echo "  ./start.sh --cas-server https://cas.example.com --cas-login https://cas.example.com/login --server https://example.com  配置cas登录选项和服务域名"
    echo "  ./start.sh --build --push --registry username --version v1.0.0   构建并推送镜像到指定仓库"
    echo "  ./start.sh --restart api    仅重启 API 服务，不重新编译"
    echo "  ./start.sh --restart web    仅重启 Web 服务，不重新编译"
    echo "  ./start.sh --nginx-port 8080  使用端口8080启动Nginx服务，其他服务不占用物理机端口"
    echo "  ./start.sh --services 'service1:example1.com:80,service2:example2.com:8080'   配置动态服务"
    echo ""
    echo "版本参数:"
    echo "  --version VERSION    指定镜像版本，例如 --version v1.0.0"
    echo "  -v VERSION    指定镜像版本，例如 --v v1.0.0"
}

# 默认不重新构建
BUILD=""
FORCE_RECREATE=""
ENV="prod"
SKIP_AUTH=false
# OAuth 默认值
GITHUB_OAUTH=""
GOOGLE_OAUTH=""
SERVER=""
CAS_SERVER=""
CAS_LOGIN=""
# 镜像仓库相关
PUSH=false
REGISTRY=""
NO_CACHE=""
# 是否强制更新镜像
UPDATE_IMAGE=false
# 重启服务
RESTART_SERVICE=""
# 代理配置
PROXY_HOST=""
PROXY_PORT=""
PROXY_TYPE=""
PROXY_DOMAINS=""
# Nginx端口
NGINX_PORT="80"
# 动态服务配置
SERVICES=""

# 添加重试函数
retry_command() {
    local max_attempts=3
    local timeout=5
    local attempt=1
    local exit_code=0

    while [[ $attempt -le $max_attempts ]]
    do
        echo "尝试执行命令: $@（第 $attempt 次，共 $max_attempts 次）"
        "$@"
        exit_code=$?

        if [[ $exit_code -eq 0 ]]; then
            echo "命令执行成功！"
            break
        fi

        echo "命令执行失败，退出码: $exit_code"
        
        if [[ $attempt -lt $max_attempts ]]; then
            echo "等待 $timeout 秒后重试..."
            sleep $timeout
            # 每次重试增加等待时间
            timeout=$((timeout * 2))
        fi
        
        attempt=$((attempt + 1))
    done

    return $exit_code
}

# 检查镜像是否存在
image_exists() {
    local image_name=$1
    docker image inspect $image_name &>/dev/null
    return $?
}

# 拉取镜像（如果本地不存在）
pull_image_if_not_exists() {
    local image_name=$1
    local message=${2:-"拉取镜像: $image_name"}
    
    if ! image_exists $image_name; then
        echo "$message"
        retry_command docker pull $image_name || true
        return $?
    else
        echo "本地镜像 $image_name 已存在，跳过拉取"
        return 0
    fi
}

# 拉取应用镜像（如果本地不存在或强制更新）
pull_app_image_if_not_exists() {
    local service=$1
    local version=${VERSION:-latest}
    
    # 镜像名称（带仓库前缀）
    local image_name="${REGISTRY:-bellatop}/bella-openapi-$service:$version"
    
    # 如果设置了强制更新镜像，则直接拉取
    if [ "$UPDATE_IMAGE" = true ]; then
        echo "强制从远程仓库更新镜像: $image_name ..."
        retry_command docker pull $image_name
        return $?
    fi
    
    # 检查镜像是否存在
    if ! image_exists $image_name; then
        echo "镜像 $image_name 不存在，尝试从远程仓库拉取..."
        pull_image_if_not_exists $image_name "拉取 $service 镜像: $image_name"
        return $?
    else
        echo "镜像 $image_name 已存在，跳过拉取"
        return 0
    fi
}

# 预先拉取所需的 Docker 镜像
pre_pull_images() {
    echo "预先拉取所需的 Docker 镜像..."
    
    # 创建数据目录并设置权限
    echo "创建数据目录并设置权限..."
    mkdir -p ./mysql/data
    mkdir -p ./redis/data
    chmod -R 777 ./mysql/data
    chmod -R 777 ./redis/data
    
    # 拉取基础镜像（如果本地不存在）
    pull_image_if_not_exists "openjdk:8" "拉取 OpenJDK 镜像..."
    pull_image_if_not_exists "docker.ipigsy.com/library/node:20.11-alpine3.19" "拉取 Node.js 镜像..."
    pull_image_if_not_exists "nginx:latest" "拉取 Nginx 镜像..."
    pull_image_if_not_exists "mysql:8.0" "拉取 MySQL 镜像..."
    pull_image_if_not_exists "redis:6" "拉取 Redis 镜像..."
    
    # 如果不需要编译（没有 --build 参数），则拉取应用镜像
    if [ -z "$BUILD" ]; then
        echo "检查是否需要拉取应用镜像..."
        
        # 拉取 API 和 Web 镜像
        pull_app_image_if_not_exists "api"
        pull_app_image_if_not_exists "web"
    else
        echo "检测到 --build 参数，跳过拉取应用镜像，将使用本地构建"
    fi
    
    echo "所有镜像拉取完成"
}

# 生成服务配置
generate_service_configs() {
    # 如果没有配置服务，返回空
    if [ -z "$SERVICES" ]; then
        echo "# 没有配置动态服务"
        return
    fi
    
    # 初始化服务配置字符串
    local service_configs=""
    
    # 处理每个服务配置
    IFS=',' read -ra SERVICE_ARRAY <<< "$SERVICES"
    for service_config in "${SERVICE_ARRAY[@]}"; do
        # 解析服务配置（格式：服务名:域名:端口）
        IFS=':' read -ra CONFIG <<< "$service_config"
        local service_name="${CONFIG[0]}"
        local service_domain="${CONFIG[1]}"
        local service_port="${CONFIG[2]:-80}"
        
        if [ -z "$service_name" ] || [ -z "$service_domain" ] || [ -z "$service_port" ]; then
            echo "警告: 服务配置格式不正确: $service_config，应为 服务名:域名:端口"
            continue
        fi
        
        # 获取容器名（服务名加前缀）
        local container_name="$service_name"
        
        # 生成服务配置
        service_configs+=$(cat <<EOF

# $service_name 服务域名配置
server {
    listen       80;
    listen  [::]:80;
    server_name  $service_domain;

    # 访问日志
    access_log  /var/log/nginx/$(echo $service_name | tr -d ' ').access.log  main;

    # 添加resolver指令，避免启动时检查upstream主机
    resolver 127.0.0.11 valid=30s;

    # 所有请求转发到 $service_name 服务
    location / {
        set \$backend "$container_name:$service_port";
        proxy_pass http://\$backend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header X-Forwarded-Host \$host;
        proxy_set_header X-Forwarded-Port \$server_port;
        
        # 添加WebSocket支持
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        
        # 确保原始请求方法和头信息传递
        proxy_pass_request_headers on;
        proxy_pass_request_body on;
    }
}
EOF
)
    done
    
    echo "$service_configs"
}

# 构建函数 - 处理所有构建逻辑
build_services() {
    # 设置缓存选项
    CACHE_OPT=""
    if [ -n "$NO_CACHE" ]; then
        CACHE_OPT="--no-cache"
    fi
    
    # 执行 API 服务的 Maven 编译
    echo "执行 API 服务的 Maven 编译..."
    chmod +x api/build.sh
    ./api/build.sh
    if [ $? -ne 0 ]; then
        echo "错误: API 服务编译失败，退出执行"
        exit 1
    fi
    
    # 根据是否需要推送镜像选择构建方式
    if [ "$PUSH" = true ] && [ -n "$REGISTRY" ]; then
        # 多架构构建并推送
        if docker buildx version >/dev/null 2>&1; then
            echo "使用 buildx 进行多架构构建并推送..."
            
            # 清理 builder 缓存，避免磁盘空间不足
            echo "清理 buildx 缓存..."
            docker buildx prune -f
            
            # 删除并重新创建 builder 实例，确保干净的构建环境
            echo "重新创建 buildx builder 实例..."
            docker buildx rm multibuilder 2>/dev/null || true
            docker buildx create --name multibuilder --driver docker-container --bootstrap --use
            
            # 确认 builder 状态
            echo "检查 builder 状态..."
            docker buildx inspect --bootstrap
            
            # 推送时使用多架构
            PLATFORMS="linux/amd64,linux/arm64"
            echo "推送多架构镜像，支持平台: $PLATFORMS"
            
            # 构建并推送 API 多架构镜像
            echo "构建并推送 API 多架构镜像..."
            docker buildx build $CACHE_OPT \
                --platform $PLATFORMS \
                --build-arg VERSION=${VERSION:-v1.0.0} \
                --build-arg REGISTRY=${REGISTRY:-bellatop} \
                -t ${REGISTRY:-bellatop}/bella-openapi-api:${VERSION:-v1.0.0} \
                -t ${REGISTRY:-bellatop}/bella-openapi-api:latest \
                --push ./api
                
            # 构建并推送 Web 多架构镜像
            echo "构建并推送 Web 多架构镜像..."
            docker buildx build $CACHE_OPT \
                --platform $PLATFORMS \
                --build-arg VERSION=${VERSION:-v1.0.0} \
                --build-arg REGISTRY=${REGISTRY:-bellatop} \
                -t ${REGISTRY:-bellatop}/bella-openapi-web:${VERSION:-v1.0.0} \
                -t ${REGISTRY:-bellatop}/bella-openapi-web:latest \
                --push ./web
                
            echo "验证多架构镜像..."
            docker buildx imagetools inspect ${REGISTRY:-bellatop}/bella-openapi-api:${VERSION:-v1.0.0}
            docker buildx imagetools inspect ${REGISTRY:-bellatop}/bella-openapi-web:${VERSION:-v1.0.0}
                
            echo "✅ 多架构镜像已成功推送到 ${REGISTRY:-bellatop}"
            echo "   这些镜像可以在任何支持的平台上运行，包括:"
            echo "   - x86_64/amd64 系统 (大多数 Linux 服务器、Intel Mac、Windows)"
            echo "   - ARM64 系统 (Apple Silicon Mac、AWS Graviton、树莓派 4 64位)"
            
            # 推送后不自动启动服务，直接退出
            echo ""
            echo "镜像已成功推送，可以在服务器上使用以下命令拉取和启动服务:"
            echo "./start.sh --registry ${REGISTRY:-bellatop} --version ${VERSION:-v1.0.0}"
            exit 0
        else
            echo "错误: buildx 不可用，无法构建多架构镜像"
            exit 1
        fi
    else
        # 本地构建，使用 docker-compose
        echo "本地构建，使用 docker-compose..."
        if [ -n "$NO_CACHE" ]; then
            echo "强制重新构建（不使用缓存）..."
            docker-compose build --no-cache --build-arg VERSION=${VERSION:-v1.0.0} --build-arg REGISTRY=${REGISTRY:-bellatop} --build-arg NODE_ENV=$NODE_ENV --build-arg DEPLOY_ENV=$DEPLOY_ENV
        else
            echo "重新构建..."
            docker-compose build --build-arg VERSION=${VERSION:-v1.0.0} --build-arg REGISTRY=${REGISTRY:-bellatop} --build-arg NODE_ENV=$NODE_ENV --build-arg DEPLOY_ENV=$DEPLOY_ENV
        fi
    fi
}

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -b|--build)
            BUILD="--build"
            shift
            ;;
        -r|--rebuild)
            BUILD="--build"
            FORCE_RECREATE="--force-recreate"
            NO_CACHE="--no-cache"
            shift
            ;;
        -e|--env)
            ENV="$2"
            shift 2
            ;;
        -v|--version)
            VERSION="$2"
            shift 2
            ;;
        --skip-auth)
            SKIP_AUTH=true
            shift
            ;;
        --push)
            PUSH=true
            BUILD="--build"
            echo "设置推送模式，将自动构建镜像"
            shift
            ;;
        --update-image)
            UPDATE_IMAGE=true
            echo "设置强制更新镜像模式"
            shift
            ;;
        --registry)
            REGISTRY="$2"
            shift 2
            ;;
        --github-oauth)
            GITHUB_OAUTH="$2"
            shift 2
            ;;
        --google-oauth)
            GOOGLE_OAUTH="$2"
            shift 2
            ;;
        --server)
            SERVER="$2"
            shift 2
            ;;
        --cas-server)
            CAS_SERVER="$2"
            shift 2
            ;;
        --cas-login)
            CAS_LOGIN="$2"
            shift 2
            ;;
        --proxy-host)
            PROXY_HOST="$2"
            shift 2
            ;;
        --proxy-port)
            PROXY_PORT="$2"
            shift 2
            ;;
        --proxy-type)
            PROXY_TYPE="$2"
            shift 2
            ;;
        --proxy-domains)
            PROXY_DOMAINS="$2"
            shift 2
            ;;
        --restart)
            RESTART_SERVICE="$2"
            shift 2
            ;;
        --nginx-port)
            NGINX_PORT="$2"
            shift 2
            ;;
        --services)
            SERVICES="$2"
            shift 2
            ;;
        *)
            echo "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
done

pre_pull_images

# 执行构建（如果需要）
if [ -n "$BUILD" ] || [ -n "$FORCE_RECREATE" ]; then
    echo "构建服务..."
    build_services
fi

# 如果指定了重启特定服务
if [ -n "$RESTART_SERVICE" ]; then
    echo "重启 $RESTART_SERVICE 服务..."
    docker-compose restart $RESTART_SERVICE
    echo "$RESTART_SERVICE 服务已重启"
    exit 0
fi

# 生成动态服务配置
if [ -n "$SERVICES" ]; then
    echo "生成动态服务配置..."
    # 生成动态服务配置并写入到文件中
    DYNAMIC_SERVICE_CONFIGS=$(generate_service_configs)
else
    # 如果没有配置服务，创建一个只包含注释的配置文件
    DYNAMIC_SERVICE_CONFIGS="# 没有配置动态服务"
fi
# 创建nginx配置目录（如果不存在）
mkdir -p ./nginx/conf.d
# 删除旧的配置文件
rm -rf ./nginx/conf.d/dynamic-services.conf
# 将动态服务配置写入到单独的配置文件中
echo "$DYNAMIC_SERVICE_CONFIGS" > ./nginx/conf.d/dynamic-services.conf

# 处理OAuth配置
if [ -n "$GITHUB_OAUTH" ]; then
    # 验证格式是否正确 (CLIENT_ID:CLIENT_SECRET)
    if [[ "$GITHUB_OAUTH" != *:* ]]; then
        echo "错误: GitHub OAuth 参数格式不正确，应为 CLIENT_ID:CLIENT_SECRET"
        exit 1
    fi
    
    # 分割CLIENT_ID和CLIENT_SECRET
    GITHUB_CLIENT_ID=$(echo $GITHUB_OAUTH | cut -d: -f1)
    GITHUB_CLIENT_SECRET=$(echo $GITHUB_OAUTH | cut -d: -f2)
    
    # 导出环境变量
    export GITHUB_ENABLE=true
    export GITHUB_CLIENT_ID=$GITHUB_CLIENT_ID
    export GITHUB_CLIENT_SECRET=$GITHUB_CLIENT_SECRET
    export LOGIN_TYPE=oauth
    
    echo "已配置 GitHub OAuth: CLIENT_ID=$GITHUB_CLIENT_ID"
fi

if [ -n "$GOOGLE_OAUTH" ]; then
    # 验证格式是否正确 (CLIENT_ID:CLIENT_SECRET)
    if [[ "$GOOGLE_OAUTH" != *:* ]]; then
        echo "错误: Google OAuth 参数格式不正确，应为 CLIENT_ID:CLIENT_SECRET"
        exit 1
    fi
    
    # 分割CLIENT_ID和CLIENT_SECRET
    GOOGLE_CLIENT_ID=$(echo $GOOGLE_OAUTH | cut -d: -f1)
    GOOGLE_CLIENT_SECRET=$(echo $GOOGLE_OAUTH | cut -d: -f2)
    
    # 导出环境变量
    export GOOGLE_ENABLE=true
    export GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID
    export GOOGLE_CLIENT_SECRET=$GOOGLE_CLIENT_SECRET
    export LOGIN_TYPE=oauth
    
    echo "已配置 Google OAuth: CLIENT_ID=$GOOGLE_CLIENT_ID"
fi

# 设置服务域名
if [ -n "$SERVER" ]; then
    # 检查是否包含协议前缀
    if [[ "$SERVER" != http://* && "$SERVER" != https://* ]]; then
        echo "错误: --server 参数必须包含协议前缀 (http:// 或 https://)"
        exit 1
    fi
    
    # 无协议前缀的域名
    SERVER_DOMAIN=$(echo $SERVER | sed -e 's|^http://||' -e 's|^https://||')
    export SERVER
    export SERVER_DOMAIN
    echo "已配置服务域名: $SERVER (域名: $SERVER_DOMAIN)"
else
    # 默认服务域名
    export SERVER="http://localhost"
    export SERVER_DOMAIN="localhost"
fi

# 设置CAS服务器URL
if [ -n "$CAS_SERVER" ]; then
    export CAS_SERVER=$CAS_SERVER
    echo "已配置CAS服务器URL: $CAS_SERVER"
fi

# 设置CAS登录URL
if [ -n "$CAS_LOGIN" ]; then
    export CAS_LOGIN=$CAS_LOGIN
    echo "已配置CAS登录URL: $CAS_LOGIN"
fi

# 设置登录类型
if [ -n "$CAS_SERVER" ] && [ -n "$CAS_LOGIN" ]; then
    export LOGIN_TYPE="cas"
    echo "检测到CAS配置完整，已设置登录类型为: cas"
fi

# 设置代理配置
if [ -n "$PROXY_HOST" ] && [ -n "$PROXY_PORT" ] && [ -n "$PROXY_TYPE" ]; then
    export PROXY_HOST=$PROXY_HOST
    export PROXY_PORT=$PROXY_PORT
    export PROXY_TYPE=$PROXY_TYPE
    export PROXY_DOMAINS=$PROXY_DOMAINS
    
    echo "已配置代理: $PROXY_HOST:$PROXY_PORT ($PROXY_TYPE)"
fi

# 导出环境变量
export VERSION=$VERSION
export NGINX_PORT=$NGINX_PORT

# 验证环境参数
if [[ "$ENV" != "dev" && "$ENV" != "test" && "$ENV" != "prod" ]]; then
    echo "错误: 环境必须是 dev, test 或 prod"
    exit 1
fi

# 根据环境设置映射关系
case $ENV in
    dev)
        NODE_ENV="test"
        DEPLOY_ENV="test"
        SPRING_PROFILE="docker"
        ;;
    test)
        NODE_ENV="test"
        DEPLOY_ENV="test"
        SPRING_PROFILE="docker"
        ;;
    prod)
        NODE_ENV="production"
        DEPLOY_ENV="production"
        SPRING_PROFILE="docker"
        ;;
esac

echo "环境: $ENV (前端: NODE_ENV=$NODE_ENV, DEPLOY_ENV=$DEPLOY_ENV, 后端: SPRING_PROFILES_ACTIVE=$SPRING_PROFILE)"
echo "镜像版本: $VERSION"

# 导出环境变量，供 docker-compose.yml 使用
export SPRING_PROFILES_ACTIVE=$SPRING_PROFILE
export NODE_ENV=$NODE_ENV
export DEPLOY_ENV=$DEPLOY_ENV

# 检查 docker 和 docker-compose 是否安装
if ! command -v docker &> /dev/null; then
    echo "错误: docker 未安装"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "错误: docker-compose 未安装"
    exit 1
fi

# 启动服务
echo "启动服务..."
if [ -n "$FORCE_RECREATE" ]; then
    echo "强制重新创建容器..."
    docker-compose up -d --force-recreate --no-deps
    UP_RESULT=$?
else
    echo "正在启动服务..."
    docker-compose up -d
    UP_RESULT=$?
fi

# 检查 docker-compose up 命令是否成功
if [ $UP_RESULT -ne 0 ]; then
    echo "错误: docker-compose up 命令执行失败，退出代码: $UP_RESULT"
    echo "请检查 docker-compose.yml 文件和 Docker 服务状态"
    exit 1
fi

# 检查服务是否启动成功
echo "检查服务状态..."
sleep 5  # 等待服务启动

# 获取服务状态
SERVICES_STATUS=$(docker-compose ps --services --filter "status=running")

# 检查 API 服务
if ! echo "$SERVICES_STATUS" | grep -q "api"; then
    echo "错误: API 服务启动失败"
    echo "查看日志: docker-compose logs api"
    exit 1
fi

# 检查 Web 服务
if ! echo "$SERVICES_STATUS" | grep -q "web"; then
    echo "错误: Web 服务启动失败"
    echo "查看日志: docker-compose logs web"
    exit 1
fi

echo "✅ 所有服务启动成功！"
echo "服务域名: $SERVER"
echo ""
echo "查看日志: docker-compose logs -f"
echo "停止服务: ./stop.sh"

# 生成系统API Key
echo ""
echo "正在生成系统API Key..."
if [ -f "./generate-system-apikey.sh" ]; then
    # 执行生成系统API Key的脚本并捕获输出
    APIKEY_OUTPUT=$(./generate-system-apikey.sh)
    APIKEY_EXIT_CODE=$?
    
    # 输出脚本结果
    echo "$APIKEY_OUTPUT"
    
    # 检查脚本是否成功执行
    if [ $APIKEY_EXIT_CODE -eq 0 ]; then
        # 检查是否生成成功
        if [ -f "system-apikey.txt" ]; then
            echo ""
            
            # 检查输出中是否包含API_KEY_STATUS=NEW，判断是否是新生成的API Key
            if echo "$APIKEY_OUTPUT" | grep -q "API_KEY_STATUS=NEW"; then
                echo "系统API Key已成功生成！"
                
                # 如果是新生成的API Key且未设置跳过授权标志，则询问用户是否需要授权管理员
                if [ "$SKIP_AUTH" = false ]; then
                    echo ""
                    read -p "是否需要授权管理员？(请确保您已配置用户登录方式)(y/n): " NEED_AUTH
                    
                    if [[ "$NEED_AUTH" == "y" || "$NEED_AUTH" == "Y" ]]; then
                        echo ""
                        echo "请先登录前端页面获取您的用户ID或邮箱："
                        echo "1. 访问 $SERVER"
                        echo "2. 使用第三方账号登录（如Google、GitHub等）"
                        echo "3. 点击右上角头像，查看个人信息获取用户ID或邮箱"
                        echo ""
                        
                        # 等待用户登录
                        read -p "已登录并获取到用户ID/邮箱？按回车继续..." CONTINUE
                        
                        # 启动授权脚本
                        if [ -f "./authorize-admin.sh" ]; then
                            ./authorize-admin.sh
                        else
                            echo "错误: 未找到授权脚本 (authorize-admin.sh)"
                            echo "请手动授权管理员，详见README.md中的说明。"
                        fi
                    else
                        echo ""
                        echo "如需稍后授权管理员，请运行:"
                        echo "./authorize-admin.sh"
                    fi
                else
                    echo ""
                    echo "已跳过管理员授权步骤。"
                    echo "如需授权管理员，请稍后运行:"
                    echo "./authorize-admin.sh"
                fi
            else
                # 如果是已存在的API Key
                echo "检测到系统已存在API Key，已自动跳过管理员授权步骤。"
                echo "如需授权管理员，请稍后运行:"
                echo "./authorize-admin.sh"
            fi
        else
            echo "警告: 未找到system-apikey.txt文件，系统API Key可能未成功生成。"
        fi
    else
        echo ""
        echo "错误: 系统API Key生成失败。请检查错误信息并解决问题。"
    fi
else
    echo "警告: 未找到generate-system-apikey.sh脚本，请手动生成系统API Key。"
fi
