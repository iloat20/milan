import os, glob
from PIL import Image
from rembg import new_session, remove

BASE = r"C:\Users\Administrator\Downloads\work\milan\docs\superpowers\art-direction\weapons-v2"
GEN = os.path.join(BASE, "weapon-gen")
ASSETS = r"C:\Users\Administrator\Downloads\work\milan\MilanKotlin\app\src\main\assets\weapons"
CANVAS = (1024, 1024)

sess = new_session('u2netp')
subdirs = sorted(d for d in glob.glob(os.path.join(GEN, '*')) if os.path.isdir(d))
print('subdirs found:', len(subdirs))
ok = 0
for d in subdirs:
    vfx = os.path.basename(d)
    pngs = sorted(glob.glob(os.path.join(d, '*.png')))
    if not pngs:
        print('  SKIP', vfx, '(no png)')
        continue
    src = pngs[-1]  # 最新时间戳胜出（避免同秒碰撞残留）
    im = Image.open(src).convert('RGBA')
    out = remove(im, session=sess, alpha_matting=False)
    sw, sh = out.size
    scale = min(CANVAS[0] / sw, CANVAS[1] / sh)
    nw, nh = max(1, round(sw * scale)), max(1, round(sh * scale))
    out = out.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new('RGBA', CANVAS, (0, 0, 0, 0))
    canvas.paste(out, ((CANVAS[0] - nw) // 2, (CANVAS[1] - nh) // 2), out)
    dst = os.path.join(ASSETS, vfx + '.webp')
    canvas.save(dst, 'WEBP', lossless=True)
    has_a = canvas.mode in ('RGBA', 'LA') or ('transparency' in canvas.info)
    print(f"  {vfx:<24} src {(sw,sh)} scale {scale:.3f} -> {os.path.getsize(dst)//1024}KB alpha={has_a}")
    ok += 1
print('processed', ok, 'weapons')
