import requests
import time
import random
import io
from PIL import Image, ImageDraw, ImageFont

BASE_URL = "http://localhost:8080"
PASSWORD = "password123"

users = [f"testUser{i}" for i in range(1, 11)]
user_tokens = {}

text_styles = [
    { "name": "经典白", "color": "#FFFFFF", "font": "sans-serif", "weight": "bold", "shadow": True },
    { "name": "极简黑", "color": "#000000", "font": "sans-serif", "weight": "bold", "shadow": False },
    { "name": "衬线雅", "color": "#FFFFFF", "font": "serif", "weight": "bold", "shadow": True },
    { "name": "活力黄", "color": "#FFD700", "font": "sans-serif", "weight": "900", "shadow": True, "shadowColor": "rgba(0,0,0,0.8)" },
    { "name": "清新绿", "color": "#E0FFEB", "font": "monospace", "weight": "bold", "shadow": True, "shadowColor": "#004d00" },
    { "name": "霓虹粉", "color": "#FF00FF", "font": "sans-serif", "weight": "bold", "shadow": True, "shadowColor": "#00FFFF", "glow": True },
    { "name": "描边黑", "color": "#FFFFFF", "font": "Impact, sans-serif", "weight": "bold", "stroke": "#000000", "strokeWidth": 3 }
]

cover_styles = [
    { "name": "粉嫩", "type": "gradient", "colors": ["#FF9A9E", "#FECFEF"], "decoration": "circles" },
    { "name": "紫罗兰", "type": "gradient", "colors": ["#a18cd1", "#fbc2eb"], "decoration": "circles" },
    { "name": "清新", "type": "gradient", "colors": ["#84fab0", "#8fd3f4"], "decoration": "circles" },
    { "name": "暗黑", "type": "gradient", "colors": ["#434343", "#000000"], "decoration": "grid" },
    { "name": "日落", "type": "gradient", "colors": ["#fa709a", "#fee140"], "decoration": "lines" },
    { "name": "幽蓝", "type": "gradient", "colors": ["#30cfd0", "#330867"], "decoration": "bubbles" },
    { "name": "纯净白", "type": "solid", "colors": ["#ffffff"], "decoration": "border", "defaultTextIndex": 1 },
    { "name": "复古纸张", "type": "solid", "colors": ["#f4e4bc"], "decoration": "noise", "defaultTextIndex": 1 },
    { "name": "科技蓝", "type": "gradient", "colors": ["#000428", "#004e92"], "decoration": "grid" },
    { "name": "派对", "type": "solid", "colors": ["#FFF5E6"], "decoration": "confetti", "defaultTextIndex": 1 },
    { "name": "几何", "type": "gradient", "colors": ["#2E3192", "#1BFFFF"], "decoration": "geometric" },
    { "name": "赛博", "type": "solid", "colors": ["#000000"], "decoration": "neon", "defaultTextIndex": 5 }
]

tags = ["书籍", "数码", "家居", "玩具", "服装", "文具", "美食", "旅行", "运动", "影音"]
title_prefix = ["精选", "测评", "分享", "入门", "清单", "指南", "推荐", "体验", "总结", "上手"]
title_subject = ["心得", "技巧", "搭配", "故事", "避坑", "玩法", "合集", "记录", "对比", "灵感"]
content_openers = [
    "这次想聊聊我的真实体验，整体感受",
    "最近研究了不少资料，发现关键点是",
    "使用一段时间后，我觉得最重要的是",
    "整理了几个实用的小技巧，首先是",
    "从入门到进阶，我的总结是",
    "这里是我对这次体验的简单回顾",
    "如果你正准备尝试，建议先关注",
    "这篇记录了我的实际过程，重点在于"
]
content_endings = [
    "欢迎交流你的看法。",
    "希望能对你有所帮助。",
    "有问题可以留言一起讨论。",
    "如果有更多想法我会继续更新。",
    "以上是我的个人体验，仅供参考。",
    "后续有新发现会再补充。"
]

def register_user(username):
    url = f"{BASE_URL}/api/auth/register"
    payload = {
        "username": username,
        "password": PASSWORD,
        "email": f"{username}@example.com",
        "nickname": f"Nick_{username}"
    }
    try:
        requests.post(url, json=payload, timeout=10)
    except Exception:
        return

def login_user(username):
    url = f"{BASE_URL}/api/auth/login"
    payload = {
        "username": username,
        "password": PASSWORD
    }
    try:
        res = requests.post(url, json=payload, timeout=10)
        if res.status_code == 200:
            return res.json().get("accessToken")
    except Exception:
        return None
    return None

def parse_color(value):
    if isinstance(value, tuple):
        return value
    if value.startswith("#"):
        hex_color = value.lstrip("#")
        r = int(hex_color[0:2], 16)
        g = int(hex_color[2:4], 16)
        b = int(hex_color[4:6], 16)
        return (r, g, b, 255)
    if value.startswith("rgba"):
        parts = value[value.find("(") + 1:value.find(")")].split(",")
        r = int(parts[0].strip())
        g = int(parts[1].strip())
        b = int(parts[2].strip())
        a = float(parts[3].strip())
        return (r, g, b, int(a * 255))
    if value.startswith("rgb"):
        parts = value[value.find("(") + 1:value.find(")")].split(",")
        r = int(parts[0].strip())
        g = int(parts[1].strip())
        b = int(parts[2].strip())
        return (r, g, b, 255)
    named = {
        "white": (255, 255, 255, 255),
        "black": (0, 0, 0, 255)
    }
    return named.get(value.lower(), (255, 255, 255, 255))

def create_gradient(width, height, color1, color2):
    base = Image.new("RGBA", (width, height), color1)
    top = Image.new("RGBA", (width, height), color2)
    mask = Image.new("L", (width, height))
    mask_data = []
    for y in range(height):
        for x in range(width):
            mask_data.append(int(255 * ((x + y) / (width + height))))
    mask.putdata(mask_data)
    base.paste(top, (0, 0), mask)
    return base

def get_font(size):
    font_names = ["msyh.ttc", "simhei.ttf", "simsun.ttc", "arial.ttf"]
    for font_name in font_names:
        try:
            return ImageFont.truetype(font_name, size)
        except Exception:
            continue
    return ImageFont.load_default()

def measure_text(draw, text, font):
    try:
        return draw.textlength(text, font=font)
    except Exception:
        bbox = draw.textbbox((0, 0), text, font=font)
        return bbox[2] - bbox[0]

def draw_canvas_content(text):
    width, height = 600, 800
    bg_style = random.choice(cover_styles)
    text_index = bg_style.get("defaultTextIndex", random.randrange(len(text_styles)))
    txt_style = text_styles[text_index]

    if bg_style["type"] == "solid":
        img = Image.new("RGBA", (width, height), parse_color(bg_style["colors"][0]))
    else:
        img = create_gradient(width, height, parse_color(bg_style["colors"][0]), parse_color(bg_style["colors"][1]))

    draw = ImageDraw.Draw(img, "RGBA")

    if bg_style["decoration"] in ["circles", "bubbles"]:
        count = 30 if bg_style["decoration"] == "bubbles" else 50
        for _ in range(count):
            r = random.random() * (80 if bg_style["decoration"] == "bubbles" else 50)
            x = random.random() * width
            y = random.random() * height
            draw.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, 25))
    elif bg_style["decoration"] == "grid":
        step = 50
        for x in range(0, width + 1, step):
            draw.line((x, 0, x, height), fill=(255, 255, 255, 25), width=2)
        for y in range(0, height + 1, step):
            draw.line((0, y, width, y), fill=(255, 255, 255, 25), width=2)
    elif bg_style["decoration"] == "lines":
        for _ in range(20):
            x = random.random() * width
            y = random.random() * height
            draw.line((x, y, x + 200, y + 200), fill=(255, 255, 255, 38), width=3)
    elif bg_style["decoration"] == "border":
        stroke_color = (255, 255, 255, 204) if txt_style["color"] == "#FFFFFF" else (0, 0, 0, 204)
        draw.rectangle((20, 20, width - 20, height - 20), outline=stroke_color, width=20)
        draw.rectangle((50, 50, width - 50, height - 50), outline=stroke_color, width=2)
    elif bg_style["decoration"] == "noise":
        for _ in range(5000):
            x = random.random() * width
            y = random.random() * height
            draw.rectangle((x, y, x + 2, y + 2), fill=(0, 0, 0, 13))
    elif bg_style["decoration"] == "confetti":
        colors = [(255, 199, 0, 200), (255, 0, 0, 200), (46, 49, 146, 200), (0, 158, 0, 200), (255, 0, 255, 200)]
        for _ in range(100):
            x = random.random() * width
            y = random.random() * height
            w = 8 + random.random() * 8
            h = 4 + random.random() * 4
            draw.rectangle((x, y, x + w, y + h), fill=random.choice(colors))
    elif bg_style["decoration"] == "geometric":
        for _ in range(15):
            p1 = (random.random() * width, random.random() * height)
            p2 = (random.random() * width, random.random() * height)
            p3 = (random.random() * width, random.random() * height)
            alpha = int((0.05 + random.random() * 0.1) * 255)
            draw.polygon([p1, p2, p3], fill=(255, 255, 255, alpha))
    elif bg_style["decoration"] == "neon":
        for _ in range(5):
            y = random.random() * height
            draw.line((0, y, width, y), fill=(0, 255, 255, 140), width=2)
        for _ in range(5):
            x = random.random() * width
            draw.line((x, 0, x, height), fill=(255, 0, 255, 140), width=2)

    font = get_font(56)
    text_color = parse_color(txt_style["color"])
    shadow_color = parse_color(txt_style.get("shadowColor", "rgba(0,0,0,0.3)"))
    stroke_color = parse_color(txt_style.get("stroke", "#000000"))
    words = list(text)
    lines = []
    line = ""
    max_width = width - 120
    for ch in words:
        test_line = line + ch
        if measure_text(draw, test_line, font) > max_width and line:
            lines.append(line)
            line = ch
        else:
            line = test_line
    lines.append(line)

    line_height = 70
    total_height = len(lines) * line_height
    start_y = (height - total_height) / 2

    for i, line in enumerate(lines):
        y = start_y + i * line_height
        if txt_style.get("glow"):
            for dx in range(-2, 3):
                for dy in range(-2, 3):
                    if dx == 0 and dy == 0:
                        continue
                    draw.text((width / 2 + dx, y + dy), line, font=font, fill=shadow_color, anchor="mm")
        elif txt_style.get("shadow"):
            draw.text((width / 2 + 2, y + 2), line, font=font, fill=shadow_color, anchor="mm")

        if txt_style.get("stroke"):
            draw.text((width / 2, y), line, font=font, fill=text_color, stroke_width=int(txt_style.get("strokeWidth", 2)), stroke_fill=stroke_color, anchor="mm")
        else:
            draw.text((width / 2, y), line, font=font, fill=text_color, anchor="mm")

    watermark_font = get_font(24)
    watermark_color = (text_color[0], text_color[1], text_color[2], int(0.6 * 255))
    draw.text((width / 2, height - 50), "GoodShare", font=watermark_font, fill=watermark_color, anchor="mm")

    img_rgb = img.convert("RGB")
    img_byte_arr = io.BytesIO()
    img_rgb.save(img_byte_arr, format="JPEG", quality=80)
    img_byte_arr.seek(0)
    return img_byte_arr

def upload_image_file(image_data, token):
    url = f"{BASE_URL}/api/upload"
    files = {"file": ("cover.jpg", image_data, "image/jpeg")}
    headers = {"Authorization": f"Bearer {token}"}
    try:
        res = requests.post(url, files=files, headers=headers, timeout=20)
        if res.status_code == 200:
            return res.json().get("url")
    except Exception:
        return None
    return None

def generate_title(tag, index):
    return f"{tag}{random.choice(title_prefix)}{random.choice(title_subject)} #{index:03d}"

def generate_content(tag, index):
    return f"{random.choice(content_openers)}{tag}，整体来说#{index:03d} {random.choice(content_endings)}"

print("Setting up users...")
for u in users:
    register_user(u)
    token = login_user(u)
    if token:
        user_tokens[u] = token

if not user_tokens:
    print("No users available. Exiting.")
    exit(1)

target_count = 100
created = 0
attempts = 0
max_attempts = target_count * 3

print("Creating posts...")
while created < target_count and attempts < max_attempts:
    attempts += 1
    tag = random.choice(tags)
    title = generate_title(tag, created + 1)
    content = generate_content(tag, created + 1)
    user_key = random.choice(list(user_tokens.keys()))
    token = user_tokens[user_key]
    img_bytes = draw_canvas_content(title)
    image_url = upload_image_file(img_bytes, token)
    if not image_url:
        continue

    post_body = {
        "title": title,
        "content": content,
        "tags": [tag],
        "imageUrls": [image_url],
        "coverUrl": image_url
    }
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    try:
        res = requests.post(f"{BASE_URL}/api/posts", json=post_body, headers=headers, timeout=20)
        if res.status_code == 200:
            created += 1
            print(f"[OK] {created}/{target_count} {title} {tag} {user_key}")
        else:
            print(f"[FAIL] {res.status_code} {res.text}")
    except Exception as e:
        print(f"[FAIL] {e}")
    time.sleep(0.1)

print(f"Batch creation completed. Created: {created}")
