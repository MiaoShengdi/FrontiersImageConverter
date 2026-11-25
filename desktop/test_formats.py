# -*- coding: utf-8 -*-
import os, sys, random
sys.path.insert(0, r'E:\FrontiersImageTool\desktop')
from PIL import Image
import converter_core as core

td = r'E:\FrontiersImageTool\desktop\test_out'
os.makedirs(td, exist_ok=True)

# large noisy PNG (noise resists LZW -> forces the shrink loop)
random.seed(1)
w, h = 3200, 4000
im = Image.new('RGB', (w, h))
px = im.load()
for y in range(h):
    for x in range(w):
        px[x, y] = (random.randint(0, 255), random.randint(0, 255), random.randint(0, 255))
png = os.path.join(td, 'noisy_big.png')
im.save(png)
jpg = os.path.join(td, 'noisy_big.jpg')
im.save(jpg, quality=92)

# RGBA transparency test
rgba = Image.new('RGBA', (800, 600), (255, 0, 0, 128))
ap = os.path.join(td, 'alpha.png')
rgba.save(ap)

for src in (png, jpg, ap):
    dst = core.output_path_for(src, td)
    r = core.convert(src, dst)
    out = Image.open(dst)
    print(os.path.basename(src), '->', os.path.basename(dst),
          '| mode', out.mode, '| dpi', out.info.get('dpi'),
          '| MB', round(r[2] / 1048576, 2), '| scale', round(r[3], 2))
