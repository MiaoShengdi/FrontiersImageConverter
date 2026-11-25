# -*- coding: utf-8 -*-
import os, sys
sys.path.insert(0, r'E:\FrontiersImageTool\desktop')
import converter_core as core
from PIL import Image

tests = [
    r'E:\宫颈鳞癌Biomolecules投稿\论文图表\fig1.tif',
    r'E:\宫颈鳞癌Biomolecules投稿\论文图表\Supplementary\Supplementary_Figure_S3.tif',
]
outdir = r'E:\FrontiersImageTool\desktop\test_out'
os.makedirs(outdir, exist_ok=True)
for src in tests:
    dst = core.output_path_for(src, outdir)
    w, h, size, scale = core.convert(src, dst)
    im = Image.open(dst)
    print('RESULT:', os.path.basename(src), '->', os.path.basename(dst))
    print('  mode:', im.mode, '| dpi:', im.info.get('dpi'), '| compression:', im.info.get('compression'))
    print('  size bytes:', size, '| under 10MB:', size <= 10 * 1024 * 1024,
          '| pixels:', im.size, '| scale:', scale)
