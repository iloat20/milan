#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
generate_zhipu.py — 智谱 CogView-3-Flash 生成角色立绘（国内直连免费通道，2026-08-25）

背景：Pollinations 免费档到顶（源图 627x940 + 半写实厚涂 + 锚点服从度差），
用户定调「只要 2D、卡牌人物感、精致」→ 切智谱 CogView-3-Flash（完全免费、国内直连）。
2026-08-26 v2.3：全局写实化——STYLE_PREFIX/ZH_STYLE 改写实口径（对齐 06 号规范 §7）。

与 generate_free.py 的差异：
- prompt 复用 build_free_prompt()（STYLE_PREFIX 前置 + 主体/姿态段），
  但把「transparent background」换成「纯白背景」（CogView 出不了透明图，白底最利于 rembg 抠图），
  并追加中文风格尾注（CogView 中文训练充分，二次元风格词用中文更稳）。
- 尺寸用 864x1152（CogView-3-Flash 支持档里最接近 2:3 的横裁源），
  入库前由 upscale 前的中心裁宽步骤统一到 2:3。
- 每角色默认 roll 2 张（--rolls 可调），后续走拼图择优。

用法:
  python generate_zhipu.py                          # P0 列表 ×2 rolls -> zp-gen/r1,r2
  python generate_zhipu.py --ids char_ur_zhulong    # 单测
  python generate_zhipu.py --rolls 3 --out zp-gen
"""
import argparse
import base64
import json
import os
import sys
import time
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from generate_free import P0_IDS, build_free_prompt, load_chars  # noqa: E402

EXPORT = os.path.join(HERE, "prompts-export.json")
KEY_FILE = os.path.join(os.path.expanduser("~"), ".zhipu_key")
ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/images/generations"
SIZE = "864x1152"
RETRIES = 3

# 中文风格尾注：CogView 中文语料充分，风格词用中文钉更死；白底利于 rembg。
# 2026-08-26 v2.3 写实化定调：尾注已由「二次元赛璐璐」改为「写实」（对齐 06 号规范 §7）。
ZH_STYLE = (
    "，精致写实风格卡牌游戏角色立绘，真实人体比例与材质质感，电影级光影，"
    "高细节完成度，纯白色纯色背景，无文字无边框"
)


def load_key(path: str) -> str:
    with open(path, encoding="utf-8") as f:
        key = f.read().strip()
    if not key:
        raise SystemExit(f"key 文件为空: {path}")
    return key


def build_prompt(char: dict) -> str:
    p = build_free_prompt(char)
    p = p.replace("transparent background, no scenery", "pure white background, no scenery")
    return p + ZH_STYLE


def generate_one(key: str, prompt: str) -> bytes:
    body = json.dumps({"model": "cogview-3-flash", "prompt": prompt, "size": SIZE}).encode()
    req = urllib.request.Request(
        ENDPOINT, data=body, method="POST",
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
    )
    last_err = ""
    for attempt in range(1, RETRIES + 1):
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                data = json.loads(resp.read().decode())
            url = data["data"][0]["url"]
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=120) as img:
                return img.read()
        except Exception as e:  # noqa: BLE001
            last_err = str(e)
            if attempt < RETRIES:
                time.sleep(10 * attempt)
    raise RuntimeError(last_err)


def erase_watermark(im):
    """擦除 CogView-3-Flash 右下角「AI生成」徽章（watermark_enabled=false 被服务端忽略）。

    v2：徽章由服务端固定盖在右下角（实测 x≈0.74-0.985 / y≈0.925-0.99，位置尺寸恒定）。
    初版按「低饱和才修」在徽章压到角色身上时漏掉混合像素留 ghost——
    改为徽章矩形全遮盖 inpaint：白底填白；压角处小面积柔化，远好于可读水印文字。"""
    import cv2
    import numpy as np
    from PIL import Image

    arr = cv2.cvtColor(np.array(im), cv2.COLOR_RGB2BGR)
    h, w = arr.shape[:2]
    x0, y0 = int(w * 0.735), int(h * 0.918)
    x1, y1 = int(w * 0.992), int(h * 0.992)
    mask = np.zeros((h, w), np.uint8)
    mask[y0:y1, x0:x1] = 255
    out = cv2.inpaint(arr, mask, 7, cv2.INPAINT_TELEA)
    return Image.fromarray(cv2.cvtColor(out, cv2.COLOR_BGR2RGB))


def to_webp(raw: bytes, path: str) -> None:
    from io import BytesIO

    from PIL import Image
    im = Image.open(BytesIO(raw)).convert("RGB")
    im = erase_watermark(im)
    im.save(path, "WEBP", quality=95)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ids", help="逗号分隔 id 白名单")
    ap.add_argument("--out", default=os.path.join(HERE, "zp-gen"))
    ap.add_argument("--rolls", type=int, default=2, help="每角色出几张（不同 roll 供择优）")
    args = ap.parse_args()

    key = load_key(KEY_FILE)
    only_ids = set(x.strip() for x in (args.ids or "").split(",") if x.strip()) or set(P0_IDS)
    chars = load_chars(EXPORT, only_ids=only_ids)
    print(f"[run] CogView-3-Flash {len(chars)} 角色 x{args.rolls} rolls -> {args.out}")

    ok, fail = [], []
    for i, char in enumerate(chars):
        cid = char["id"]
        prompt = build_prompt(char)
        for r in range(1, args.rolls + 1):
            out_dir = os.path.join(args.out, f"r{r}")
            os.makedirs(out_dir, exist_ok=True)
            out_path = os.path.join(out_dir, f"{cid}.webp")
            if os.path.exists(out_path) and os.path.getsize(out_path) > 8 * 1024:
                print(f"[{i+1}/{len(chars)}] {cid} r{r} 已存在，跳过")
                ok.append(f"{cid}:r{r}")
                continue
            t0 = time.time()
            try:
                raw = generate_one(key, prompt)
                to_webp(raw, out_path)
                print(f"  [OK] {cid} r{r} ({os.path.getsize(out_path)//1024}KB, {int(time.time()-t0)}s)")
                ok.append(f"{cid}:r{r}")
            except Exception as e:  # noqa: BLE001
                print(f"  [FAIL] {cid} r{r}: {e}")
                fail.append((cid, r, str(e)))
            time.sleep(2)
    print(f"\n[done] 成功 {len(ok)} / 失败 {len(fail)}")
    if fail:
        print("[retry-hint] 重跑本脚本续传：" + ", ".join(f"{c}:r{r}" for c, r, _ in fail))


if __name__ == "__main__":
    main()
