#!/usr/bin/env python3
"""
Generates high-fidelity 3:2 Devpost presentation cards featuring the REAL Android screen recording frames:
1. real_showcase_01_autoscan_interception.png (1500x1000 px, 3:2)
2. real_showcase_02_circle_and_history.png (1500x1000 px, 3:2)
"""

import os
from PIL import Image, ImageDraw, ImageFont

def build_showcases():
    docs_dir = '/Users/udishasingh/Build-HybridAI/docs/gallery'
    brain_dir = '/Users/udishasingh/.gemini/antigravity/brain/6697d563-f1f1-498b-85e9-2b8ae92136ce'
    os.makedirs(docs_dir, exist_ok=True)
    os.makedirs(brain_dir, exist_ok=True)

    font_path = '/System/Library/Fonts/HelveticaNeue.ttc'

    f_tag = ImageFont.truetype(font_path, 14, index=1)
    f_title = ImageFont.truetype(font_path, 36, index=1)
    f_desc = ImageFont.truetype(font_path, 17, index=0)
    f_card_title = ImageFont.truetype(font_path, 16, index=1)
    f_card_sub = ImageFont.truetype(font_path, 13, index=0)
    f_badge = ImageFont.truetype(font_path, 13, index=1)

    # Helper: draw a phone mockup with rounded corners and subtle bezel
    def create_phone_frame(screen_im, target_h=700):
        aspect = screen_im.width / screen_im.height
        sw = int(target_h * aspect)
        sh = target_h
        resized_screen = screen_im.resize((sw, sh), Image.Resampling.LANCZOS)

        bezel = 10
        pw = sw + bezel * 2
        ph = sh + bezel * 2

        phone = Image.new('RGBA', (pw, ph), (0, 0, 0, 0))
        pdraw = ImageDraw.Draw(phone)

        # Phone body (dark matte titanium bezel)
        pdraw.rounded_rectangle([(0, 0), (pw - 1, ph - 1)], radius=28, fill=(20, 24, 28), outline=(60, 70, 78), width=2)

        # Mask for screen
        mask = Image.new('L', (sw, sh), 0)
        m舖 = ImageDraw.Draw(mask)
        m舖.rounded_rectangle([(0, 0), (sw - 1, sh - 1)], radius=18, fill=255)

        phone.paste(resized_screen, (bezel, bezel), mask)

        # Top pill speaker cutout
        pdraw.rounded_rectangle([(pw // 2 - 24, 4), (pw // 2 + 24, 7)], radius=2, fill=(60, 70, 78))

        return phone

    # Load source real images
    im_notif = Image.open(f'{docs_dir}/real_01_whatsapp_notification.png').convert('RGB')
    im_banner = Image.open(f'{docs_dir}/real_02_mark_as_scam_banner.png').convert('RGB')
    im_toast = Image.open(f'{docs_dir}/real_03_toast_marked_and_shared.png').convert('RGB')
    im_hist = Image.open(f'{docs_dir}/real_04_history_scam_logged.png').convert('RGB')
    im_circle = Image.open(f'{docs_dir}/real_05_family_circle_broadcast.png').convert('RGB')

    # =========================================================================
    # CARD 1: REAL DEVICE WHATSAPP INTERCEPTION & ACTION (1500 x 1000)
    # =========================================================================
    W, H = 1500, 1000
    card1 = Image.new('RGB', (W, H), (238, 244, 242))
    d1 = ImageDraw.Draw(card1)

    # Left Column: Story & Annotations
    d1.rounded_rectangle([(70, 60), (270, 94)], radius=17, fill=(215, 240, 235), outline=(27, 111, 104), width=1)
    d1.text((88, 70), "LIVE ANDROID CAPTURE", font=f_tag, fill=(20, 95, 85))

    d1.text((70, 115), "Real-Time WhatsApp\nScam Interception", font=f_title, fill=(18, 38, 35), spacing=8)
    
    story1 = (
        "Actual mobile screen recording from a real Android\n"
        "device. Chaukas monitors incoming WhatsApp notifications\n"
        "100% on-device, identifies scam vectors instantly, and\n"
        "presents a high-visibility interactive action overlay."
    )
    d1.text((70, 225), story1, font=f_desc, fill=(60, 80, 76), spacing=8)

    # Step Cards on the left
    # Step 1 Info Box
    d1.rounded_rectangle([(70, 350), (560, 470)], radius=14, fill=(255, 255, 255), outline=(210, 230, 225), width=1)
    d1.rounded_rectangle([(85, 365), (125, 405)], radius=8, fill=(235, 245, 242))
    d1.text((98, 375), "1", font=ImageFont.truetype(font_path, 20, index=1), fill=(27, 111, 104))
    d1.text((138, 365), "Notification Intercepted", font=f_card_title, fill=(20, 40, 36))
    d1.text((138, 392), "WhatsApp refund phishing message detected.\nChaukas Scam Shield alerts user before message tap.", font=f_card_sub, fill=(70, 90, 85), spacing=4)

    # Step 2 Info Box
    d1.rounded_rectangle([(70, 490), (560, 610)], radius=14, fill=(255, 255, 255), outline=(210, 230, 225), width=1)
    d1.rounded_rectangle([(85, 505), (125, 545)], radius=8, fill=(255, 235, 235))
    d1.text((98, 515), "2", font=ImageFont.truetype(font_path, 20, index=1), fill=(200, 40, 40))
    d1.text((138, 505), "One-Tap 'Mark as scam' Banner", font=f_card_title, fill=(20, 40, 36))
    d1.text((138, 532), "Interactive action banner floats over Android UI.\nUser confirms threat without opening suspicious links.", font=f_card_sub, fill=(70, 90, 85), spacing=4)

    # Privacy guarantee badge
    d1.rounded_rectangle([(70, 640), (560, 695)], radius=12, fill=(225, 242, 238), outline=(180, 215, 208), width=1)
    d1.text((90, 658), "[Zero-Access] Scanned locally, never uploaded to external servers", font=f_badge, fill=(20, 85, 75))

    # Right side: Two phones side by side with proper margins
    target_h1 = 700
    p1 = create_phone_frame(im_notif, target_h=target_h1)
    p2 = create_phone_frame(im_banner, target_h=target_h1)

    x1, y1 = 590, 180
    x2, y2 = 1030, 180

    # Draw label pills above phones
    d1.rounded_rectangle([(x1 + 30, 130), (x1 + 380, 164)], radius=16, fill=(24, 30, 34))
    d1.text((x1 + 55, 139), "STEP 1: INCOMING SCAM ALERT", font=f_badge, fill=(230, 245, 240))

    d1.rounded_rectangle([(x2 + 25, 130), (x2 + 385, 164)], radius=16, fill=(190, 35, 35))
    d1.text((x2 + 45, 139), "STEP 2: FLOATING ACTION BANNER", font=f_badge, fill=(255, 255, 255))

    card1.paste(p1, (x1, y1), p1)
    card1.paste(p2, (x2, y2), p2)

    card1_path_docs = f'{docs_dir}/real_showcase_01_autoscan_interception.png'
    card1_path_brain = f'{brain_dir}/real_showcase_01_autoscan_interception.png'
    card1.save(card1_path_docs)
    card1.save(card1_path_brain)
    print(f'Saved Card 1: {card1_path_docs}')

    # =========================================================================
    # CARD 2: REAL DEVICE PROTECTION CONFIRMATION, HISTORY & CIRCLE (1500 x 1000)
    # =========================================================================
    card2 = Image.new('RGB', (W, H), (244, 245, 248))
    d2 = ImageDraw.Draw(card2)

    # Header
    d2.rounded_rectangle([(70, 35), (280, 70)], radius=17, fill=(230, 235, 248), outline=(70, 90, 180), width=1)
    d2.text((90, 44), "FAMILY DEFENSE & AUDIT", font=f_tag, fill=(50, 70, 160))

    d2.text((70, 80), "Auto-Sync to Family Circle & Local History", font=f_title, fill=(25, 30, 45))
    
    desc2 = (
        "When marked as scam, Chaukas instantly displays a confirmation toast, logs the incident\n"
        "to on-device private history, and broadcasts a protective warning card to Family Circle."
    )
    d2.text((70, 135), desc2, font=f_desc, fill=(75, 80, 95), spacing=6)

    # Three phones spread across canvas
    target_h2 = 630
    p3 = create_phone_frame(im_toast, target_h=target_h2)
    p4 = create_phone_frame(im_hist, target_h=target_h2)
    p5 = create_phone_frame(im_circle, target_h=target_h2)

    # Calculate horizontal layout
    # phone width = int(630 * 392 / 698) + 20 = 354 + 20 = 374 px
    pw2 = p3.width
    total_w = pw2 * 3
    spacing = (W - total_w) // 4  # (1500 - 1122) // 4 = 378 // 4 = 94 px
    px3 = spacing
    px4 = spacing * 2 + pw2
    px5 = spacing * 3 + pw2 * 2
    py2 = 235

    # Badge labels above each phone
    d2.rounded_rectangle([(px3 + 20, 192), (px3 + pw2 - 20, 224)], radius=15, fill=(35, 40, 50))
    d2.text((px3 + 35, 201), "1. INSTANT TOAST ALERT", font=f_badge, fill=(240, 245, 250))

    d2.rounded_rectangle([(px4 + 20, 192), (px4 + pw2 - 20, 224)], radius=15, fill=(27, 111, 104))
    d2.text((px4 + 35, 201), "2. ON-DEVICE HISTORY AUDIT", font=f_badge, fill=(240, 255, 250))

    d2.rounded_rectangle([(px5 + 20, 192), (px5 + pw2 - 20, 224)], radius=15, fill=(180, 45, 45))
    d2.text((px5 + 30, 201), "3. BROADCAST TO CIRCLE", font=f_badge, fill=(255, 245, 245))

    card2.paste(p3, (px3, py2), p3)
    card2.paste(p4, (px4, py2), p4)
    card2.paste(p5, (px5, py2), p5)

    # Sub-captions below phones
    d2.text((px3 + 15, py2 + p3.height + 16), "Toast confirms circle alert & save", font=f_card_sub, fill=(90, 95, 110))
    d2.text((px4 + 25, py2 + p4.height + 16), "Scam stored locally with risk 75", font=f_card_sub, fill=(90, 95, 110))
    d2.text((px5 + 10, py2 + p5.height + 16), "Real-time warning + peer reactions", font=f_card_sub, fill=(90, 95, 110))

    card2_path_docs = f'{docs_dir}/real_showcase_02_circle_and_history.png'
    card2_path_brain = f'{brain_dir}/real_showcase_02_circle_and_history.png'
    card2.save(card2_path_docs)
    card2.save(card2_path_brain)
    print(f'Saved Card 2: {card2_path_docs}')

if __name__ == '__main__':
    build_showcases()
