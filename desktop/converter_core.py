# -*- coding: utf-8 -*-
"""
Frontiers 图片转换核心
输入：PNG / JPG / JPEG / BMP / WEBP / GIF / TIF 等常见位图格式
输出：RGB、300 dpi、LZW 压缩的 TIFF；文件超过 10 MB 时等比缩小直至达标。
Frontiers 要求：TIF/TIFF、RGB、300 dpi（final size）。
"""
import os
from PIL import Image, ImageOps

MAX_BYTES = 10 * 1024 * 1024
TARGET_DPI = 300
INPUT_EXTS = ('.png', '.jpg', '.jpeg', '.bmp', '.webp', '.gif', '.tif', '.tiff', '.jfif')


def load_as_rgb(src):
    img = Image.open(src)
    img = ImageOps.exif_transpose(img)
    has_alpha = (
        img.mode in ('RGBA', 'LA', 'PA')
        or (img.mode == 'P' and 'transparency' in img.info)
    )
    if has_alpha:
        rgba = img.convert('RGBA')
        bg = Image.new('RGB', rgba.size, (255, 255, 255))
        bg.paste(rgba, mask=rgba.split()[-1])
        return bg
    return img.convert('RGB')


def save_tiff(img, dst):
    img.save(dst, format='TIFF', compression='tiff_lzw',
             dpi=(TARGET_DPI, TARGET_DPI))


def convert(src, dst, max_bytes=MAX_BYTES, log=print):
    """返回 (最终像素宽, 最终像素高, 最终字节数, 缩放比例)。"""
    img = load_as_rgb(src)
    orig_w, orig_h = img.size
    work = img
    save_tiff(work, dst)
    scale = 1.0
    # 超 10MB：每次缩小 10%，直到达标；保留至少 600 px 短边
    while os.path.getsize(dst) > max_bytes and min(work.size) > 600:
        scale *= 0.9
        new_size = (max(1, round(orig_w * scale)), max(1, round(orig_h * scale)))
        work = img.resize(new_size, Image.LANCZOS)
        save_tiff(work, dst)
    size = os.path.getsize(dst)
    if size > max_bytes:
        raise RuntimeError('无法在 600 px 短边限制内压缩到 10 MB 以内')
    log('  输出: %s | %dx%d | 缩放 %.0f%% | %.2f MB'
        % (os.path.basename(dst), work.size[0], work.size[1], scale * 100, size / 1048576))
    return work.size[0], work.size[1], size, scale


def output_path_for(src, out_dir):
    base = os.path.splitext(os.path.basename(src))[0]
    return os.path.join(out_dir, base + '.tif')
