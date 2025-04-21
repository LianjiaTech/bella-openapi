# base image
FROM docker.ipigsy.com/library/node:20.11-alpine3.19 AS base

# 国内使用阿里云镜像源
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

# install packages
FROM base AS packages

WORKDIR /app/web

COPY package.json .
COPY package-lock.json .

# 设置 npm 配置以提高稳定性
RUN npm config set registry https://registry.npmmirror.com/

# 使用 cnpm 安装依赖，更稳定
RUN npm install -g cnpm --registry=https://registry.npmmirror.com/ \
    && cnpm install --no-audit --no-fund --ignore-scripts

# build resources
FROM base AS builder
WORKDIR /app/web
COPY --from=packages /app/web/ .
COPY . .

ARG NODE_ENV=production
ARG DEPLOY_ENV=production
ARG NEXT_PUBLIC_API_HOST

# 设置环境变量
ENV NODE_ENV=${NODE_ENV}
ENV DEPLOY_ENV=${DEPLOY_ENV}
ENV NEXT_PUBLIC_API_HOST=${NEXT_PUBLIC_API_HOST}

# 使用 npm 构建
RUN NODE_ENV=${NODE_ENV} npm run build

# production stage
FROM base AS production
ENV PORT=3000
ENV HOSTNAME=127.0.0.1

# set timezone
ENV TZ=Asia/Shanghai
RUN ln -s /usr/share/zoneinfo/${TZ} /etc/localtime \
    && echo ${TZ} > /etc/timezone

# global runtime packages
# 使用 cnpm 安装全局包
RUN npm install -g cnpm --registry=https://registry.npmmirror.com \
    && cnpm install -g pm2 \
    && npm cache clean --force

WORKDIR /app/web
COPY --from=builder /app/web/.next/standalone ./
COPY --from=builder /app/web/.next/static ./.next/static

COPY docker/pm2.json ./pm2.json
COPY docker/entrypoint.sh ./entrypoint.sh

EXPOSE 3000
ENTRYPOINT ["/bin/sh", "./entrypoint.sh"]
