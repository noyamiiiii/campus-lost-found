# 用 Docker 跑这套系统

目标：换一台装好 Docker Desktop 的电脑，`clone` 下来一条命令起全栈，不装 JDK / Node / Python / MySQL。

## 起停命令

```powershell
cd campus-lost-found\deploy

# 首次（会构建镜像，5–10 分钟）
docker compose -f docker-compose.prod.yml up -d --build

# 之后
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml logs -f backend    # 看后端日志
docker compose -f docker-compose.prod.yml down               # 停掉，保留数据
docker compose -f docker-compose.prod.yml down -v            # 停掉并删除数据库卷
```

打开 <http://localhost:8088> 即可；后端不单独暴露端口，所有请求经 nginx 转发。

## 容器做了什么

| 容器 | 基础镜像 | 作用 | 对外 |
| --- | --- | --- | --- |
| `clf-frontend` | `nginx:1.27-alpine` | 托管 `vue-tsc + vite build` 的产物，并把 `/api`、`/files` 反代到后端，`/ai` 反代到 AI 服务 | `8088:80` |
| `clf-backend` | `eclipse-temurin:21-jre-alpine` | Spring Boot 应用，`SPRING_PROFILES_ACTIVE=local`（Flyway 自动建表并写入 `db/dev` 的演示账号） | 仅内网 8081 |
| `clf-ai` | `python:3.12-slim` | FastAPI 的文本相似度 / 关键词抽取服务 | 仅内网 8000 |
| `clf-mysql-prod` | `mysql:8.4.3` | 数据库，数据在卷 `clf_mysql-data` | 仅内网 3306 |
| `clf-minio`（可选） | `minio/minio` | 对象存储，仅在 `--profile s3` 时启动 | `9000` / `9001` |

三个镜像都是多阶段构建：Maven 依赖、`node_modules`、pip 包都留在构建阶段，运行阶段只带
最终产物。构建脚本见 `deploy/docker/`：

- `backend.Dockerfile`：`maven:3.9-eclipse-temurin-21` 编译 → JRE 里只放一个 jar
- `frontend.Dockerfile`：`node:20-alpine` + `corepack pnpm` 构建 → nginx 托管 `dist`
- `ai.Dockerfile`：`requirements.txt` 三个依赖装完即用，带 `HEALTHCHECK`
- `maven-settings.xml`：默认走阿里云镜像，海外构建加 `--build-arg USE_MIRROR=no`
- `nginx.conf`：SPA 回退 `index.html`、`client_max_body_size 32m`（对应上传限制）、静态资源长缓存

## 常用开关

改配置：复制 `deploy/.env.docker.example` 为 `deploy/.env.docker`，然后

```powershell
docker compose -f docker-compose.prod.yml --env-file .env.docker up -d
```

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `CLF_HTTP_PORT` | `8088` | 浏览器访问端口 |
| `CLF_MYSQL_PASSWORD` / `CLF_MYSQL_ROOT_PASSWORD` | `clf_dev_pass` / `clf_dev_root` | 数据库密码 |
| `CLF_JWT_SECRET` | 占位串 | **对外分享前必须换掉**，`openssl rand -base64 32` 生成 |
| `STORAGE_PROVIDER` | `local` | 图片存容器卷；改成 `s3` 走 MinIO |
| `AUTH_EXPOSE_SMS_CODE` | `true` | 演示用固定验证码 `123456`；正式环境应为 `false` |

只重建某一个服务：

```powershell
docker compose -f docker-compose.prod.yml up -d --build backend
```

换代码后不想重建：

```powershell
docker compose -f docker-compose.prod.yml restart backend
```

## 用 MinIO 存图片

```powershell
# 1. 起 MinIO
docker compose -f docker-compose.prod.yml --profile s3 up -d minio

# 2. 建桶（控制台 http://localhost:9001，账号 clfadmin / clf_dev_minio）
#    或：docker run --rm --network clf_default minio/mc \
#        sh -c "mc alias set m http://minio:9000 clfadmin clf_dev_minio && mc mb -p m/lostfound && mc anonymous set download m/lostfound"

# 3. 让后端切过去（publicBaseUrl 必须是浏览器能访问的地址）
$env:STORAGE_PROVIDER='s3'
$env:STORAGE_S3_PUBLIC_BASE_URL='http://localhost:8088/s3'
docker compose -f docker-compose.prod.yml up -d backend
```

注意：MinIO 的图片 URL 由后端返回，要让浏览器直接打开就需要 `publicBaseUrl` 可达。
更省事的做法是保持 `STORAGE_PROVIDER=local`（默认），图片由后端从卷里读出来经 nginx 转发。

## 排查

| 现象 | 原因 / 处理 |
| --- | --- |
| `up` 卡在 `mysql` unhealthy | 数据库首次初始化要 20–40 秒，等健康检查通过；看 `logs mysql` |
| 打开 8088 白屏 | 前端构建失败，`docker compose ... logs frontend`；多半是 `pnpm-lock.yaml` 与 `package.json` 不一致 |
| 构建报 `packages field missing or empty` | `frontend/pnpm-workspace.yaml` 只有 `allowBuilds` 段（pnpm 10 语法），pnpm 9 会当成工作区根报错 —— 镜像里已 `rm` 掉，本地用 pnpm 10 不受影响 |
| 构建报 `Cannot find module .../vue-tsc/bin/vue-tsc.js` | 仓库根缺 `.dockerignore`，把本机 `node_modules` 拷进镜像盖掉了容器里装好的依赖；本项目已加 `.dockerignore` |
| 接口 502 | 后端还没起来（健康检查 40 秒 start_period）；或 `MYSQL_HOST` 没指到 `mysql` |
| 启动日志 `No migrations found` | `pom.xml` 用 `${project.basedir}/../db` 引迁移脚本，镜像里 pom 必须位于 `/src/backend`、db 位于 `/src/db`（`backend.Dockerfile` 已按此布局） |
| 图片 404 | `STORAGE_LOCAL_ROOT` 与卷路径不一致；默认 `/app/data/files` 已挂到卷 `clf_uploads` |
| 构建时 Maven/pip 超时 | 换镜像源：`--build-arg USE_MIRROR=no` 或 `--build-arg PIP_INDEX_URL=https://pypi.org/simple` |

## 容器之间怎么连

- 前端 `nginx` 把 `/api`、`/files` 反代到 `backend:8081`，`/ai` 反代到 `ai:8000`，浏览器只认 8088 一个端口
- 后端通过 `AI_BASE_URL=http://ai:8000` 调 AI 服务（`docker-compose.prod.yml` 里已设），AI 挂了也不影响主流程
- 后端通过 `MYSQL_HOST=mysql` 连数据库；图片默认写卷 `clf_uploads`
- 因为都在 `clf_default` 网络里，容器名就是主机名，换机器不用改配置

## 在这台机器上验证过的内容（2026-11-11）

```text
docker compose -f docker-compose.prod.yml up -d --build   # 三个镜像全部构建成功
clf-mysql-prod healthy / clf-backend healthy / clf-ai healthy / clf-frontend up
GET  http://localhost:8088/api/v1/health        → code=0, version=0.2.0
GET  http://localhost:8088/ai/health            → ok, v0.2.0
GET  http://localhost:8088/api/v1/posts         → 6 条种子数据（Flyway 跑到 V6）
POST /api/v1/files（multipart）                  → 640x480 PNG，返回 /files/item/202611/xxx.png
POST /api/v1/posts（带 imageIds）                → 详情返回 images，GET 该 URL 200 / image/png
GET  /api/v1/posts/{id}/matches                 → reasons 里含「语义相似 15%」（AI 参与打分）
POST /api/v1/auth/sms-code + /login             → 演示账号可登录
```

## 给别人演示的最小说明

1. 装 Docker Desktop；
2. `git clone` 仓库；
3. `deploy` 目录执行 `docker compose -f docker-compose.prod.yml up -d --build`；
4. 浏览器打开 <http://localhost:8088>，用演示账号登录（手机号 `13800000001`，验证码 `123456`）。