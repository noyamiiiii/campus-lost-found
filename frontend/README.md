# Frontend（Vue 3 + TypeScript + Vite）

W1 骨架阶段：完成应用外壳、检索筛选、详情抽屉和三端服务状态自检。

## 启动

```powershell
cd G:\软件工程\campus-lost-found\frontend
pnpm install
pnpm dev
```

浏览器打开 <http://localhost:5173>。

## 常用命令

| 命令 | 作用 |
| --- | --- |
| `pnpm dev` | 启动开发服务器，热更新 |
| `pnpm build` | 类型检查 + 生产构建到 `dist/` |
| `pnpm preview` | 本地预览生产构建（4173） |
| `pnpm typecheck` | 只做类型检查 |

## 目录

```text
src/
├─ api/          与后端交互的封装（W9 起逐步补充）
├─ components/   可复用组件
├─ data/         W1 演示数据，接口接入后删除
├─ router/       路由表
├─ stores/       Pinia 状态
├─ styles/       全局样式与设计变量
├─ types/        与 OpenAPI 对齐的类型定义
└─ views/        页面
```

## 开发代理

`vite.config.ts` 中把 `/api` 代理到 `http://localhost:8081`、`/ai` 代理到 `http://localhost:8000`。
因此前端代码里直接写 `/api/v1/health` 即可，不需要处理跨域。

需要指向别的后端时，启动前设置 `VITE_API_TARGET`（例如 `$env:VITE_API_TARGET="http://localhost:9000"`）。

## 常见问题

**`pnpm dev` 报 `无法加载文件 pnpm.ps1`**

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

或改用 `pnpm.cmd dev`。

**页面打开是空白**

先看浏览器控制台与终端报错；再确认当前目录是 `frontend`（`pnpm dev` 必须在含 `package.json` 的目录执行）。

**页面右上显示“未连接”**

表示后端或 AI 服务没启动，前端本身是正常的。启动对应服务后点服务状态卡的刷新按钮即可。

