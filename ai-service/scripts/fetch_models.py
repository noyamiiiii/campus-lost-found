"""下载图片模型到 ai-service/models/。

服务启动后首次调用 ``/analyze`` 也会自动下载，这个脚本是给「构建镜像」和
「离线环境预先准备」用的：在能联网的机器上跑一次，把 models/ 一起带走即可。

用法：
    python scripts/fetch_models.py
    python scripts/fetch_models.py --dir D:\\models     # 指定目录
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app import vision  # noqa: E402  - 需要先加项目根到 sys.path


def main() -> int:
    parser = argparse.ArgumentParser(description="下载 AI 服务所需的模型文件")
    parser.add_argument("--dir", help="模型目录，默认 ai-service/models")
    args = parser.parse_args()

    if args.dir:
        import os

        os.environ["AI_MODEL_DIR"] = args.dir

    ready = vision.ensure_models()
    directory = vision.model_dir()
    print(f"模型目录：{directory}")
    for name, ok in ready.items():
        print(f"  {name:<12} {'OK' if ok else 'MISSING'}")
    if not ready.get("classifier"):
        print("\n分类模型没就绪：检查网络，或手动把 mobilenetv2-7.onnx 放到模型目录。")
        return 1
    print("\n完成。服务在没有网络时也能直接用这些文件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())