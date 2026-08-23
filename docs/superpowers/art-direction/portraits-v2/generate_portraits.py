#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
generate_portraits.py — 消费 prompts-export.json，批量生成 28 张角色立绘。

生图 Provider（可插拔）：
  mock    不调用任何 API：仅把每个角色的 full_prompt 落盘为 out/<id>.prompt.txt
          并打印，用于在无网络/无 Key 的沙箱里验证导出链路。
  openai  OpenAI Images API（gpt-image-1，支持 background='transparent'）。
          需环境变量 OPENAI_API_KEY；pip install openai。
  flux    Replicate Flux.1（aspect_ratio 2:3, webp）。需环境变量
          REPLICATE_API_TOKEN；pip install replicate。

输出命名严格遵循工程约定：out/char_<rarity小写>_<拼音>.webp
（与 data.json / PortraitLoader 的 getIdentifier 命名对齐，落入
 res/drawable-nodpi/ 后无需改代码）。

用法：
  python generate_portraits.py --provider mock --out ./out
  python generate_portraits.py --provider openai --out ./out --rarity UR --limit 2
  python generate_portraits.py --provider flux   --out ./out --workers 4 --seed 42

真实环境须联网并配置对应 API Key；沙箱（无网络/无 Key）只能跑 mock。
"""
import argparse
import base64
import json
import os
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
EXPORT = os.path.join(HERE, "prompts-export.json")


def load_chars(export_path, rarity=None, limit=None, only_ids=None):
    data = json.load(open(export_path, encoding="utf-8"))
    chars = data["characters"]
    if only_ids:
        chars = [c for c in chars if c["id"] in only_ids]
    if rarity:
        chars = [c for c in chars if c["rarity"].upper() == rarity.upper()]
    if limit:
        chars = chars[: int(limit)]
    return chars


# ---------- Provider: mock ----------
def gen_mock(char, out_dir):
    pid = char["id"]
    txt_path = os.path.join(out_dir, f"{pid}.prompt.txt")
    with open(txt_path, "w", encoding="utf-8") as f:
        f.write(f"# {char['name_cn']} {char['pinyin']} · {char['title']}\n")
        f.write(f"# rarity={char['rarity']} world={char['world']} element={char['element']}\n")
        f.write(f"# canvas={char['canvas']} aspect={char['aspect_ratio']}\n")
        f.write(f"# anchors={char['anchors']}\n\n")
        f.write(char["full_prompt"])
        f.write("\n\nnegative_prompt: " + char["negative_prompt"])
    return txt_path


# ---------- Provider: openai ----------
def gen_openai(char, out_dir, size_map=None):
    from openai import OpenAI  # requires: pip install openai

    client = OpenAI()
    pid = char["id"]
    # gpt-image-1 支持竖版 1024x1536；更大画布阶梯需生图后再 upscale
    size = (size_map or {}).get(char["rarity"], "1024x1536")
    resp = client.images.generate(
        model="gpt-image-1",
        prompt=char["full_prompt"],
        size=size,
        background="transparent",           # 透明背景铁律
        quality="high",
        extra_body={"negative_prompt": char["negative_prompt"]},
    )
    item = resp.data[0]
    b64 = item.b64_json
    out_path = os.path.join(out_dir, f"{pid}.png")
    with open(out_path, "wb") as f:
        f.write(base64.b64decode(b64))
    return out_path


# ---------- Provider: flux (replicate) ----------
def gen_flux(char, out_dir, seed=None):
    import replicate  # requires: pip install replicate + REPLICATE_API_TOKEN

    pid = char["id"]
    input_dict = {
        "prompt": char["full_prompt"],
        "negative_prompt": char["negative_prompt"],
        "aspect_ratio": char["aspect_ratio"],   # "2:3"
        "output_format": "webp",
        "output_quality": 95,
        "num_outputs": 1,
        "num_inference_steps": 35,
    }
    if seed is not None:
        input_dict["seed"] = int(seed)
    out = replicate.run("black-forest-labs/flux-1.1-pro", input=input_dict)
    # out 为 FileOutput / URL 列表
    first = out[0] if isinstance(out, (list, tuple)) else out
    if hasattr(first, "read"):
        data = first.read()
        out_path = os.path.join(out_dir, f"{pid}.webp")
        with open(out_path, "wb") as f:
            f.write(data)
    else:
        out_path = os.path.join(out_dir, f"{pid}.url.txt")
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(str(first))
    return out_path


PROVIDERS = {"mock": gen_mock, "openai": gen_openai, "flux": gen_flux}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--provider", default="mock", choices=list(PROVIDERS))
    ap.add_argument("--out", default=os.path.join(HERE, "out"))
    ap.add_argument("--export", default=EXPORT)
    ap.add_argument("--rarity", help="仅生成某稀有度 UR/SSR/SR/R")
    ap.add_argument("--limit", type=int, help="最多生成 N 张（按文件顺序）")
    ap.add_argument("--ids", help="逗号分隔的 id 白名单，如 char_ur_zhulong,char_ssr_fenghuang")
    ap.add_argument("--workers", type=int, default=1, help="并发数（openai/flux 可用）")
    ap.add_argument("--seed", type=int, help="flux 固定 seed（可复现）")
    ap.add_argument("--size-map", default="", help='openai 尺寸覆盖，如 UR=1536x1024,SSR=1024x1536')
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    only_ids = set(x.strip() for x in (args.ids or "").split(",") if x.strip()) or None
    size_map = {}
    for kv in (args.size_map or "").split(","):
        if "=" in kv:
            k, v = kv.split("=", 1)
            size_map[k.strip().upper()] = v.strip()

    chars = load_chars(args.export, rarity=args.rarity, limit=args.limit, only_ids=only_ids)
    if not chars:
        print("[error] 没有匹配的角色，检查 --rarity/--ids/--limit", file=sys.stderr)
        sys.exit(1)

    print(f"[run] provider={args.provider} 目标 {len(chars)} 张 -> {args.out}")
    fn = PROVIDERS[args.provider]

    results, errors = [], []
    lock = threading.Lock()

    def work(c):
        try:
            if args.provider == "mock":
                p = fn(c, args.out)
            elif args.provider == "openai":
                p = fn(c, args.out, size_map=size_map)
            elif args.provider == "flux":
                p = fn(c, args.out, seed=args.seed)
            with lock:
                results.append((c["id"], p))
            return c["id"], p, None
        except Exception as e:  # 单张失败不阻断整体
            return c["id"], None, repr(e)

    if args.workers > 1 and args.provider != "mock":
        with ThreadPoolExecutor(max_workers=args.workers) as ex:
            futs = [ex.submit(work, c) for c in chars]
            for fut in as_completed(futs):
                cid, p, err = fut.result()
                if err:
                    errors.append((cid, err))
                    print(f"[fail] {cid}: {err}")
                else:
                    print(f"[ok]   {cid} -> {p}")
    else:
        for c in chars:
            cid, p, err = work(c)
            if err:
                errors.append((cid, err))
                print(f"[fail] {cid}: {err}")
            else:
                print(f"[ok]   {cid} -> {p}")

    print(f"\n[done] 成功 {len(results)} / 失败 {len(errors)} / 共 {len(chars)}")
    if errors:
        print("[errors] " + ", ".join(cid for cid, _ in errors))


if __name__ == "__main__":
    main()
