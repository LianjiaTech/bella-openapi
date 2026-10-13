#!/bin/bash

# 管理员授权脚本
# 此脚本用于授权管理员访问系统
# 使用方法：先登录前端页面获取userId，然后运行此脚本

# 配置参数
API_HOST=${SERVER:-"http://localhost:8080"}
OUTPUT_FILE="system-apikey.txt"
# 从环境变量获取前端URL，默认为http://localhost:3000
FRONTEND_URL=${SERVER:-"http://localhost:3000"}

echo "=== Bella OpenAPI 管理员授权 ==="
echo ""
echo "请先登录前端页面 ($FRONTEND_URL)，获取您的用户ID或邮箱"
echo "您可以通过点击右上角头像查看个人信息获取用户ID"
echo ""

# 检查是否存在系统API Key文件
if [ ! -f "$OUTPUT_FILE" ]; then
    echo "错误: 未找到系统API Key文件 ($OUTPUT_FILE)"
    echo "请先运行 ./generate-system-apikey.sh 生成系统API Key"
    exit 1
fi

# 从文件中提取API Key
API_KEY=$(grep "API Key:" "$OUTPUT_FILE" | cut -d':' -f2 | tr -d ' ')

echo "现有系统API key: $API_KEY"

if [ -z "$API_KEY" ]; then
    echo "错误: 无法从 $OUTPUT_FILE 中提取API Key"
    echo "请确保已正确生成系统API Key"
    exit 1
fi

# 选择授权方式
echo "请选择授权方式:"
echo "1. 使用用户ID授权"
echo "2. 使用邮箱授权"
read -p "请选择 (1/2): " AUTH_METHOD

case $AUTH_METHOD in
    1)
        # 使用用户ID授权
        read -p "请输入用户ID: " USER_ID
        if [ -z "$USER_ID" ]; then
            echo "错误: 用户ID不能为空"
            exit 1
        fi
        
        read -p "请输入用户名称 (可选): " USER_NAME
        USER_NAME=${USER_NAME:-"system"}
        
        echo "正在授权用户ID: $USER_ID 为管理员..."
        
        # 构建请求数据
        REQUEST_DATA="{
            \"userId\": $USER_ID,
            \"userName\": \"$USER_NAME\"
        }"
        ;;
    2)
        # 使用邮箱授权
        read -p "请输入邮箱地址: " EMAIL
        if [ -z "$EMAIL" ]; then
            echo "错误: 邮箱地址不能为空"
            exit 1
        fi
        
        read -p "请输入用户来源 (例如: google, github): " SOURCE
        SOURCE=${SOURCE:-"google"}
        
        read -p "请输入用户名称 (可选): " USER_NAME
        USER_NAME=${USER_NAME:-"system"}
        
        echo "正在授权邮箱: $EMAIL 为管理员..."
        
        # 构建请求数据
        REQUEST_DATA="{
            \"source\": \"$SOURCE\",
            \"email\": \"$EMAIL\",
            \"userId\": 0,
            \"userName\": \"$USER_NAME\"
        }"
        ;;
    *)
        echo "错误: 无效的选择"
        exit 1
        ;;
esac

# 发送授权请求
RESPONSE=$(curl --location "$API_HOST/console/userInfo/manager" \
--header "Authorization: Bearer $API_KEY" \
--header "Content-Type: application/json" \
--data-raw "$REQUEST_DATA" -s)

# 检查响应
if [[ $RESPONSE == *"\"code\":200"* ]]; then
    echo "授权成功！请点击头像登出系统，重新登录后即可拥有管理员权限！"
else
    echo "授权失败。服务器响应:"
    echo "$RESPONSE"
    echo ""
    echo "请检查用户id或邮箱是否正确，并重新运行./authorize-admin.sh"
fi
