#!/bin/bash

# 生成系统API key并直接写入数据库的脚本
# 此脚本会生成一个system类型的API key，并将其直接插入到数据库中

# 配置参数
DB_HOST=${DB_HOST:-"mysql"}
DB_PORT=${DB_PORT:-"3306"}
DB_NAME=${DB_NAME:-"bella_openapi"}
DB_USER=${DB_USER:-"bella_user"}
DB_PASS=${DB_PASS:-"123456"}
MYSQL_CONTAINER="bella-openapi-mysql"
OUTPUT_FILE="system-apikey.txt"
# 从环境变量获取前端URL，默认为http://localhost:3000
FRONTEND_URL=${SERVER:-"http://localhost:3000"}

echo "正在生成系统API key..."

# 检查MySQL容器是否存在
if ! docker ps | grep -q "$MYSQL_CONTAINER"; then
    echo "错误: MySQL容器 '$MYSQL_CONTAINER' 未运行。"
    echo "请确保Docker服务已启动，并且MySQL容器正在运行。"
    exit 1
fi

# 生成API key的code (格式: ak-uuid)
CODE_UUID=$(uuidgen | tr '[:upper:]' '[:lower:]')
CODE="ak-$CODE_UUID"

# 生成实际的API key
APIKEY=$(uuidgen | tr '[:upper:]' '[:lower:]')

# 计算API key的SHA-256哈希值
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    AK_SHA=$(echo -n "$APIKEY" | shasum -a 256 | cut -d' ' -f1)
else
    # Linux
    AK_SHA=$(echo -n "$APIKEY" | sha256sum | cut -d' ' -f1)
fi

# 创建掩码显示版本 (前2个字符 + **** + 后4个字符)
if [ ${#APIKEY} -ge 6 ]; then
    FIRST_TWO=${APIKEY:0:2}
    LAST_FOUR=${APIKEY: -4}
    AK_DISPLAY="${FIRST_TWO}****${LAST_FOUR}"
else
    AK_DISPLAY=$APIKEY
fi

# 检查数据库是否存在
echo "检查数据库是否存在..."
DB_EXISTS=$(docker exec -i $MYSQL_CONTAINER mysql -u$DB_USER -p$DB_PASS -e "SHOW DATABASES LIKE '$DB_NAME';" 2>/dev/null | grep -c "$DB_NAME")

if [ "$DB_EXISTS" -eq 0 ]; then
    echo "错误: 数据库 '$DB_NAME' 不存在。"
    echo "请确保数据库已正确创建并且可以访问。"
    exit 1
fi

# 检查表是否存在
echo "检查apikey表是否存在..."
TABLE_EXISTS=$(docker exec -i $MYSQL_CONTAINER mysql -u$DB_USER -p$DB_PASS $DB_NAME -e "SHOW TABLES LIKE 'apikey';" 2>/dev/null | grep -c "apikey")

if [ "$TABLE_EXISTS" -eq 0 ]; then
    echo "错误: 'apikey'表不存在。"
    echo "请确保数据库结构已正确初始化。"
    exit 1
fi

# 检查是否已存在系统API key
echo "检查是否已存在系统API key..."
EXISTING_KEY=$(docker exec -i $MYSQL_CONTAINER mysql -u$DB_USER -p$DB_PASS $DB_NAME -e "SELECT code FROM apikey WHERE owner_type='system' LIMIT 1;" 2>/dev/null | grep -v code)

if [ -n "$EXISTING_KEY" ]; then
    echo "系统API key已存在，code: ${EXISTING_KEY}"
    echo "API_KEY_STATUS=EXISTING"
    exit 0
fi

# 生成插入SQL
INSERT_SQL="
SET NAMES utf8mb4;
INSERT INTO apikey (
    code, 
    ak_sha, 
    ak_display, 
    name, 
    owner_type, 
    owner_code, 
    owner_name, 
    role_code, 
    safety_level, 
    month_quota,
    status,
    cuid, 
    cu_name, 
    muid, 
    mu_name
) VALUES (
    '$CODE', 
    '$AK_SHA', 
    '$AK_DISPLAY', 
    'system apikey', 
    'system', 
    '0', 
    'system', 
    'all', 
    100, 
    50000, 
    'active',
    0, 
    'system', 
    0, 
    'system'
);"

# 执行SQL语句
docker exec -i $MYSQL_CONTAINER mysql -u$DB_USER -p$DB_PASS $DB_NAME <<EOF
$INSERT_SQL
EOF

if [ $? -ne 0 ]; then
    echo "错误: 插入API key失败。"
    exit 1
fi

# 保存到文件
echo "成功生成并插入系统API key到数据库" > $OUTPUT_FILE
echo "Code: $CODE" >> $OUTPUT_FILE
echo "API Key: $APIKEY" >> $OUTPUT_FILE
echo "登录密钥：$APIKEY (访问 $FRONTEND_URL/login 使用密钥登录)" >> $OUTPUT_FILE
echo "API_KEY_STATUS=NEW" >> $OUTPUT_FILE

echo "成功生成并插入系统API key到数据库"
echo "Code: $CODE"
echo "API Key: $APIKEY (已保存到$OUTPUT_FILE)"
echo "登录密钥：$APIKEY （访问 $FRONTEND_URL/login 使用密钥登录）"
echo "API_KEY_STATUS=NEW"

echo "完成。"
