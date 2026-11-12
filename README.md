# 校园失物招领平台

校园内的失物招领与寻物协作平台。当前进度：**核心链路已贯通**。发布（含多图）、候选匹配、认领闭环、站内通知、管理后台，以及图片理解（分类/主色/敏感内容/自动描述）与以图搜图都已跑通三端。

三个可独立启动的服务：

| 服务 | 技术 | 端口 | 说明 |
| --- | --- | --- | --- |
| `frontend` | Vue 3 + TypeScript + Vite | 5173 | 用户端界面，开发期通过 Vite 代理访问后端 |
| `backend` | Spring Boot 3.5 / Java 21 | 8081 | 业务 API、鉴权、事务 |
| `ai-service` | FastAPI / Python 3.13 | 8000 | 文本相似度、候选匹配打分、关键词抽取 |

开发依赖：MySQL 8.4、Redis 7.4、MinIO。默认单元测试不依赖外部服务；真实业务接口使用 `local` 配置连接这些依赖。

## 目录结构

```text
campus-lost-found/
├─ docs/                 设计文档、决策记录、图表、测试记录
│  ├─ decisions/         Day 0 决策与周会结论
│  ├─ adr/               架构决策记录
│  ├─ diagrams/          用例图、状态图、ER、时序图等
│  ├─ requirements/      需求基线（当前已冻结）
│  ├─ archive/           已结束阶段的历史材料
│  ├─ prototype/         原型导出与页面说明
│  ├─ meetings/          评审与周会记录
│  └─ test/              测试计划、缺陷、验收记录
├─ contracts/            OpenAPI 契约（前后端唯一接口依据）
├─ frontend/             Vue 3 用户端
├─ backend/              Spring Boot 3（用 IDEA 打开此目录的 pom.xml）
├─ ai-service/           FastAPI 向量服务
├─ deploy/               docker compose 与环境变量样例
└─ scripts/              环境自检脚本
```

## 环境要求

| 工具 | 要求 | 本机实测 |
| --- | --- | --- |
| JDK | 21（Spring Boot 3.5 支持 17–25） | 21.0.10 |
| Maven | 3.9+（或使用 IDEA 内置 Maven） | 4.0.0-rc-5，见下方说明 |
| Node.js | 20 LTS 及以上 | 22.22.1 |
| pnpm | 9+ | 11.25.0 |
| Python | 3.11+ | 3.13.14（`py -3.13`） |
| Docker Desktop | 用于 MySQL / Redis / MinIO | 客户端 29.9.2，守护进程需手动启动 |

> 本机 `mvn` 是 Maven 4.0.0-rc-5 预发布版，且全局 `conf\settings.xml` 目前有语法错误（见“常见问题”）。修好之前，在 `backend` 目录下用临时配置构建：
>
> ```powershell
> $s = "G:\软件工程\campus-lost-found\.local\maven-settings.xml"
> mvn -gs $s -s $s test
> ```
>
> IDEA 用户也可以直接用 IDEA 内置 Maven 3.9，不依赖这份全局配置。

## 启动顺序

### 0. 一条命令跑全栈（Docker，给别人演示时最省事）

```powershell
cd G:\软件工程\campus-lost-found\deploy
docker compose -f docker-compose.prod.yml up -d --build
```

浏览器打开 <http://localhost:8088>。镜像里包含 MySQL、后端、AI 服务和一个托管前端的
nginx，首次构建约 5–10 分钟（Maven/pnpm 依赖），之后启动只要几秒。数据库与上传的图片
放在 Docker 卷里，`docker compose -f docker-compose.prod.yml down` 不会删数据，
要连数据一起清掉加 `-v`。

细节（端口、密钥、切 MinIO、只重建某个服务）见 [deploy/DOCKER.md](deploy/DOCKER.md)。

### 1. 前端（最常用）

```powershell
cd G:\软件工程\campus-lost-found\frontend
npm install
npm run dev
```

浏览器打开 <http://localhost:5173>。端口被占用时 Vite 会自动换端口，以终端输出为准。
如果本机使用 `pnpm`，等价的命令是 `pnpm install` 与 `pnpm dev`。

### 2. 后端

```powershell
cd G:\软件工程\campus-lost-found\backend
$s = "G:\软件工程\campus-lost-found\.local\maven-settings.xml"
mvn -gs $s -s $s spring-boot:run
```

（`-gs` / `-s` 只是绕过本机损坏的全局 Maven 配置；在 IDEA 中直接运行 `CampusLostFoundApplication` 即可，不依赖这段参数。）

验证：<http://localhost:8081/api/v1/health>

```json
{"code":0,"message":"ok","data":{"status":"UP","service":"campus-lost-found-backend","version":"0.2.0"},"traceId":"..."}
```

默认配置只保证健康检查等基础接口可启动，不执行数据库迁移。运行真实业务接口时先启动 MySQL，并使用 `local` 配置：

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$s = "G:\软件工程\campus-lost-found\.local\maven-settings.xml"
mvn -gs $s -s $s spring-boot:run
```

`local` 配置会自动执行 `db/migration` 与 `db/dev` 下的 Flyway 脚本。空库首次启动会建表并写入本地演示数据。

### 3. AI 服务

```powershell
cd G:\软件工程\campus-lost-found\ai-service
py -3.13 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload --port 8000
```

验证：<http://localhost:8000/health>

它不只是一个健康检查：提供图片理解与文本相似两类能力，且都被后端真实调用。

| 接口 | 用途 | 谁在调用 |
| --- | --- | --- |
| `POST /analyze` | 图片理解：分类、主色、敏感内容、描述与标题、图片向量 | 后端 `AiVisionClient` → 上传后写入 `file_object` |
| `POST /embed/image` | 单张图片的向量 | 预留给发布后的异步重算 |
| `POST /describe` | 由分类/颜色/地点拼描述与标题 | 与 `/analyze` 同源，可单独调用 |
| `POST /similar` | 一段文本对一组候选打分（0~1）| 后端 `AiTextClient` → 帖子详情「可能的匹配」 |
| `POST /similar/vectors` | 一组向量里找最相似的 | 以图搜图的评分口径参考 |
| `POST /embed/text` | 文本向量 | 预留 |
| `POST /similar/pair` | 两条文本的相似度 | 留给后续「重复发帖合并」 |
| `POST /tags` | 关键词抽取 | 留给发布表单的标签联想 |

后端通过 `AI_BASE_URL`（默认 `http://127.0.0.1:8000`）、`AI_TIMEOUT_MS`（默认 800）、
`AI_VISION_TIMEOUT_MS`（默认 4000）、`AI_ENABLED` 控制；**AI 服务没启动也不影响主流程**——
匹配退回本地中文二元组打分，上传仍成功、只是 `ai.status` 记为 `FAILED`。首页服务状态卡里的
「AI 服务」探的就是 `/ai/health`。

模型文件在 `ai-service/models`（不入库，`python scripts/fetch_models.py` 下载约 14 MB）：
`mobilenetv2-7.onnx` 做分类与图片向量，`face_detection_yunet_2023mar.onnx` 做人脸/证件区域检测，
分类只在本地 CPU 推理，没有外部大模型调用。

### 4. 开发依赖（可选）

```powershell
cd G:\软件工程\campus-lost-found
Copy-Item deploy\.env.example deploy\.env
docker compose -f deploy\docker-compose.dev.yml up -d
docker compose -f deploy\docker-compose.dev.yml ps
```

| 组件 | 地址 | 开发默认账号 |
| --- | --- | --- |
| MySQL | `localhost:3307`，库 `lostfound` | `root` / `clf_dev_root`，应用账号 `lostfound` / `clf_dev_pass` |
| Redis | `localhost:6379` | 密码 `clf_dev_redis` |
| MinIO | API `localhost:9000`，控制台 <http://localhost:9001> | `clfadmin` / `clf_dev_minio` |

本机已有 MySQL 服务占用 `3306`，因此项目容器映射到 `3307`。`deploy/.env` 已作本地覆盖，不提交；以上密码**仅用于本地开发**，任何部署环境必须替换。

## 用 IDE 打开

- **IntelliJ IDEA**：`File → Open` 选择 `backend/pom.xml`，以 Project 方式打开；Project SDK 选 JDK 21。根目录可另开一个窗口查看文档。
- **VSCode**：打开 `G:\软件工程\campus-lost-found` 整个文件夹。工作区已带 `.vscode/settings.json` 与推荐扩展。
- **draw.io**：`docs/diagrams` 下的 `.drawio` 文件可直接双击打开。
- **PlantUML**：`.puml` 文件在 IDEA / VSCode 中预览，需要本机 `java` 与 Graphviz（时序图、用例图可不装 Graphviz）。

## 端口与接口

| 接口 | 说明 |
| --- | --- |
| `GET /api/v1/health` | 后端健康检查（统一响应结构） |
| `GET /api/v1/categories` | 启用的物品分类 |
| `GET /api/v1/locations` | 启用的校园地点，发帖时选择 |
| `GET /api/v1/posts` | 公开帖子游标分页、类型/分类/关键词筛选 |
| `GET /api/v1/posts/{publicId}` | 公开帖子详情 |
| `GET /api/v1/posts/{publicId}/matches` | 候选匹配（相反类型、按类别/地点/描述重合打分，最多 5 条） |
| `GET /api/v1/posts/{publicId}/similar-images` | 以图搜图：按封面图向量找外观相似的帖子 |
| `POST /api/v1/posts` | 发布寻物/招领（需 Bearer 令牌），可带 `imageIds` 最多 6 张 |
| `GET /api/v1/posts/mine` | 自己发布的帖子（需 Bearer 令牌） |
| `POST /api/v1/files` | 上传图片（multipart 字段 `file`，可选 `kind`/`location`，≤5 MB），返回 `ai` 识别结果 |
| `GET /files/**` | 本地存储模式下读取已上传图片 |
| `POST /api/v1/posts/{publicId}/claims` | 发起认领（归属说明必填，可附佐证图） |
| `GET /api/v1/claims/mine` | 我发起的认领 |
| `GET /api/v1/claims/received` | 我收到的认领 |
| `POST /api/v1/claims/{publicId}/confirm` | 发布者同意认领 |
| `POST /api/v1/claims/{publicId}/reject` | 发布者驳回 |
| `POST /api/v1/claims/{publicId}/reopen` | 申请人重新提交（仅驳回后） |
| `POST /api/v1/claims/{publicId}/cancel` | 申请人撤回 |
| `POST /api/v1/claims/{publicId}/complete` | 交接完成核销 |
| `GET /api/v1/notifications` | 站内通知列表 |
| `GET /api/v1/notifications/unread-count` | 未读数 |
| `POST /api/v1/notifications/read-all` | 全部标记已读 |
| `POST /api/v1/auth/sms-code` | 发送注册/登录验证码（本地固定为 `123456`） |
| `POST /api/v1/auth/register` | 学号 + 手机号 + 验证码注册，成功即返回登录会话 |
| `POST /api/v1/auth/login` | 已注册账号登录，返回访问令牌并写入刷新令牌 Cookie |
| `POST /api/v1/auth/refresh` | 轮换刷新令牌并恢复会话 |
| `POST /api/v1/auth/logout` | 注销刷新会话并清除 Cookie |
| `GET /api/v1/users/me` | 获取当前登录用户资料（需 Bearer 令牌） |
| `PUT /api/v1/users/me` | 修改当前用户资料（需 Bearer 令牌） |
| `GET /api/v1/admin/summary` | 管理后台统计（需 ADMIN 角色） |
| `GET /api/v1/admin/users` | 账号分页查询（需 ADMIN 角色） |
| `PATCH /api/v1/admin/users/{publicId}/status` | 封禁/解封账号（需 ADMIN 角色） |
| `GET /health` | AI 服务健康检查 |
| `POST /similar` | 文本候选匹配（返回 score 与共享词元） |
| `POST /similar/pair` | 两条文本的相似度 |
| `POST /tags` | 关键词抽取 |
| `GET /actuator/health` | Spring Boot Actuator |

前端开发服务器把 `/api` 代理到 `8081`、`/ai` 代理到 `8000`、`/files` 代理到 `8081`（本地图片），因此浏览器端不涉及跨域配置。

### 图片存储

`app.storage.provider` 决定图片写到哪里，默认 `local`：图片保存到 `backend/data/files`，由后端把 `/files/**` 映射成静态资源，开发机不必启动 Docker。要改用 MinIO / 兼容 S3 的对象存储时，把 `deploy/.env`（或启动时环境变量）设成：

| 环境变量 | 说明 |
| --- | --- |
| `STORAGE_PROVIDER=s3` | 切换到对象存储实现 |
| `STORAGE_S3_ENDPOINT` | 例如 `http://127.0.0.1:9000` |
| `STORAGE_S3_BUCKET` | 桶名，默认 `lostfound`（需事先创建） |
| `STORAGE_S3_ACCESS_KEY` / `STORAGE_S3_SECRET_KEY` | 与 `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` 对应 |
| `STORAGE_S3_PUBLIC_BASE_URL` | 图片可被浏览器访问的地址，例如 `http://127.0.0.1:9000/lostfound` |

对象存储不可用时后端仍能启动，图片上传会返回 `503 / 50301`，其他接口不受影响。

本地演示账号：学号 `20260001`，手机号 `13800000001`，固定验证码 `123456`。验证码需要先点击“获取验证码”才会生成并自动填入，有效期为 5 分钟；改了学号或手机号要重新获取。新账号请在登录弹窗中切换到“注册”，注册成功后会自动登录并引导完善姓名与院系。

管理员演示账号：学号 `admin`，手机号 `13900000000`，验证码同样是先点击“获取验证码”再自动填入。登录后右上角账号菜单会出现“管理后台”，可以查看账号、发布数量并封禁/解封账号。

## 常见问题

**`pnpm` 无法运行，提示禁止执行脚本**

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

或临时改用 `pnpm.cmd dev`。

**`docker compose` 报 `cannot find the file specified`**

Docker 客户端已安装但 Docker Desktop 未启动。打开 Docker Desktop，等状态变为 Running 后重试。若提示 WSL 相关错误，执行 `wsl --update` 后重启。

**`mvn` 报 `Non-parseable settings ... expected </mirrors>`**

`F:\maven\apache-maven-4.0.0-rc-5-bin\apache-maven-4.0.0-rc-5\conf\settings.xml` 第 149 行的 `<mirrors>` 没有闭合，且其中混入了 settings 不允许的 `<repositories>` / `<pluginRepositories>` 段落。修复方式：

1. 备份该文件（例如 `settings.xml.bak`）。
2. 保留 `<mirror>...</mirror>`，紧随其后补一行 `</mirrors>`。
3. 删除误放入 `<mirrors>` 内的 `<repositories>` 与 `<pluginRepositories>` 两段（镜像已覆盖中央仓库，不需要它们）。
4. 保存为 UTF-8 后执行 `mvn -B test`（只跑 `mvn -v` 不会解析 settings，测不出这个问题）。

**端口被占用**

```powershell
netstat -ano | findstr :8081
```

前端会自动换端口；后端默认端口是 8081（8080 常被 Steam 等软件占用），可在 `application.yml` 改，或启动前设置 `$env:SERVER_PORT="9000"`。

代理工具（如 mihomo / Clash）可能把 8081 用作出站连接的本地端口。此时 `netstat` 看到的是 `ESTABLISHED` 或 `TIME_WAIT` 而不是 `LISTEN`，但 Tomcat 仍会报 `Port 8081 was already in use`。可以临时换端口，并让前端代理指向同一地址：

```powershell
# 后端终端
$env:SERVER_PORT = "8091"

# 前端终端
$env:VITE_API_TARGET = "http://localhost:8091"
```

**端口不通时页面显示“未连接”**

这是预期行为：首页的服务状态卡会分别探测后端与 AI 服务，未启动即显示离线，不影响前端本身运行。

## 已完成基线

- [x] 目录结构与 Git 仓库就绪
- [x] 前端可启动，首页为真实应用界面而非落地页
- [x] 后端 `/api/v1/health` 返回统一结构
- [x] AI `/health` 可访问
- [x] MySQL 8.4 空库可由 Flyway 一键迁移并写入开发种子
- [x] 分类、帖子列表、游标分页、关键词检索、详情接口通过集成测试
- [x] 首页改为真实 API，覆盖加载、空结果、失败重试、加载更多与详情抽屉
- [x] 验证码登录、JWT 访问令牌、HttpOnly 刷新令牌轮换与退出登录
- [x] 独立注册入口、学号/手机号重复校验与注册后自动登录
- [x] 个人资料读取与修改，登录弹窗、账号菜单和个人中心页面
- [x] Compose 文件可解析（`docker compose config`）
- [x] 图片上传（本地磁盘 / MinIO 两种实现）与发帖图集、列表封面、详情图集
- [x] 认领闭环：发起、驳回、重新提交、撤回、确认、交接核销，帖子状态随之流转
- [x] 站内通知：认领各环节落库，顶栏未读角标与通知中心
- [x] 后端集成测试 24 个用例（认证、帖子、认领、文件、管理端、健康检查）
- [ ] 三位成员各自电脑按 README 跑通（需团队确认）
- [ ] 首次提交与分支约定落地（需团队确认）

详细验证数据与截图见 [测试与验收记录](docs/test/README.md)。当前阶段结论见 [开发阶段基线](docs/decisions/2026-11-02-development-baseline.md)。

首次提交前先设置身份（仓库内或全局均可）：

```powershell
git config user.name "你的名字"
git config user.email "你的邮箱"
git add .
git commit -m "chore: 初始化校园失物招领项目骨架"
```

## 文档

- [项目执行步骤](../校园失物招领平台_项目执行步骤.md)
- [需求分析与项目设计](../校园失物招领平台_需求分析与项目设计.md)
- [Day 0 决策](docs/decisions/day0-decisions.md)
- [ADR 0001：单仓多应用与技术选型](docs/adr/0001-monorepo-and-runtime.md)

