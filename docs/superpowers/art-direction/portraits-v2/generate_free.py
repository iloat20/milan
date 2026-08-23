#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
generate_free.py — 使用 Pollinations AI 免费生成角色立绘
"""
import json
import os
import sys
import urllib.request
import urllib.parse
import time

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

def load_chars(export_path, only_ids=None):
    data = json.load(open(export_path, encoding="utf-8"))
    chars = data["characters"]
    if only_ids:
        chars = [c for c in chars if c["id"] in only_ids]
    return chars

def generate_with_pollinations(prompt, output_path, width=1024, height=1536):
    """使用 Pollinations AI 免费生成图片"""
    encoded_prompt = urllib.parse.quote(prompt)
    url = f"https://image.pollinations.ai/prompt/{encoded_prompt}?width={width}&height={height}&nologo=true"
    
    try:
        req = urllib.request.Request(url, headers={
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        })
        with urllib.request.urlopen(req, timeout=120) as response:
            with open(output_path, 'wb') as f:
                f.write(response.read())
        return True
    except Exception as e:
        print(f"  [ERROR] {e}")
        return False

def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    
    chars = load_chars(EXPORT, only_ids=P0_IDS)
    print(f"[run] 使用 Pollinations AI 免费生成 {len(chars)} 张 P0 立绘")
    
    results = []
    for i, char in enumerate(chars):
        char_id = char["id"]
        name_cn = char["name_cn"]
        pinyin = char["pinyin"]
        
        print(f"\n[{i+1}/{len(chars)}] {name_cn} ({pinyin}) - {char_id}")
        
        # 使用简化版 prompt（Pollinations 对长 prompt 支持有限）
        prompt = char["prompt"]["subject"] + " " + char["prompt"]["style"]
        
        output_path = os.path.join(OUT_DIR, f"{char_id}.webp")
        
        if generate_with_pollinations(prompt, output_path):
            size = os.path.getsize(output_path)
            print(f"  [OK] {output_path} ({size} bytes)")
            results.append((char_id, "success"))
        else:
            results.append((char_id, "failed"))
        
        # 避免请求过快
        time.sleep(2)
    
    print(f"\n[done] 成功 {sum(1 for _, s in results if s == 'success')} / 失败 {sum(1 for _, s in results if s == 'failed')} / 共 {len(chars)}")

if __name__ == "__main__":
    main()
