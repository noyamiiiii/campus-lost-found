# AI Service（FastAPI / Python 3.13）

W1 只提供 `/health`，**不加载 CLIP 模型**，启动时间是秒级。图像向量能力按执行手册在 W8 做 go/no-go 验证后再接入。

## 创建虚拟环境

```powershell
cd G:\软件工程\campus-lost-found\ai-service
py -3.13 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

> 本机默认 `python` 是 3.8，不要用它建虚拟环境。统一使用 `py -3.13`。

## 启动

```powershell
python -m uvicorn app.main:app --reload --port 8000
```

- 健康检查：<http://localhost:8000/health>
- 接口文档：<http://localhost:8000/docs>

## 运行测试

```powershell
python -m pip install -r requirements-dev.txt
python -m pytest -q
```

## 约定

- 该服务只做“图像 → 向量”和“向量相似度”两件事，业务判断（是否同一物品、能否认领）在后端。
- 接口返回结构、超时与降级策略以 `docs/` 与 `contracts/` 为准。
- 模型文件不入库，权重下载脚本放 `scripts/`，并在 README 记录体积与来源。

