#!/bin/bash

# 编译 API 服务的脚本

# 设置错误处理
set -e

# 函数：显示错误信息并退出
show_error_and_exit() {
    echo "错误: $1"
    exit 1
}

echo "编译 API 服务..."

# 获取当前脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# 切换到脚本所在目录（确保在 api 目录中）
cd "$SCRIPT_DIR"

# 检查 Maven 是否安装
if ! command -v mvn &> /dev/null; then
    show_error_and_exit "请安装 Maven 并确保其在 PATH 中"
else
    # 清理旧的构建产物
    rm -rf release/

    # 执行 Maven 编译
    echo "执行 Maven 编译..."
    if ! mvn clean package -Dmaven.test.skip=true; then
        show_error_and_exit "Maven 编译失败"
    fi

    # 创建必要的目录并复制构建产物
    mkdir -p release/{bin,lib}
    cp run.sh release/bin/run.sh
    chmod +x release/bin/run.sh
    cp server/target/*.jar release/lib/
fi

echo "API 服务编译完成"
exit 0
