#!/usr/bin/env python3
"""
Creates a curated set of 3:2 ratio images (1500 x 1000 px) for Devpost's Project Media Gallery.
Optimized for Devpost guidelines: PNG, 3:2 ratio, under 5 MB (each ~200-400 KB).
"""

import os
from PIL import Image, ImageOps, ImageDraw, ImageFont

def create_gallery():
    src_dir = os.path.abspath('docs/screenshots')
    out_dir = os.path.abspath('docs/gallery')
    artifact_dir = '/Users/udishasingh/.gemini/antigravity/brain/6697d563-f1f1-498b-85e9-2b8ae92136ce'
    os.makedirs(out_dir, exist_ok=True)

    TARGET_W = 1500
    TARGET_H = 1000  # Exact 3:2 ratio

    def fit_into_canvas(im, bg_color=(245, 247, 248), pad=40):
        canvas = Image.new('RGB', (TARGET_W, TARGET_H), bg_color)
        max_w = TARGET_W - (pad * 2)
        max_h = TARGET_H - (pad * 2)

        ratio = min(max_w / im.width, max_h / im.height)
        new_w = int(im.width * ratio)
        new_h = int(im.height * ratio)

        resized = im.resize((new_w, new_h), Image.Resampling.LANCZOS)
        x = (TARGET_W - new_w) // 2
        y = (TARGET_H - new_h) // 2
        
        # Paste with border/subtle shadow
        canvas.paste(resized, (x, y))
        return canvas

    gallery_items = []

    # 1. Home / Check Screen
    im_check = Image.open(os.path.join(src_dir, 'check.png')).convert('RGB')
    g1 = fit_into_canvas(im_check, bg_color=(240, 244, 245), pad=30)
    g1.save(os.path.join(out_dir, '01_chaukas_home_screen.png'))
    gallery_items.append('01_chaukas_home_screen.png')

    # 2. Result Screen
    im_res = Image.open(os.path.join(src_dir, 'result.png')).convert('RGB')
    g2 = fit_into_canvas(im_res, bg_color=(244, 240, 245), pad=20)
    g2.save(os.path.join(out_dir, '02_deep_threat_analysis_verdict.png'))
    gallery_items.append('02_deep_threat_analysis_verdict.png')

    # 3. Hybrid AI Engine
    im_eng = Image.open(os.path.join(src_dir, 'engine.png')).convert('RGB')
    g3 = fit_into_canvas(im_eng, bg_color=(240, 248, 244), pad=30)
    g3.save(os.path.join(out_dir, '03_firebase_hybrid_ai_engine.png'))
    gallery_items.append('03_firebase_hybrid_ai_engine.png')

    # 4. Mobile Showcase Side-by-Side (Hindi Home + Hindi Result)
    h_home = Image.open(os.path.join(src_dir, 'hindi-home.png')).convert('RGBA')
    h_res = Image.open(os.path.join(src_dir, 'hindi-result.png')).convert('RGBA')
    g4 = Image.new('RGB', (TARGET_W, TARGET_H), (238, 244, 242))

    target_mob_h = 880
    r_h = target_mob_h / h_home.height
    mob1 = h_home.resize((int(h_home.width * r_h), target_mob_h), Image.Resampling.LANCZOS)
    mob2 = h_res.resize((int(h_res.width * r_h), target_mob_h), Image.Resampling.LANCZOS)

    total_mob_w = mob1.width + 60 + mob2.width
    start_x = (TARGET_W - total_mob_w) // 2
    mob_y = (TARGET_H - target_mob_h) // 2

    g4.paste(mob1.convert('RGB'), (start_x, mob_y))
    g4.paste(mob2.convert('RGB'), (start_x + mob1.width + 60, mob_y))
    g4.save(os.path.join(out_dir, '04_mobile_autoscan_multilingual.png'))
    gallery_items.append('04_mobile_autoscan_multilingual.png')

    # 5. Official Indian Helplines & First-Hour Recovery
    im_rep = Image.open(os.path.join(src_dir, 'report.png')).convert('RGB')
    g5 = fit_into_canvas(im_rep, bg_color=(245, 242, 238), pad=25)
    g5.save(os.path.join(out_dir, '05_india_1930_helpline_recovery.png'))
    gallery_items.append('05_india_1930_helpline_recovery.png')

    # 6. QR & UPI Inspector
    im_qr = Image.open(os.path.join(src_dir, 'qr.png')).convert('RGB')
    g6 = fit_into_canvas(im_qr, bg_color=(242, 246, 248), pad=25)
    g6.save(os.path.join(out_dir, '06_qr_and_upi_fraud_inspector.png'))
    gallery_items.append('06_qr_and_upi_fraud_inspector.png')

    # 7. Scam Dojo Gamified Training
    im_dojo = Image.open(os.path.join(src_dir, 'dojo.png')).convert('RGB')
    g7 = fit_into_canvas(im_dojo, bg_color=(244, 245, 248), pad=30)
    g7.save(os.path.join(out_dir, '07_scam_dojo_interactive_training.png'))
    gallery_items.append('07_scam_dojo_interactive_training.png')

    # 8. Threat Insights & Trends
    im_ins = Image.open(os.path.join(src_dir, 'insights.png')).convert('RGB')
    g8 = fit_into_canvas(im_ins, bg_color=(245, 243, 247), pad=30)
    g8.save(os.path.join(out_dir, '08_threat_insights_and_trends.png'))
    gallery_items.append('08_threat_insights_and_trends.png')

    # 9. Multilingual Localization (14 Indian Languages)
    im_lang = Image.open(os.path.join(src_dir, 'language.png')).convert('RGB')
    g9 = fit_into_canvas(im_lang, bg_color=(240, 245, 245), pad=40)
    g9.save(os.path.join(out_dir, '09_multilingual_indian_languages.png'))
    gallery_items.append('09_multilingual_indian_languages.png')

    # 10. WhatsApp-Ready Shareable Alert Card
    im_card = Image.open(os.path.join(src_dir, 'alert-card.png')).convert('RGB')
    g10 = fit_into_canvas(im_card, bg_color=(238, 244, 242), pad=30)
    g10.save(os.path.join(out_dir, '10_whatsapp_scam_alert_card.png'))
    gallery_items.append('10_whatsapp_scam_alert_card.png')

    # 11. Privacy Modes & Model Settings
    im_set = Image.open(os.path.join(src_dir, 'settings.png')).convert('RGB')
    g11 = fit_into_canvas(im_set, bg_color=(242, 244, 246), pad=25)
    g11.save(os.path.join(out_dir, '11_privacy_modes_and_settings.png'))
    gallery_items.append('11_privacy_modes_and_settings.png')

    # Copy to artifact dir for instant previews
    for name in gallery_items:
        src = os.path.join(out_dir, name)
        dst = os.path.join(artifact_dir, name)
        Image.open(src).save(dst)
        print(f"Generated 3:2 Gallery Image: {name} (1500x1000 px, {os.path.getsize(src)/1024:.0f} KB)")

if __name__ == '__main__':
    create_gallery()
