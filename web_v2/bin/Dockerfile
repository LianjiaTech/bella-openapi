# base image
FROM harbor.intra.ke.com/keci/node:20.11-alpine3.19 AS base

# if you located in China, you can use aliyun mirror to speed up
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

RUN apk add --no-cache tzdata

# install packages
FROM base AS packages

WORKDIR /app/web

COPY ../package.json .
COPY ../package-lock.json .

# if you located in China, you can use taobao registry to speed up
RUN npm install --frozen-lockfile --registry https://registry.npmmirror.com/

# build resources
FROM base AS builder
WORKDIR /app/web

RUN rm -f .env.production
COPY bin/.env.production .
COPY --from=packages /app/web/ .
COPY .. .

ARG NODE_ENV=production
ARG DEPLOY_ENV=PRODUCTION

# 设置环境变量
ENV NODE_ENV=${NODE_ENV}
ENV DEPLOY_ENV=${DEPLOY_ENV}
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
# install cnpm and use it to install pm2
RUN npm install -g cnpm --registry=https://registry.npmmirror.com \
    && cnpm install -g pm2 \
    && npm cache clean --force

WORKDIR /app/web
COPY --from=builder /app/web/.next/standalone ./
COPY --from=builder /app/web/.next/static ./.next/static

COPY ../docker/pm2.json ./pm2.json
COPY ../docker/entrypoint.sh ./entrypoint.sh

EXPOSE 3000
ENTRYPOINT ["/bin/sh", "./entrypoint.sh"]
