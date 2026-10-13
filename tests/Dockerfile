# Playwright 测试环境 Dockerfile
#
# 职责:
# - 提供隔离的 Docker 环境运行 E2E 测试
# - 使用 Playwright 内置浏览器,确保版本匹配和功能完整性
# - 通过分层构建最大化 Docker 缓存利用率
#
# 设计说明:
# 1. 分层策略:系统依赖层 → npm配置层 → package.json层 → Playwright浏览器层 → 代码层
# 2. 只有 package.json 变化时才重新安装依赖,其他文件变化不影响依赖层
# 3. 使用 .dockerignore 排除不必要文件,避免缓存失效
# 4. 中文字体层独立,很少变化
# 5. Playwright 浏览器层独立缓存,减少重复下载

FROM node:20-bookworm-slim

WORKDIR /app

# ============================================================
# 第一层:系统依赖安装(很少变化,优先缓存)
# ============================================================
# 安装 Playwright 运行时依赖(不包括浏览器本身)
# 优化 APT 源使用国内镜像加速
RUN echo "deb https://mirrors.aliyun.com/debian/ bookworm main non-free contrib" > /etc/apt/sources.list && \
    echo "deb https://mirrors.aliyun.com/debian/ bookworm-updates main non-free contrib" >> /etc/apt/sources.list && \
    echo "deb https://mirrors.aliyun.com/debian-security bookworm-security main" >> /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        fonts-liberation \
        fonts-wqy-zenhei \
        fonts-wqy-microhei \
        fontconfig \
        libnss3 \
        libnspr4 \
        libatk1.0-0 \
        libatk-bridge2.0-0 \
        libcups2 \
        libdrm2 \
        libxkbcommon0 \
        libxcomposite1 \
        libxdamage1 \
        libxfixes3 \
        libxrandr2 \
        libgbm1 \
        libasound2 && \
    # 清理 APT 缓存
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /var/cache/apt/*

# ============================================================
# 第二层:中文字体配置(轻量级,使用 WenQuanYi 字体)
# ============================================================
RUN fc-cache -f -v && \
    mkdir -p /etc/fonts/conf.d && \
    cat > /etc/fonts/conf.d/99-chinese.conf <<'EOF'
<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "fonts.dtd">
<fontconfig>
  <match target="pattern">
    <test qual="any" name="family"><string>serif</string></test>
    <edit name="family" mode="prepend" binding="strong">
      <string>WenQuanYi Zen Hei</string>
    </edit>
  </match>
  <match target="pattern">
    <test qual="any" name="family"><string>sans-serif</string></test>
    <edit name="family" mode="prepend" binding="strong">
      <string>WenQuanYi Micro Hei</string>
    </edit>
  </match>
  <match target="pattern">
    <test qual="any" name="family"><string>monospace</string></test>
    <edit name="family" mode="prepend" binding="strong">
      <string>WenQuanYi Zen Hei</string>
    </edit>
  </match>
</fontconfig>
EOF

# ============================================================
# 第三层:npm 配置(很少变化)
# ============================================================
RUN npm config set registry https://registry.npmmirror.com

# ============================================================
# 第四层:依赖安装(只有 package.json 变化时才重建)
# ============================================================
# 关键优化:先复制 package.json,再安装依赖,最后复制代码
# 这样代码变化不会触发依赖重新安装
COPY package.json package-lock.json* yarn.lock* pnpm-lock.yaml* ./

# 安装依赖,支持 npm/yarn/pnpm
# 使用 BuildKit 缓存挂载,即使重建镜像也能复用 npm/yarn/pnpm 缓存
RUN --mount=type=cache,target=/root/.npm \
    --mount=type=cache,target=/root/.yarn \
    --mount=type=cache,target=/root/.local/share/pnpm/store \
    if [ -f yarn.lock ]; then \
        yarn config set registry https://registry.npmmirror.com && \
        yarn install --frozen-lockfile --prefer-offline --network-timeout 300000; \
    elif [ -f package-lock.json ]; then \
        npm ci --prefer-offline --timeout=300000; \
    elif [ -f pnpm-lock.yaml ]; then \
        npm install -g pnpm@10.6.5 --registry=https://registry.npmmirror.com && \
        pnpm config set registry https://registry.npmmirror.com && \
        pnpm config set network-timeout 300000 && \
        pnpm install --frozen-lockfile; \
    else \
        npm install --timeout=300000; \
    fi

# ============================================================
# 第五层:安装 Playwright 浏览器(独立层,方便缓存)
# ============================================================
# 使用 Playwright 官方命令安装浏览器
# 只安装 Chromium,不安装 Firefox 和 WebKit
RUN npx playwright install --with-deps chromium

# ============================================================
# 第六层:复制代码和配置(经常变化,放在最后)
# ============================================================
# 这些文件会在运行时通过 volume 挂载覆盖,这里只是备用
COPY playwright.config.ts ./
COPY playwright.docker.config.ts ./
COPY tests ./tests

# 默认命令:运行测试
CMD ["npx", "playwright", "test", "--config=playwright.docker.config.ts"]