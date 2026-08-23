import os, glob
from PIL import Image
from rembg import new_session, remove

BASE = r"C:\Users\Administrator\Downloads\work\milan\docs\superpowers\art-direction\portraits-v2"
GEN = os.path.join(BASE, "p1-gen")
FINAL = os.path.join(BASE, "final")
os.makedirs(FINAL, exist_ok=True)

canvas_map = {'ur': (1536, 2304), 'ssr': (1280, 1920), 'sr': (1024, 1536), 'r': (832, 1248)}

sess = new_session('u2netp')
subdirs = sorted(d for d in glob.glob(os.path.join(GEN, 'char_*')) if os.path.isdir(d))
print('subdirs found:', len(subdirs))
ok = 0
for d in subdirs:
    cid = os.path.basename(d)
    rarity = cid.split('_')[1]
    W, H = canvas_map[rarity]
    pngs = sorted(glob.glob(os.path.join(d, '*.png')))
    src = pngs[-1]  # latest timestamp wins (avoids collision leftovers)
    im = Image.open(src).convert('RGBA')
    out = remove(im, session=sess, alpha_matting=False)
    sw, sh = out.size
    scale = min(W / sw, H / sh)
    nw, nh = max(1, round(sw * scale)), max(1, round(sh * scale))
    out = out.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    canvas.paste(out, ((W - nw) // 2, (H - nh) // 2), out)
    dst = os.path.join(FINAL, cid + '.webp')
    canvas.save(dst, 'WEBP', lossless=True)
    has_a = canvas.mode in ('RGBA', 'LA') or ('transparency' in canvas.info)
    print(f"  {cid:<22} canvas {(W,H)} src {(sw,sh)} scale {scale:.3f} -> {os.path.getsize(dst)//1024}KB alpha={has_a}")
    ok += 1
print('processed', ok, 'images')
