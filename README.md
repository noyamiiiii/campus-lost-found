# 校园失物招领平台

校园内的失物招领与寻物协作平台。当前进度：**W1 骨架阶段（执行手册第 0–3 节）**。

三个可独立启动的服务：

| 服务 | 技术 | 端口 | 说明 |
| --- | --- | --- | --- |
| `frontend` | Vue 3 + TypeScript + Vite | 5173 | 用户端界面，开发期通过 Vite 代理访问后端 |
| `backend` | Spring Boot 3.5 / Java 21 | 8081 | 业务 API、鉴权、事务 |
| `ai-service` | FastAPI / Python 3.13 | 8000 | 图像向量与识别（W1 仅有健康检查） |

开发依赖：MySQL 8.4、Redis 7.4、MinIO。**W1 阶段后端不连接数据库，不开 Docker 也能启动。**

## 目录结构

```text
campus-lost-found/
├─ docs/                 设计文档、决策记录、图表、测试记录
│  ├─ decisions/         Day 0 决策与周会结论
│  ├─ adr/               架构决策记录
│  ├─ diagrams/          用例图、状态图、ER、时序图等
│  ├─ requirements/      D1 / D2 需求基线
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

### 1. 前端（最常用）

```powershell
cd G:\软件工程\campus-lost-found\frontend
pnpm install
pnpm dev
```

浏览器打开 <http://localhost:5173>。端口被占用时 Vite 会自动换端口，以终端输出为准。

### 2. 后端

```powershell
cd G:\软件工程\campus-lost-found\backend
$s = "G:\软件工程\campus-lost-found\.local\maven-settings.xml"
mvn -gs $s -s $s spring-boot:run
```

（`-gs` / `-s` 只是绕过本机损坏的全局 Maven 配置；在 IDEA 中直接运行 `CampusLostFoundApplication` 即可，不依赖这段参数。）

验证：<http://localhost:8081/api/v1/health>

```json
{"code":0,"message":"ok","data":{"status":"UP","service":"campus-lost-found-backend","version":"0.1.0"}}
```

不启动 MySQL / Redis 也能正常运行，W1 尚未引入数据库依赖。

### 3. AI 服务

```powershell
cd G:\软件工程\campus-lost-found\ai-service
py -3.13 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload --port 8000
```

验证：<http://localhost:8000/health>

### 4. 开发依赖（可选）

```powershell
cd G:\软件工程\campus-lost-found
Copy-Item deploy\.env.example deploy\.env
docker compose -f deploy\docker-compose.dev.yml up -d
docker compose -f deploy\docker-compose.dev.yml ps
```

| 组件 | 地址 | 开发默认账号 |
| --- | --- | --- |
| MySQL | `localhost:3306`，库 `lostfound` | `root` / `clf_dev_root`，应用账号 `lostfound` / `clf_dev_pass` |
| Redis | `localhost:6379` | 密码 `clf_dev_redis` |
| MinIO | API `localhost:9000`，控制台 <http://localhost:9001> | `clfadmin` / `clf_dev_minio` |

以上密码**仅用于本地开发**，任何部署环境必须替换，`.env` 不提交。

## 用 IDE 打开

- **IntelliJ IDEA**：`File → Open` 选择 `backend/pom.xml`，以 Project 方式打开；Project SDK 选 JDK 21。根目录可另开一个窗口查看文档。
- **VSCode**：打开 `G:\软件工程\campus-lost-found` 整个文件夹。工作区已带 `.vscode/settings.json` 与推荐扩展。
- **draw.io**：`docs/diagrams` 下的 `.drawio` 文件可直接双击打开。
- **PlantUML**：`.puml` 文件在 IDEA / VSCode 中预览，需要本机 `java` 与 Graphviz（时序图、用例图可不装 Graphviz）。

## 端口与接口

| 接口 | 说明 |
| --- | --- |
| `GET /api/v1/health` | 后端健康检查（统一响应结构） |
| `GET /health` | AI 服务健康检查 |
| `GET /actuator/health` | Spring Boot Actuator |

前端开发服务器把 `/api` 代理到 `8081`、`/ai` 代理到 `8000`，因此浏览器端不涉及跨域配置。

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

**端口不通时页面显示“未连接”**

这是预期行为：首页的服务状态卡会分别探测后端与 AI 服务，未启动即显示离线，不影响前端本身运行。

## W1 验收清单

- [x] 目录结构与 Git 仓库就绪
- [x] 前端可启动，首页为真实应用界面而非落地页
- [x] 后端 `/api/v1/health` 返回统一结构
- [x] AI `/health` 可访问
- [x] Compose 文件可解析（`docker compose config`）
- [ ] 三位成员各自电脑按 README 跑通（需团队确认）
- [ ] 首次提交与分支约定落地（需团队确认）

详细验证数据与截图见 [W1 验收记录](docs/test/README.md)。

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

