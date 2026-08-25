#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
generate_free.py — 使用 Pollinations AI 免费生成角色立绘（零 Key 路线）

v2.2（2026-08-23）适配：
- 使用「浓缩 prompt 构造器」build_free_prompt()：LORE 原文 + 识别锚点 + 卡牌化指令精简版，
  控制在免费通道长度限额内（实测 >2KB 编码 URL 会被 404 拒绝；≤1.7KB 稳定通过）。
  完整版指令层请走 generate_portraits.py --provider openai/flux。
- 404/超时自动重试（指数退避）；已产出且有效的文件自动跳过 → 支持中断续跑。
- P0 角色列表可被 --ids / --all 覆盖。

已知通道天花板（2026-08-24 实测，多轮验证）：
- 中文锚点段与英文主体段组合会触发确定性 404 → 锚点一律用英文 key_elements（见 build_free_prompt）。
- 同 prompt 重跑命中服务端缓存拿回同一张图（缓存键=prompt URL）→ 请求必须带随机 seed。
- 涌现式内容过滤：分块单发均过、特定「主体×姿态」组合即拒（keqing/kikyo），逐词软化无效
  → generate 阶梯降级重试（全量 → 去标签 → 去姿态）保证出图。
- 单独成行的锚点/标签段被模型无视；只有主体/姿态段散文被渲染 → pose 必须并入（v2.2.3）。
- 手持道具（弓/双剑/锤/五色石）与异形下半身（蛇尾）在主体散文未提及时基本不渲染；
  zhulong 成功恰因 subject 散文以蛇尾为主要描述——锚点要稳定呈现需改设计稿主体段（内容工作）。
- 无法渲染无头人形（刑天锚点 A）：「NO HEAD / headless / 平焊颈盖」均被自动纠正为带头盔。
- IP 联动角色有先验泄漏（桔梗三次出图两次长出猫/狐耳）。
- 结论：锚点齐全的交付走付费通道（generate_portraits.py --provider openai/flux）；
  免费通道产物定位为占位/氛围参考。

用法:
  python generate_free.py                # P0 列表，续跑
  python generate_free.py --ids char_ur_xingtian,char_ur_zhulong
  python generate_free.py --all          # 全部 28 张
"""
import argparse
import json
import os
import random
import sys
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
EXPORT = os.path.join(HERE, "prompts-export.json")
OUT_DIR = os.path.join(HERE, "out")

# P0 角色列表
P0_IDS = [
    "char_sr_suanni",
    "char_ur_zhulong",
    "char_ur_xingtian",
    "char_ur_kikyo",
    "char_ur_keqing",
    "char_ur_wuxu",
    "char_ur_jinwu",
    "char_ur_nuwa",
    "char_ssr_fenghuang",
    "char_ssr_leishen",
    "char_ssr_xiangliu"
]

WIDTH, HEIGHT = 1024, 1536
RETRIES = 4
RETRY_BACKOFF_SEC = 25


def load_chars(export_path, only_ids=None):
    data = json.load(open(export_path, encoding="utf-8"))
    chars = data["characters"]
    if only_ids:
        chars = [c for c in chars if c["id"] in only_ids]
    return chars


def build_free_prompt(char: dict, include_tags: bool = True, include_pose: bool = True) -> str:
    """浓缩 prompt（v2.2.3）：优先级 = 主体段 > 姿态段(道具/动势载荷) > 卡牌指令 > 中文 LORE。

    教训链（2026-08-24 实测）：
    - 404 是内容过滤且「分块单发均过、特定组合即拒」（涌现式，无法逐词定位）——
      对策是 generate_with_pollinations 的降级重试阶梯，而非猜触发词。
    - 只有主体/姿态段的英文散文被渲染；单独成行的锚点标签被无视。
    - pose 字段含道具与异形解剖的关键描述（拉弓/双剑/蛇尾/五色石），
      丢弃 pose = 道具锚点全灭——必须以精简散文并入。"""
    parts: list[str] = []

    # 1) 英文主体段（解剖签名/配色）+ 并入去蛇形 key_elements
    subject = (char.get("prompt", {}).get("subject") or "").strip()
    key_elements = (char.get("prompt", {}) or {}).get("key_elements") or []
    if key_elements and include_tags:
        tags = ", ".join(k.replace("_", " ") for k in key_elements)
        subject = f"{subject} Identity anchors, all clearly visible: {tags}."
    if subject:
        parts.append(subject)

    # 2) 姿态段精简（道具/动势载荷）：去掉「Dynamic pose PX (xxx) +」样板前缀，保留实质分句
    pose = (char.get("prompt", {}).get("pose") or "").strip()
    if pose and include_pose:
        clauses = [c.strip() for c in pose.replace("Dynamic pose", "|").split("+") if c.strip()]
        # 首个分句形如「| P1 (deity hovering)」——保留其中的动作名，去掉管道符
        pose_txt = "; ".join(c.lstrip("| ").strip() for c in clauses)
        parts.append(pose_txt)

    # 3) 卡牌构图+光影精简指令
    r = char["rarity"]
    tier = {
        "UR": "molten-gold god-ray accents",
        "SSR": "violet twilight glow",
        "SR": "clean heroic lighting",
        "R": "soft bright lighting",
    }.get(r, "")
    parts.append(
        f"Dynamic card-game illustration ({r}), three-quarter view, low-angle hero shot, "
        f"full-body or waist-up as fits the design, subject fills 75-85% of frame, "
        f"face never cropped; strong key light upper-left, rim light in element color "
        f"from back-left, {tier}; transparent background, no scenery."
    )

    # 4) 中文 LORE 尾注（氛围参考；超限最先牺牲）
    if char.get("lore"):
        parts.append("Backstory ref (zh): " + " ".join(char["lore"].split()))

    out = "\n".join(p for p in parts if p)
    # 长度红线（2026-08-24 复测）：1471 字过 / 1507 字拒 → 阈值 ≈1.5KB，取 1400 留安全边际
    while len(out) > 1400 and len(parts) > 2:
        parts.pop()          # 从尾部丢：先牺牲 LORE 尾注（主体+姿态是身份载荷，绝不丢）
        out = "\n".join(parts)
    return out


def generate_with_pollinations(prompt, output_path):
    encoded = urllib.parse.quote(prompt)
    # seed 每次尝试随机：① 同 prompt 重跑不会命中服务端缓存拿回旧图（实测缓存键=prompt URL，
    # 不带 seed 的重 roll 是空转）；② 重试时自动换新 roll。
    seed = random.randint(1, 10**9)
    url = (f"https://image.pollinations.ai/prompt/{encoded}"
           f"?width={WIDTH}&height={HEIGHT}&nologo=true&seed={seed}")
    last_err = ""
    for attempt in range(1, RETRIES + 1):
        try:
            req = urllib.request.Request(url, headers={
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            })
            with urllib.request.urlopen(req, timeout=180) as response:
                data = response.read()
            if len(data) < 8 * 1024:
                raise RuntimeError(f"响应过小({len(data)}B)，疑似错误页")
            with open(output_path, 'wb') as f:
                f.write(data)
            return True, ""
        except Exception as e:
            last_err = str(e)
            if attempt < RETRIES:
                time.sleep(RETRY_BACKOFF_SEC * attempt)  # 指数退避：25/50/75s
    return False, last_err


def valid_existing(path: str) -> bool:
    return os.path.exists(path) and os.path.getsize(path) >= 8 * 1024


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ids", help="逗号分隔 id 白名单")
    ap.add_argument("--all", action="store_true", help="全部 28 张")
    ap.add_argument("--out", default=OUT_DIR, help="输出目录（默认 out/；v2.2 重跑建议新目录避免混入旧产物）")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    only_ids = set(x.strip() for x in (args.ids or "").split(",") if x.strip()) or (
        None if args.all else set(P0_IDS))
    chars = load_chars(EXPORT, only_ids=only_ids)
    print(f"[run] Pollinations 免费 {len(chars)} 张 -> {args.out}（单图最长重试 {RETRIES} 次，已存在自动跳过）")

    # 降级阶梯：全量(标签+姿态) → 去标签 → 去姿态（涌现式 404 的工程化对策）
    LADDER = [
        ("全量", True, True),
        ("去标签", False, True),
        ("去姿态", False, False),
    ]

    ok, fail = [], []
    for i, char in enumerate(chars):
        cid = char["id"]
        out_path = os.path.join(args.out, f"{cid}.webp")
        if valid_existing(out_path):
            print(f"[{i+1}/{len(chars)}] {cid} 已存在，跳过")
            ok.append(cid)
            continue

        print(f"\n[{i+1}/{len(chars)}] {char['name_cn']} ({cid})")
        t0 = time.time()
        success, err, used = False, "", ""
        for label, inc_tags, inc_pose in LADDER:
            prompt = build_free_prompt(char, include_tags=inc_tags, include_pose=inc_pose)
            print(f"  [{label}] prompt={len(prompt)}字")
            success, err = generate_with_pollinations(prompt, out_path)
            if success:
                used = label
                break
            time.sleep(2)
        cost = int(time.time() - t0)
        if success:
            print(f"  [OK] {out_path} ({os.path.getsize(out_path)//1024}KB, {cost}s, {used})")
            ok.append(cid)
        else:
            print(f"  [FAIL] {err} ({cost}s)")
            fail.append((cid, err))
        time.sleep(2)

    print(f"\n[done] 成功 {len(ok)} / 失败 {len(fail)} / 共 {len(chars)}")
    if fail:
        print("[retry-hint] 直接重跑本脚本即可续传未完成项：" +
              ", ".join(c for c, _ in fail))


if __name__ == "__main__":
    main()
