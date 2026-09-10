#!/usr/bin/env python3
"""
生成 battle SFX 占位资产（第 8 节 P0 接线用）。

用 numpy 合成短促打击/技能/结算音，写成标准 RIFF/WAVE，
文件名按 MilanAudio 约定命名为 .ogg。
Android SoundPool 经 MediaExtractor 按数据嗅探容器（非扩展名），
WAVE/PCM 可被加载播放；正式美术替换为真 Ogg Vorbis 时同名覆盖即可。

产出目录：MilanKotlin/app/src/main/assets/audio/sfx/
"""
from __future__ import annotations

import math
import struct
import wave
from pathlib import Path

import numpy as np

SR = 22050
OUT = Path("MilanKotlin/app/src/main/assets/audio/sfx")


def write_wav(path: Path, samples: np.ndarray, gain: float = 0.35) -> None:
    samples = np.asarray(samples, dtype=np.float64)
    peak = np.max(np.abs(samples)) or 1.0
    samples = samples / peak * gain
    pcm = (samples * 32767.0).astype(np.int16)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print(f"  {path.name:22s} {path.stat().st_size:6d} B  {len(pcm)/SR:.3f}s")


def t(n: float) -> np.ndarray:
    return np.arange(int(SR * n)) / SR


def env_exp(n: float, decay: float = 12.0, attack: float = 0.004) -> np.ndarray:
    x = t(n)
    a = np.clip(x / max(attack, 1e-4), 0, 1)
    d = np.exp(-decay * x)
    return a * d


def tone(freq: float, n: float, kind: str = "sine") -> np.ndarray:
    x = t(n)
    ph = 2 * math.pi * freq * x
    if kind == "sine":
        return np.sin(ph)
    if kind == "square":
        return np.sign(np.sin(ph))
    if kind == "saw":
        return 2 * ((freq * x) % 1.0) - 1
    if kind == "noise":
        rng = np.random.default_rng(int(freq * 1000 + n * 100))
        return rng.standard_normal(len(x))
    return np.sin(ph)


def sweep(f0: float, f1: float, n: float) -> np.ndarray:
    x = t(n)
    # 指数扫频
    f = f0 * (f1 / f0) ** (x / max(n, 1e-6))
    ph = 2 * math.pi * np.cumsum(f) / SR
    return np.sin(ph)


def lowpass(x: np.ndarray, alpha: float = 0.2) -> np.ndarray:
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += alpha * (v - acc)
        y[i] = acc
    return y


def highpass(x: np.ndarray, alpha: float = 0.85) -> np.ndarray:
    return x - lowpass(x, 1.0 - alpha)


def sfx_strike() -> np.ndarray:
    """普攻命中：短噪声冲击 + 低频 thump。"""
    n = 0.14
    hit = tone(900, n, "noise") * env_exp(n, 28)
    thump = tone(90, n) * env_exp(n, 18)
    click = tone(1800, 0.04) * env_exp(0.04, 60)
    body = 0.55 * hit + 0.7 * thump
    body[: len(click)] += 0.35 * click
    return body


def sfx_skill() -> np.ndarray:
    """技能：上扫 + 亮部闪烁。"""
    n = 0.28
    up = sweep(280, 1400, n) * env_exp(n, 8, 0.01)
    shimmer = tone(2200, n, "sine") * env_exp(n, 10) * 0.35
    return up + shimmer


def sfx_ultimate() -> np.ndarray:
    """大招：深沉扫频 + 金箔亮音 + 尾混。"""
    n = 0.45
    boom = sweep(120, 60, n) * env_exp(n, 6, 0.02)
    rise = sweep(200, 900, n * 0.7) * env_exp(n * 0.7, 5, 0.01)
    ring = tone(880, n) * env_exp(n, 4) * 0.25
    tail = tone(440, n) * env_exp(n, 3) * 0.15
    sig = np.zeros(int(SR * n))
    sig += 0.8 * boom
    m = len(rise)
    sig[:m] += 0.45 * rise
    sig += ring + tail
    return sig


def sfx_hurt() -> np.ndarray:
    """我方受击：短促刺痛 + 下沉。"""
    n = 0.16
    sharp = tone(520, n, "square") * env_exp(n, 30)
    drop = sweep(400, 140, n) * env_exp(n, 16)
    return 0.5 * sharp + 0.7 * drop


def sfx_death() -> np.ndarray:
    """阵亡：下坠 + 暗噪。"""
    n = 0.4
    fall = sweep(350, 70, n) * env_exp(n, 7)
    grit = tone(200, n, "noise") * env_exp(n, 9) * 0.4
    grit = lowpass(grit, 0.15)
    return fall + grit


def sfx_victory() -> np.ndarray:
    """胜利：三音上行琶音 + 明亮点。"""
    n = 0.55
    out = np.zeros(int(SR * n))
    notes = [523.25, 659.25, 783.99]  # C5 E5 G5
    for i, f in enumerate(notes):
        start = int(SR * 0.08 * i)
        seg_n = 0.22
        seg = tone(f, seg_n) * env_exp(seg_n, 9)
        end = start + len(seg)
        if end > len(out):
            seg = seg[: len(out) - start]
            end = len(out)
        out[start:end] += seg * (0.7 + 0.1 * i)
    # 尾和弦
    tail_n = 0.35
    tail = (
        tone(523.25, tail_n) + tone(659.25, tail_n) + tone(783.99, tail_n)
    ) / 3.0 * env_exp(tail_n, 5)
    out[-len(tail) :] += 0.45 * tail
    return out


def sfx_defeat() -> np.ndarray:
    """失败：下行双音 + 低鸣。"""
    n = 0.5
    d1 = sweep(330, 196, n * 0.5) * env_exp(n * 0.5, 8)
    d2 = sweep(196, 110, n * 0.5) * env_exp(n * 0.5, 7)
    out = np.zeros(int(SR * n))
    out[: len(d1)] += d1
    out[len(d1) : len(d1) + len(d2)] += 0.85 * d2
    low = tone(55, n) * env_exp(n, 4) * 0.5
    out += low
    return out


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    builders = {
        "battle_strike": sfx_strike,
        "battle_skill": sfx_skill,
        "battle_ultimate": sfx_ultimate,
        "battle_hurt": sfx_hurt,
        "battle_death": sfx_death,
        "battle_victory": sfx_victory,
        "battle_defeat": sfx_defeat,
    }
    print(f"writing {len(builders)} battle sfx → {OUT}")
    for name, fn in builders.items():
        write_wav(OUT / f"{name}.ogg", fn(), gain=0.38)
    print("done")


if __name__ == "__main__":
    main()
