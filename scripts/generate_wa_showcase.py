#!/usr/bin/env python3
"""
Generates high-fidelity 3:2 mobile showcase graphics for Devpost Gallery:
1. 12_whatsapp_autoscan_interception.png: Shows WhatsApp receiving a scam with Chaukas Auto-Scan banner floating on top.
2. 13_mark_as_scam_compromised_contact.png: Shows the Chaukas mobile review screen with the "Mark as scam" action and Family Circle sync.
"""

import os
import pymupdf
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_wa_showcase():
    out_dir = os.path.abspath('docs/gallery')
    artifact_dir = '/Users/udishasingh/.gemini/antigravity/brain/6697d563-f1f1-498b-85e9-2b8ae92136ce'
    os.makedirs(out_dir, exist_ok=True)

    W = 1500
    H = 1000

    font_path = '/System/Library/Fonts/HelveticaNeue.ttc'
    font_arial = '/System/Library/Fonts/Supplemental/Arial Bold.ttf'
    font_arial_reg = '/System/Library/Fonts/Supplemental/Arial.ttf'

    f_title = ImageFont.truetype(font_path, 38, index=1)
    f_sub = ImageFont.truetype(font_path, 20, index=0)
    f_badge = ImageFont.truetype(font_path, 15, index=1)
    f_mob_head = ImageFont.truetype(font_path, 22, index=1)
    f_mob_sub = ImageFont.truetype(font_path, 14, index=0)
    f_msg = ImageFont.truetype(font_path, 18, index=0)
    f_msg_time = ImageFont.truetype(font_path, 13, index=0)
    f_btn = ImageFont.truetype(font_path, 16, index=1)
    f_card_title = ImageFont.truetype(font_path, 19, index=1)
    f_card_body = ImageFont.truetype(font_path, 16, index=0)

    # -------------------------------------------------------------
    # IMAGE 1: WHATSAPP AUTOSCAN INTERCEPTION (12_whatsapp_autoscan_interception.png)
    # -------------------------------------------------------------
    im1 = Image.new('RGB', (W, H), (236, 243, 241))
    draw1 = ImageDraw.Draw(im1)

    # Left side: Typography / Feature Explanation
    draw1.text((90, 140), "AUTO-SCAN ON ANDROID", font=ImageFont.truetype(font_path, 16, index=1), fill=(27, 111, 104))
    draw1.text((90, 175), "Real-Time WhatsApp\nScam Interception", font=ImageFont.truetype(font_path, 52, index=1), fill=(18, 38, 35), spacing=10)
    
    desc_p1 = (
        "Zero copy-paste required. As soon as a deceptive\n"
        "message arrives on WhatsApp or SMS, Chaukas's\n"
        "on-device background guard inspects notification\n"
        "heuristics and alerts you before you tap any link."
    )
    draw1.text((90, 320), desc_p1, font=ImageFont.truetype(font_path, 22, index=0), fill=(55, 75, 72), spacing=10)

    # Feature points with green checkmarks
    feats = [
        "100% on-device heuristic analysis",
        "Catches hijacked accounts of saved contacts",
        "One-tap 'Mark as scam' or 'It's okay'",
        "Zero private data leaves the hardware"
    ]
    fy = 460
    for feat in feats:
        draw1.ellipse([(90, fy + 4), (108, fy + 22)], fill=(220, 245, 235), outline=(27, 111, 104), width=2)
        draw1.line([(95, fy + 13), (98, fy + 17)], fill=(27, 111, 104), width=2)
        draw1.line([(98, fy + 17), (104, fy + 10)], fill=(27, 111, 104), width=2)
        draw1.text((122, fy), feat, font=ImageFont.truetype(font_path, 21, index=1), fill=(24, 48, 44))
        fy += 46

    # Bottom badge
    draw1.rounded_rectangle([(90, 680), (480, 740)], radius=16, fill=(255, 255, 255), outline=(190, 220, 215), width=1)
    draw1.text((115, 698), "⚡ Powered by On-Device Gemini Nano", font=ImageFont.truetype(font_path, 18, index=1), fill=(20, 95, 85))

    # Right side: Realistic Mobile Phone Mockup
    phone_w = 480
    phone_h = 860
    px = 920
    py = 70

    # Phone shadow
    sh = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    sh_draw = ImageDraw.Draw(sh)
    sh_draw.rounded_rectangle([(px + 10, py + 20), (px + phone_w + 10, py + phone_h + 20)], radius=54, fill=(0, 0, 0, 60))
    sh = sh.filter(ImageFilter.GaussianBlur(25))
    im1.paste(sh, (0, 0), sh)

    # Phone Outer Bezel
    draw1.rounded_rectangle([(px, py), (px + phone_w, py + phone_h)], radius=50, fill=(24, 28, 30), outline=(60, 66, 70), width=4)
    # Phone Screen area
    sx = px + 14
    sy = py + 14
    sw = phone_w - 28
    sh_h = phone_h - 28
    draw1.rounded_rectangle([(sx, sy), (sx + sw, sy + sh_h)], radius=42, fill=(238, 234, 226))

    # WhatsApp Top Bar (Teal #075E54)
    draw1.rectangle([(sx, sy), (sx + sw, sy + 110)], fill=(7, 94, 84))
    # WhatsApp Camera / Avatar
    draw1.ellipse([(sx + 24, sy + 44), (sx + 72, sy + 92)], fill=(210, 230, 225))
    draw1.text((sx + 38, sy + 56), "P", font=ImageFont.truetype(font_path, 24, index=1), fill=(7, 94, 84))
    draw1.text((sx + 84, sy + 48), "Priya Sharma (Saved Contact)", font=f_mob_head, fill=(255, 255, 255))
    draw1.text((sx + 84, sy + 76), "online", font=f_mob_sub, fill=(200, 235, 225))

    # Chat Background pattern & WhatsApp Date Pill
    draw1.rounded_rectangle([(sx + (sw - 120)//2, sy + 130), (sx + (sw + 120)//2, sy + 160)], radius=8, fill=(255, 255, 255, 210))
    draw1.text((sx + (sw - 80)//2, sy + 138), "TODAY", font=f_msg_time, fill=(90, 95, 100))

    # Incoming WhatsApp Scam Message Bubble (White with green tail)
    msg_y = sy + 180
    draw1.rounded_rectangle([(sx + 24, msg_y), (sx + sw - 40, msg_y + 175)], radius=14, fill=(255, 255, 255))
    msg_text = (
        "Dear consumer, your electricity power will be\n"
        "disconnected tonight at 9.30 PM from the\n"
        "office due to unpaid bill. Please immediately\n"
        "contact our officer at 9876543210 or update:\n"
        "http://bit.ly/power-bill-dhbvn"
    )
    draw1.text((sx + 40, msg_y + 16), msg_text, font=f_msg, fill=(20, 20, 20), spacing=5)
    draw1.text((sx + sw - 110, msg_y + 148), "8:42 PM ✓✓", font=f_msg_time, fill=(120, 130, 135))

    # -------------------------------------------------------------
    # FLOATING CHAUKAS TOP ALERT BANNER (The Hero)
    # -------------------------------------------------------------
    banner_y = sy + 380
    banner_h = 230
    bx = sx + 16
    bw = sw - 32

    # Banner Shadow
    b_sh = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    b_sh_draw = ImageDraw.Draw(b_sh)
    b_sh_draw.rounded_rectangle([(bx + 4, banner_y + 8), (bx + bw + 4, banner_y + banner_h + 8)], radius=24, fill=(0, 0, 0, 120))
    b_sh = b_sh.filter(ImageFilter.GaussianBlur(14))
    im1.paste(b_sh, (0, 0), b_sh)

    # Banner Background (Dark Slate #1E293B with Red border)
    draw1.rounded_rectangle([(bx, banner_y), (bx + bw, banner_y + banner_h)], radius=20, fill=(26, 32, 44), outline=(220, 50, 50), width=2)

    # Top Row: Red Alert Badge + Risk Score + Source
    draw1.rounded_rectangle([(bx + 20, banner_y + 20), (bx + 155, banner_y + 48)], radius=6, fill=(198, 40, 40))
    draw1.text((bx + 28, banner_y + 26), "SCAM ALERT 86%", font=f_badge, fill=(255, 255, 255))
    draw1.text((bx + 168, banner_y + 26), "WhatsApp · Priya", font=f_mob_sub, fill=(200, 210, 220))

    # Title & Reasons
    draw1.text((bx + 20, banner_y + 60), "⚠️ Threat of power cut · Suspicious link", font=f_card_title, fill=(255, 255, 255))
    draw1.text((bx + 20, banner_y + 92), "Sender in contacts, but account may be compromised.", font=f_mob_sub, fill=(200, 205, 215))

    # Action Buttons Row
    btn_y = banner_y + 140
    btn_w = (bw - 50) // 2

    # Button 1: MARK AS SCAM (Red)
    draw1.rounded_rectangle([(bx + 20, btn_y), (bx + 20 + btn_w + 20, btn_y + 54)], radius=12, fill=(198, 40, 40))
    draw1.text((bx + 35, btn_y + 17), "⚠️  Mark as scam", font=f_btn, fill=(255, 255, 255))

    # Button 2: IT'S OKAY (Outlined)
    draw1.rounded_rectangle([(bx + 40 + btn_w + 10, btn_y), (bx + bw - 20, btn_y + 54)], radius=12, outline=(180, 190, 200), width=2)
    draw1.text((bx + 60 + btn_w + 10, btn_y + 17), "✓  It's okay", font=f_btn, fill=(240, 245, 250))

    # Toast Notification at Bottom of phone
    toast_y = sy + sh_h - 90
    draw1.rounded_rectangle([(sx + 30, toast_y), (sx + sw - 30, toast_y + 50)], radius=25, fill=(30, 40, 45, 240))
    draw1.text((sx + 50, toast_y + 15), "🛡️ Auto-Scan active · Scanning SMS & WhatsApp", font=f_mob_sub, fill=(255, 255, 255))

    # Dynamic Island / Camera hole at top
    draw1.rounded_rectangle([(sx + (sw - 110)//2, sy + 12), (sx + (sw + 110)//2, sy + 38)], radius=13, fill=(15, 18, 20))

    im1.save(os.path.join(out_dir, '12_whatsapp_autoscan_interception.png'))
    im1.save(os.path.join(artifact_dir, '12_whatsapp_autoscan_interception.png'))
    print("Generated: 12_whatsapp_autoscan_interception.png")

    # -------------------------------------------------------------
    # IMAGE 2: MARK AS SCAM & FAMILY SYNC (13_mark_as_scam_compromised_contact.png)
    # -------------------------------------------------------------
    im2 = Image.new('RGB', (W, H), (242, 246, 248))
    draw2 = ImageDraw.Draw(im2)

    # Header section
    draw2.text((90, 70), "COMPROMISED CONTACT PROTECTION", font=ImageFont.truetype(font_path, 16, index=1), fill=(198, 40, 40))
    draw2.text((90, 100), "Mark As Scam & Instant Family Shield", font=ImageFont.truetype(font_path, 44, index=1), fill=(20, 32, 38))
    draw2.text((90, 155), "When a friend's account is hijacked, 1 tap protects your entire family circle.", font=ImageFont.truetype(font_path, 20, index=0), fill=(80, 95, 105))

    # Left Card: The Mobile Decision Screen (Phone frame)
    c1_x = 90
    c1_y = 220
    c1_w = 620
    c1_h = 710

    # Card 1 background
    draw2.rounded_rectangle([(c1_x, c1_y), (c1_x + c1_w, c1_y + c1_h)], radius=24, fill=(255, 255, 255), outline=(210, 220, 225), width=2)
    # Card Header
    draw2.rectangle([(c1_x, c1_y), (c1_x + c1_w, c1_y + 70)], fill=(245, 248, 250))
    draw2.text((c1_x + 25, c1_y + 24), "1. On Mobile: Saved Contact Flagged", font=ImageFont.truetype(font_path, 20, index=1), fill=(30, 45, 55))

    # Compromised alert card inside
    ac_y = c1_y + 100
    draw2.rounded_rectangle([(c1_x + 25, ac_y), (c1_x + c1_w - 25, ac_y + 260)], radius=16, fill=(255, 248, 240), outline=(255, 183, 77), width=2)
    draw2.text((c1_x + 45, ac_y + 22), "⚠️ Message from saved contact", font=ImageFont.truetype(font_path, 19, index=1), fill=(230, 81, 0))
    warn_p = (
        "This sender is in your contacts (Priya),\n"
        "but this message has red flags. Their account\n"
        "may be compromised. Choose what to do:"
    )
    draw2.text((c1_x + 45, ac_y + 60), warn_p, font=ImageFont.truetype(font_path, 17, index=0), fill=(80, 90, 100), spacing=6)

    # The 2 buttons
    b_y2 = ac_y + 175
    draw2.rounded_rectangle([(c1_x + 45, b_y2), (c1_x + 330, b_y2 + 56)], radius=12, fill=(198, 40, 40))
    draw2.text((c1_x + 75, b_y2 + 18), "⚠️  Mark as scam", font=ImageFont.truetype(font_path, 18, index=1), fill=(255, 255, 255))

    draw2.rounded_rectangle([(c1_x + 350, b_y2), (c1_x + c1_w - 45, b_y2 + 56)], radius=12, fill=(255, 255, 255), outline=(200, 210, 215), width=2)
    draw2.text((c1_x + 400, b_y2 + 18), "✓  It's okay", font=ImageFont.truetype(font_path, 18, index=1), fill=(60, 70, 80))

    # Result toast
    t_y = ac_y + 290
    draw2.rounded_rectangle([(c1_x + 25, t_y), (c1_x + c1_w - 25, t_y + 60)], radius=12, fill=(236, 253, 245), outline=(167, 243, 208), width=2)
    draw2.text((c1_x + 55, t_y + 19), "✓ Marked as scam and shared to circle & history", font=ImageFont.truetype(font_path, 17, index=1), fill=(6, 95, 70))

    # Key takeaway pill
    draw2.rounded_rectangle([(c1_x + 25, c1_y + 560), (c1_x + c1_w - 25, c1_y + 670)], radius=14, fill=(240, 247, 246))
    takeaway1 = (
        "• Automatically logs to local History\n"
        "• Automatically shares to all circles you are in\n"
        "• Never shares private conversation text"
    )
    draw2.text((c1_x + 45, c1_y + 578), takeaway1, font=ImageFont.truetype(font_path, 16, index=0), fill=(24, 75, 70), spacing=8)

    # Right Card: Real-Time Family Circle Feed
    c2_x = 790
    c2_y = 220
    c2_w = 620
    c2_h = 710

    draw2.rounded_rectangle([(c2_x, c2_y), (c2_x + c2_w, c2_y + c2_h)], radius=24, fill=(255, 255, 255), outline=(210, 220, 225), width=2)
    # Card Header
    draw2.rectangle([(c2_x, c2_y), (c2_x + c2_w, c2_y + 70)], fill=(245, 248, 250))
    draw2.text((c2_x + 25, c2_y + 24), "2. In Family Circle: Instant Warning Broadcast", font=ImageFont.truetype(font_path, 20, index=1), fill=(30, 45, 55))

    # Group Card Header
    g_info_y = c2_y + 95
    draw2.text((c2_x + 25, g_info_y), "Sharma Family Circle (Code: FAM789)", font=ImageFont.truetype(font_path, 19, index=1), fill=(20, 35, 45))
    draw2.text((c2_x + 25, g_info_y + 28), "4 Members · Automated 3-day protection active", font=ImageFont.truetype(font_path, 14, index=0), fill=(100, 115, 125))

    # Live Warning Card in Family Feed
    fc_y = c2_y + 170
    draw2.rounded_rectangle([(c2_x + 25, fc_y), (c2_x + c2_w - 25, fc_y + 240)], radius=16, fill=(254, 242, 242), outline=(254, 202, 202), width=2)
    
    # Alert tag
    draw2.rounded_rectangle([(c2_x + 45, fc_y + 20), (c2_x + 160, fc_y + 48)], radius=6, fill=(220, 38, 38))
    draw2.text((c2_x + 55, fc_y + 26), "SCAM ALERT", font=ImageFont.truetype(font_path, 14, index=1), fill=(255, 255, 255))
    draw2.text((c2_x + 175, fc_y + 26), "Shared by Anushka · Just now", font=ImageFont.truetype(font_path, 14, index=0), fill=(120, 50, 50))

    draw2.text((c2_x + 45, fc_y + 65), "Electricity Disconnection Phishing Wave", font=ImageFont.truetype(font_path, 18, index=1), fill=(153, 27, 27))
    fc_msg = (
        "“...electricity power will be disconnected tonight\n"
        "at 9.30 PM... immediately call officer...”\n"
        "🔗 Defanged link: http://bit[.]ly/power-bill-dhbvn"
    )
    draw2.text((c2_x + 45, fc_y + 98), fc_msg, font=ImageFont.truetype(font_path, 16, index=0), fill=(110, 40, 40), spacing=5)

    # Reaction Buttons
    draw2.rounded_rectangle([(c2_x + 45, fc_y + 185), (c2_x + 190, fc_y + 220)], radius=8, fill=(255, 255, 255), outline=(254, 202, 202), width=1)
    draw2.text((c2_x + 60, fc_y + 194), "👍 I got this too (2)", font=ImageFont.truetype(font_path, 13, index=1), fill=(153, 27, 27))

    draw2.rounded_rectangle([(c2_x + 205, fc_y + 185), (c2_x + 330, fc_y + 220)], radius=8, fill=(255, 255, 255), outline=(254, 202, 202), width=1)
    draw2.text((c2_x + 220, fc_y + 194), "📞 I'll call you", font=ImageFont.truetype(font_path, 13, index=1), fill=(153, 27, 27))

    # Second alert indicator
    draw2.rounded_rectangle([(c2_x + 25, fc_y + 260), (c2_x + c2_w - 25, fc_y + 360)], radius=16, fill=(248, 250, 252), outline=(226, 232, 240), width=1)
    draw2.text((c2_x + 45, fc_y + 280), "Fake Part-Time Job Scam (₹5000/day)", font=ImageFont.truetype(font_path, 16, index=1), fill=(71, 85, 105))
    draw2.text((c2_x + 45, fc_y + 310), "Shared by Papa · Expires in 2 days", font=ImageFont.truetype(font_path, 14, index=0), fill=(148, 163, 184))

    # Key takeaway pill
    draw2.rounded_rectangle([(c2_x + 25, c2_y + 560), (c2_x + c2_w - 25, c2_y + 670)], radius=14, fill=(240, 247, 246))
    takeaway2 = (
        "• Shared across all circles the user is in\n"
        "• Real-time cross-device sync via Cloud Firestore\n"
        "• Disappears automatically after 3 days"
    )
    draw2.text((c2_x + 45, c2_y + 578), takeaway2, font=ImageFont.truetype(font_path, 16, index=0), fill=(24, 75, 70), spacing=8)

    im2.save(os.path.join(out_dir, '13_mark_as_scam_compromised_contact.png'))
    im2.save(os.path.join(artifact_dir, '13_mark_as_scam_compromised_contact.png'))
    print("Generated: 13_mark_as_scam_compromised_contact.png")

if __name__ == '__main__':
    create_wa_showcase()
