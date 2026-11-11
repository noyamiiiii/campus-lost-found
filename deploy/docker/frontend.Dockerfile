# 前端镜像：pnpm 构建静态资源，交给 nginx 同时做静态站与反向代理
#
# 构建上下文是仓库根目录：
#   docker build -f deploy/docker/frontend.Dockerfile -t clf-frontend .

FROM node:20-alpine AS build
WORKDIR /app

# 锁定 pnpm 主版本，避免不同机器的 corepack 解析出不一致的版本
RUN corepack enable && corepack prepare pnpm@9.15.4 --activate

COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY frontend/ ./
# frontend/pnpm-workspace.yaml 只有 allowBuilds 段（pnpm 10 语法，缺 packages 字段），
# pnpm 9 会把它当成工作区根并报 "packages field missing or empty"，构建阶段删掉即可。
RUN rm -f pnpm-workspace.yaml && pnpm run build

FROM nginx:1.27-alpine
COPY deploy/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80