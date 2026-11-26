#!/usr/bin/env python3
"""
Generates high-resolution transparent video watermark / corner overlay logos for Chaukas.
Produces multiple professional styles:
1. Glass Dark Pill (Industry standard for videos, readable over any background)
2. White Transparent (Clean white text with drop shadow for dark videos)
3. Brand Teal Transparent (Official brand colors for light videos/slides)
4. Glass Light Pill (Frosted white pill for colorful/busy videos)
Both 1x (1080p optimal) and 2x (4K Ultra-HD optimal), plus vector SVG.
"""

import os
import pymupdf
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def generate(scale=1.0, suffix=""):
    out_dir = os.path.abspath('docs/watermark')
    web_dir = os.path.abspath('web/public/branding')
    artifact_dir = '/Users/udishasingh/.gemini/antigravity/brain/6697d563-f1f1-498b-85e9-2b8ae92136ce'

    os.makedirs(out_dir, exist_ok=True)
    os.makedirs(web_dir, exist_ok=True)

    # Load high-res icon
    doc = pymupdf.open('web/public/icon.svg')
    icon_pix = doc[0].get_pixmap(dpi=int(600 * scale), alpha=True)
    base_icon = Image.frombytes('RGBA', [icon_pix.width, icon_pix.height], icon_pix.samples)

    font_path = '/System/Library/Fonts/HelveticaNeue.ttc'
    font_title = ImageFont.truetype(font_path, int(68 * scale), index=1)
    font_sub = ImageFont.truetype(font_path, int(26 * scale), index=0)

    icon_dim = int(116 * scale)
    icon_resized = base_icon.resize((icon_dim, icon_dim), Image.Resampling.LANCZOS)

    dummy_img = Image.new('RGBA', (1, 1), (0, 0, 0, 0))
    dummy_draw = ImageDraw.Draw(dummy_img)
    title_text = "Chaukas"
    sub_text = "Scam shield"

    title_bbox = dummy_draw.textbbox((0, 0), title_text, font=font_title)
    sub_bbox = dummy_draw.textbbox((0, 0), sub_text, font=font_sub)

    title_w = title_bbox[2] - title_bbox[0]
    title_h = title_bbox[3] - title_bbox[1]
    sub_w = sub_bbox[2] - sub_bbox[0]
    sub_h = sub_bbox[3] - sub_bbox[1]
    text_w = max(title_w, sub_w)

    # 1. Glass Dark Pill
    pad_x = int(28 * scale)
    pad_y = int(20 * scale)
    spacing = int(22 * scale)
    pill_w = pad_x + icon_dim + spacing + text_w + pad_x + int(12 * scale)
    pill_h = max(icon_dim, title_h + int(8 * scale) + sub_h) + (pad_y * 2)

    canvas_w = pill_w + int(32 * scale)
    canvas_h = pill_h + int(32 * scale)
    offset = int(16 * scale)

    img_glass_dark = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    shadow = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(shadow)
    s_draw.rounded_rectangle(
        [(offset, offset + int(2 * scale)), (offset + pill_w, offset + int(2 * scale) + pill_h)],
        radius=pill_h // 2,
        fill=(0, 0, 0, 115)
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(int(10 * scale)))
    img_glass_dark.alpha_composite(shadow)

    pill = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    p_draw = ImageDraw.Draw(pill)
    p_draw.rounded_rectangle(
        [(offset, offset), (offset + pill_w, offset + pill_h)],
        radius=pill_h // 2,
        fill=(15, 23, 28, 198),
        outline=(255, 255, 255, 48),
        width=max(1, int(2 * scale))
    )
    img_glass_dark.alpha_composite(pill)

    icon_x = offset + pad_x
    icon_y = offset + (pill_h - icon_dim) // 2
    img_glass_dark.alpha_composite(icon_resized, (icon_x, icon_y))

    draw = ImageDraw.Draw(img_glass_dark)
    tx = icon_x + icon_dim + spacing
    ty_title = offset + pad_y + int(4 * scale)
    ty_sub = ty_title + title_h + int(12 * scale)

    draw.text((tx, ty_title), title_text, font=font_title, fill=(255, 255, 255, 255))
    draw.text((tx + int(2 * scale), ty_sub), sub_text, font=font_sub, fill=(124, 219, 200, 240))

    bbox = img_glass_dark.getbbox()
    cropped_glass_dark = img_glass_dark.crop((bbox[0] - int(4*scale), bbox[1] - int(4*scale), bbox[2] + int(4*scale), bbox[3] + int(4*scale)))

    # 2. White Transparent
    raw_w = icon_dim + spacing + text_w + int(30 * scale)
    raw_h = max(icon_dim, title_h + int(10 * scale) + sub_h) + int(24 * scale)
    off_r = int(10 * scale)
    
    img_white = Image.new('RGBA', (raw_w + int(20 * scale), raw_h + int(20 * scale)), (0, 0, 0, 0))
    tx_raw = off_r + icon_dim + spacing
    ty_raw_title = off_r + (raw_h - (title_h + int(12 * scale) + sub_h)) // 2
    ty_raw_sub = ty_raw_title + title_h + int(12 * scale)

    shadow_txt = Image.new('RGBA', img_white.size, (0, 0, 0, 0))
    st_draw = ImageDraw.Draw(shadow_txt)
    st_draw.text((tx_raw + int(2 * scale), ty_raw_title + int(3 * scale)), title_text, font=font_title, fill=(0, 0, 0, 160))
    st_draw.text((tx_raw + int(3 * scale), ty_raw_sub + int(2 * scale)), sub_text, font=font_sub, fill=(0, 0, 0, 150))
    shadow_txt = shadow_txt.filter(ImageFilter.GaussianBlur(int(4 * scale)))
    img_white.alpha_composite(shadow_txt)

    icon_shadow = Image.new('RGBA', img_white.size, (0, 0, 0, 0))
    is_draw = ImageDraw.Draw(icon_shadow)
    is_draw.rounded_rectangle(
        [(off_r + int(2*scale), off_r + int(2*scale)), (off_r + icon_dim + int(2*scale), off_r + icon_dim + int(2*scale))],
        radius=int(36 * scale), fill=(0, 0, 0, 90)
    )
    icon_shadow = icon_shadow.filter(ImageFilter.GaussianBlur(int(6 * scale)))
    img_white.alpha_composite(icon_shadow)

    icon_y_raw = off_r + (raw_h - icon_dim) // 2
    img_white.alpha_composite(icon_resized, (off_r, icon_y_raw))

    w_draw = ImageDraw.Draw(img_white)
    w_draw.text((tx_raw, ty_raw_title), title_text, font=font_title, fill=(255, 255, 255, 255))
    w_draw.text((tx_raw + int(2 * scale), ty_raw_sub), sub_text, font=font_sub, fill=(210, 245, 240, 235))

    bbox_w = img_white.getbbox()
    cropped_white = img_white.crop((bbox_w[0] - int(4*scale), bbox_w[1] - int(4*scale), bbox_w[2] + int(4*scale), bbox_w[3] + int(4*scale)))

    # 3. Brand Teal Transparent
    img_brand = Image.new('RGBA', img_white.size, (0, 0, 0, 0))
    img_brand.alpha_composite(icon_resized, (off_r, icon_y_raw))
    b_draw = ImageDraw.Draw(img_brand)
    b_draw.text((tx_raw, ty_raw_title), title_text, font=font_title, fill=(20, 86, 81, 255))
    b_draw.text((tx_raw + int(2 * scale), ty_raw_sub), sub_text, font=font_sub, fill=(44, 125, 84, 235))

    bbox_b = img_brand.getbbox()
    cropped_brand = img_brand.crop((bbox_b[0] - int(4*scale), bbox_b[1] - int(4*scale), bbox_b[2] + int(4*scale), bbox_b[3] + int(4*scale)))

    # 4. Glass Light Pill
    img_glass_light = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    shadow_l = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    sl_draw = ImageDraw.Draw(shadow_l)
    sl_draw.rounded_rectangle(
        [(offset, offset + int(2 * scale)), (offset + pill_w, offset + int(2 * scale) + pill_h)],
        radius=pill_h // 2,
        fill=(0, 0, 0, 50)
    )
    shadow_l = shadow_l.filter(ImageFilter.GaussianBlur(int(8 * scale)))
    img_glass_light.alpha_composite(shadow_l)

    pill_l = Image.new('RGBA', (canvas_w, canvas_h), (0, 0, 0, 0))
    pl_draw = ImageDraw.Draw(pill_l)
    pl_draw.rounded_rectangle(
        [(offset, offset), (offset + pill_w, offset + pill_h)],
        radius=pill_h // 2,
        fill=(255, 255, 255, 235),
        outline=(27, 111, 104, 45),
        width=max(1, int(1.5 * scale))
    )
    img_glass_light.alpha_composite(pill_l)

    img_glass_light.alpha_composite(icon_resized, (icon_x, icon_y))
    gl_draw = ImageDraw.Draw(img_glass_light)
    gl_draw.text((tx, ty_title), title_text, font=font_title, fill=(20, 86, 81, 255))
    gl_draw.text((tx + int(2 * scale), ty_sub), sub_text, font=font_sub, fill=(44, 125, 84, 235))

    bbox_gl = img_glass_light.getbbox()
    cropped_glass_light = img_glass_light.crop((bbox_gl[0] - int(4*scale), bbox_gl[1] - int(4*scale), bbox_gl[2] + int(4*scale), bbox_gl[3] + int(4*scale)))

    # Save PNG files
    files = {
        f'chaukas-watermark-glass-dark{suffix}.png': cropped_glass_dark,
        f'chaukas-watermark-white{suffix}.png': cropped_white,
        f'chaukas-watermark-brand{suffix}.png': cropped_brand,
        f'chaukas-watermark-glass-light{suffix}.png': cropped_glass_light,
    }

    for name, img in files.items():
        p1 = os.path.join(out_dir, name)
        p2 = os.path.join(web_dir, name)
        p3 = os.path.join(artifact_dir, name)
        img.save(p1, 'PNG')
        img.save(p2, 'PNG')
        img.save(p3, 'PNG')
        print(f"Saved: {name} ({img.width}x{img.height})")

def main():
    print("Generating 1x (1080p optimal) watermarks...")
    generate(scale=1.0, suffix="")
    print("Generating 2x (4K Ultra-HD optimal) watermarks...")
    generate(scale=2.0, suffix="@2x")

if __name__ == '__main__':
    main()
