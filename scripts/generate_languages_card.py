#!/usr/bin/env python3
"""
Generates the updated 3:2 image for 09_multilingual_indian_languages.png
showing ONLY the 6 supported languages:
English, Hindi, Bengali, Tamil, Telugu, Marathi.
"""

import os
from PIL import Image, ImageDraw, ImageFont

def generate_language_image():
    W, H = 1500, 1000
    im = Image.new('RGB', (W, H), (240, 245, 245))
    draw = ImageDraw.Draw(im)

    # Main Card
    card_x0, card_y0 = 60, 120
    card_x1, card_y1 = 1440, 880
    
    # Card shadow
    draw.rounded_rectangle([(card_x0 + 4, card_y0 + 6), (card_x1 + 4, card_y1 + 6)], radius=24, fill=(225, 232, 232))
    # Card surface
    draw.rounded_rectangle([(card_x0, card_y0), (card_x1, card_y1)], radius=24, fill=(255, 255, 255), outline=(230, 236, 236), width=1)

    f_helv = '/System/Library/Fonts/HelveticaNeue.ttc'
    f_dev = '/System/Library/Fonts/Supplemental/Devanagari Sangam MN.ttc'
    f_bng = '/System/Library/Fonts/Supplemental/Bangla Sangam MN.ttc'
    f_tam = '/System/Library/Fonts/Supplemental/Tamil Sangam MN.ttc'
    f_tel = '/System/Library/Fonts/Supplemental/Telugu Sangam MN.ttc'

    font_title = ImageFont.truetype(f_helv, 36, index=1)
    font_desc = ImageFont.truetype(f_helv, 20, index=0)
    font_subtext = ImageFont.truetype(f_helv, 18, index=0)
    font_toggle_title = ImageFont.truetype(f_helv, 22, index=1)
    font_toggle_desc = ImageFont.truetype(f_helv, 18, index=0)

    # Header icon (lilac badge with message icon)
    draw.rounded_rectangle([(card_x0 + 50, card_y0 + 50), (card_x0 + 94, card_y0 + 94)], radius=12, fill=(240, 235, 250))
    # Draw simple chat bubble vector
    draw.rounded_rectangle([(card_x0 + 62, card_y0 + 62), (card_x0 + 82, card_y0 + 78)], radius=4, outline=(130, 100, 210), width=2)
    draw.polygon([(card_x0 + 66, card_y0 + 78), (card_x0 + 66, card_y0 + 84), (card_x0 + 72, card_y0 + 78)], fill=(130, 100, 210))

    # Header title
    draw.text((card_x0 + 110, card_y0 + 54), "Explain in my language", font=font_title, fill=(25, 30, 35))
    
    # Description
    desc_txt = "Chaukas reads messages in any language. Pick the language you want the verdict, the reasons and the next steps written in."
    draw.text((card_x0 + 50, card_y0 + 115), desc_txt, font=font_desc, fill=(90, 100, 110))

    # 6 Languages Grid: 2 rows of 3 columns
    langs = [
        {"name": "English", "native": "English", "font": ImageFont.truetype(f_helv, 28, index=1), "active": False},
        {"name": "Hindi", "native": "हिन्दी", "font": ImageFont.truetype(f_dev, 28, index=1), "active": True},
        {"name": "Bengali", "native": "বাংলা", "font": ImageFont.truetype(f_bng, 28, index=1), "active": False},
        {"name": "Tamil", "native": "தமிழ்", "font": ImageFont.truetype(f_tam, 28, index=1), "active": False},
        {"name": "Telugu", "native": "తెలుగు", "font": ImageFont.truetype(f_tel, 28, index=1), "active": False},
        {"name": "Marathi", "native": "मराठी", "font": ImageFont.truetype(f_dev, 28, index=1), "active": False},
    ]

    grid_x = card_x0 + 50
    grid_y = card_y0 + 175
    pill_w = 410
    pill_h = 110
    gap_x = 25
    gap_y = 20

    for idx, l in enumerate(langs):
        col = idx % 3
        row = idx // 3
        px0 = grid_x + col * (pill_w + gap_x)
        py0 = grid_y + row * (pill_h + gap_y)
        px1 = px0 + pill_w
        py1 = py0 + pill_h

        if l["active"]:
            # Active pill styling (Teal mint background + teal border)
            draw.rounded_rectangle([(px0, py0), (px1, py1)], radius=18, fill=(240, 248, 246), outline=(27, 111, 104), width=2)
            draw.text((px0 + 30, py0 + 24), l["native"], font=l["font"], fill=(20, 85, 75))
            draw.text((px0 + 30, py0 + 66), l["name"], font=ImageFont.truetype(f_helv, 17, index=0), fill=(27, 111, 104))
            # Green checkmark badge
            draw.ellipse([(px1 - 45, py0 + 38), (px1 - 20, py0 + 63)], fill=(27, 111, 104))
            draw.line([(px1 - 37, py0 + 50), (px1 - 34, py0 + 55)], fill=(255, 255, 255), width=2)
            draw.line([(px1 - 34, py0 + 55), (px1 - 27, py0 + 47)], fill=(255, 255, 255), width=2)
        else:
            # Inactive pill
            draw.rounded_rectangle([(px0, py0), (px1, py1)], radius=18, fill=(255, 255, 255), outline=(225, 230, 232), width=2)
            draw.text((px0 + 30, py0 + 24), l["native"], font=l["font"], fill=(30, 35, 40))
            draw.text((px0 + 30, py0 + 66), l["name"], font=ImageFont.truetype(f_helv, 17, index=0), fill=(120, 130, 140))

    # Gemini Nano tuning hint
    hint_y = grid_y + 2 * (pill_h + gap_y) + 35
    hint_p1 = "Gemini Nano on your device is tuned for English. For Hindi, Bengali, Tamil, Telugu and Marathi, Smart and"
    hint_p2 = "Most accurate modes give the best wording; Private mode will try its best on-device."
    draw.text((card_x0 + 50, hint_y), hint_p1, font=font_subtext, fill=(80, 90, 100))
    draw.text((card_x0 + 50, hint_y + 26), hint_p2, font=font_subtext, fill=(80, 90, 100))

    # Divider line
    div_y = hint_y + 80
    draw.line([(card_x0 + 50, div_y), (card_x1 - 50, div_y)], fill=(235, 240, 240), width=1)

    # Casual Tone Row
    row_y = div_y + 30
    draw.text((card_x0 + 50, row_y), "Casual tone", font=font_toggle_title, fill=(25, 30, 35))
    draw.text((card_x0 + 50, row_y + 32), 'Answers in friendly Hinglish, like "Bhai, yeh pakka fraud hai. Link mat kholna."', font=font_toggle_desc, fill=(100, 110, 120))

    # Toggle switch
    sw_x1 = card_x1 - 50
    sw_x0 = sw_x1 - 65
    sw_y0 = row_y + 12
    sw_y1 = sw_y0 + 36
    draw.rounded_rectangle([(sw_x0, sw_y0), (sw_x1, sw_y1)], radius=18, fill=(230, 235, 235), outline=(205, 215, 215), width=1)
    draw.ellipse([(sw_x0 + 4, sw_y0 + 4), (sw_x0 + 32, sw_y0 + 32)], fill=(255, 255, 255))

    # Save to all target paths
    docs_gallery = '/Users/udishasingh/Build-HybridAI/docs/gallery/09_multilingual_indian_languages.png'
    docs_screen = '/Users/udishasingh/Build-HybridAI/docs/screenshots/language.png'
    brain_artifact = '/Users/udishasingh/.gemini/antigravity/brain/6697d563-f1f1-498b-85e9-2b8ae92136ce/09_multilingual_indian_languages.png'

    im.save(docs_gallery)
    im.save(docs_screen)
    im.save(brain_artifact)
    print(f"Saved updated 6-language gallery image: {docs_gallery}")

if __name__ == '__main__':
    generate_language_image()
