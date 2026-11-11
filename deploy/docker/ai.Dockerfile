# AI 服务镜像：只装 requirements 里的三个依赖，启动即刻可用
#
# 构建上下文是仓库根目录：
#   docker build -f deploy/docker/ai.Dockerfile -t clf-ai .

FROM python:3.12-slim
WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    TZ=Asia/Shanghai

# 国内网络可传 --build-arg PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
ARG PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
ENV PIP_INDEX_URL=${PIP_INDEX_URL}

COPY ai-service/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY ai-service/app ./app
COPY ai-service/tests ./tests

RUN useradd -m -u 10001 app && chown -R app:app /app
USER app

EXPOSE 8000

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=5 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=2).status == 200 else 1)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]