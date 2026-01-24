import requests
import json
import time
import random
import urllib.parse
from PIL import Image, ImageDraw, ImageFont
import io

BASE_URL = "http://localhost:8080"
PASSWORD = "password123"

users = ["testUser1", "testUser2", "testUser3", "testUser4", "testUser5"]
user_tokens = {}

# Map categories to English for better image text rendering
category_en_map = {
    "书籍": "Books",
    "数码": "Digital",
    "家居": "Home",
    "玩具": "Toys",
    "服装": "Fashion",
    "文具": "Stationery",
    "美食": "Food"
}

def register_user(username):
    url = f"{BASE_URL}/api/auth/register"
    payload = {
        "username": username,
        "password": PASSWORD,
        "email": f"{username}@example.com",
        "nickname": f"Nick_{username}"
    }
    try:
        requests.post(url, json=payload)
    except Exception as e:
        pass # Ignore if exists

def login_user(username):
    url = f"{BASE_URL}/api/auth/login"
    payload = {
        "username": username,
        "password": PASSWORD
    }
    try:
        res = requests.post(url, json=payload)
        if res.status_code == 200:
            return res.json().get("accessToken")
    except Exception as e:
        print(f"Login failed for {username}: {e}")
    return None

print("Setting up users...")
for u in users:
    register_user(u)
    token = login_user(u)
    if token:
        user_tokens[u] = token

if not user_tokens:
    print("No users available. Exiting.")
    exit(1)

categories = {
    "书籍": [
        {"title": "读《百年孤独》有感", "content": "这真是一本伟大的魔幻现实主义巨著，马尔克斯的文笔太细腻了。"},
        {"title": "Java编程思想推荐", "content": "作为Java程序员，这本书是必读的经典，虽然有点厚，但值得一读。"},
        {"title": "最近看的一本历史书", "content": "明朝那些事儿写得真有趣，把历史写活了。"},
        {"title": "科幻迷必读三体", "content": "刘慈欣的三体想象力太宏大了，尤其是黑暗森林法则。"}
    ],
    "数码": [
        {"title": "iPhone 16 使用体验", "content": "新一代的iPhone在拍照方面提升很大，但是续航似乎没有太多惊喜。"},
        {"title": "机械键盘入坑指南", "content": "红轴、青轴、茶轴到底怎么选？适合自己的才是最好的。"},
        {"title": "索尼大法好", "content": "新入手的微单相机，对焦速度真的快，色彩也很讨喜。"},
        {"title": "MacBook Pro M3 测评", "content": "性能怪兽，剪辑4K视频毫无压力，就是价格稍微有点贵。"}
    ],
    "家居": [
        {"title": "极简主义装修风格", "content": "断舍离之后，家里变得宽敞多了，心情也变好了。"},
        {"title": "宜家好物推荐", "content": "这个收纳盒真的是神器，便宜又好用，推荐给大家。"},
        {"title": "智能家居改造计划", "content": "把家里的灯光、窗帘都接入了米家，语音控制太方便了。"},
        {"title": "阳台改造小花园", "content": "种了一些多肉和绿萝，每天看着它们生长，感觉很治愈。"}
    ],
    "玩具": [
        {"title": "乐高布加迪拼装记录", "content": "花了整整一个周末才拼好，机械结构太精密了，不得不佩服乐高的设计。"},
        {"title": "高达模型分享", "content": "MG独角兽，爆甲模式帅炸了，就是贴纸有点多。"},
        {"title": "Switch游戏推荐", "content": "塞尔达传说荒野之息，真的是开放世界的巅峰之作。"},
        {"title": "怀旧童年四驱车", "content": "买了一辆旋风冲锋，找回了童年的快乐。"}
    ],
    "服装": [
        {"title": "优衣库春夏穿搭", "content": "基础款也能穿出高级感，关键是颜色的搭配。"},
        {"title": "复古风穿搭分享", "content": "去古着店淘了一件牛仔外套，非常有味道。"},
        {"title": "运动鞋收藏", "content": "AJ1真的是经典，虽然现在溢价很高，但还是忍不住想买。"},
        {"title": "职场通勤穿搭", "content": "西装外套配牛仔裤，既正式又不失休闲感。"}
    ],
    "文具": [
        {"title": "百乐果汁笔试色", "content": "颜色很正，书写顺滑，做手账必备。"},
        {"title": "手账入坑第一天", "content": "买了很多胶带和贴纸，希望能坚持记录生活。"},
        {"title": "凌美钢笔使用感受", "content": "狩猎者系列性价比很高，适合学生党入门。"},
        {"title": "国誉自我手账本", "content": "时间轴的设计很科学，能够很好地规划每天的时间。"}
    ],
    "美食": [
        {"title": "家庭版红烧肉做法", "content": "关键是要炒糖色，还有要小火慢炖，肥而不腻。"},
        {"title": "探店网红火锅", "content": "排队两小时才吃到，味道确实不错，服务也很好。"},
        {"title": "自制提拉米苏", "content": "不用烤箱也能做的甜点，手指饼干吸满了咖啡酒，味道很正宗。"},
        {"title": "深夜食堂泡面法则", "content": "加个荷包蛋，再加根火腿肠，简直是人间美味。"}
    ]
}

# --- Image Generation Logic mimicking Publish.vue ---
cover_styles = [
    { 'name': '粉嫩', 'type': 'gradient', 'colors': ['#FF9A9E', '#FECFEF'], 'decoration': 'circles' },
    { 'name': '紫罗兰', 'type': 'gradient', 'colors': ['#a18cd1', '#fbc2eb'], 'decoration': 'circles' },
    { 'name': '清新', 'type': 'gradient', 'colors': ['#84fab0', '#8fd3f4'], 'decoration': 'circles' },
    { 'name': '暗黑', 'type': 'gradient', 'colors': ['#434343', '#000000'], 'decoration': 'grid' },
    { 'name': '日落', 'type': 'gradient', 'colors': ['#fa709a', '#fee140'], 'decoration': 'lines' },
    { 'name': '幽蓝', 'type': 'gradient', 'colors': ['#30cfd0', '#330867'], 'decoration': 'bubbles' },
    { 'name': '纯净白', 'type': 'solid', 'colors': ['#ffffff'], 'decoration': 'border' },
    { 'name': '复古纸张', 'type': 'solid', 'colors': ['#f4e4bc'], 'decoration': 'noise' },
    { 'name': '科技蓝', 'type': 'gradient', 'colors': ['#000428', '#004e92'], 'decoration': 'grid' }
]

def hex_to_rgb(hex_color):
    hex_color = hex_color.lstrip('#')
    return tuple(int(hex_color[i:i+2], 16) for i in (0, 2, 4))

def create_gradient(width, height, color1, color2):
    base = Image.new('RGB', (width, height), color1)
    top = Image.new('RGB', (width, height), color2)
    mask = Image.new('L', (width, height))
    mask_data = []
    for y in range(height):
        for x in range(width):
            mask_data.append(int(255 * (y / height)))
    mask.putdata(mask_data)
    base.paste(top, (0, 0), mask)
    return base

def draw_cover(text):
    style = random.choice(cover_styles)
    width, height = 600, 800
    
    if style['type'] == 'solid':
        img = Image.new('RGB', (width, height), hex_to_rgb(style['colors'][0]))
    else:
        img = create_gradient(width, height, hex_to_rgb(style['colors'][0]), hex_to_rgb(style['colors'][1]))
    
    draw = ImageDraw.Draw(img, 'RGBA')
    
    # Decorations
    if style['decoration'] in ['circles', 'bubbles']:
        count = 30 if style['decoration'] == 'bubbles' else 50
        for _ in range(count):
            x = random.randint(0, width)
            y = random.randint(0, height)
            r = random.randint(10, 80)
            fill_color = (255, 255, 255, 30)
            draw.ellipse([x-r, y-r, x+r, y+r], fill=fill_color)
    elif style['decoration'] == 'grid':
        step = 50
        for x in range(0, width, step):
            draw.line([(x, 0), (x, height)], fill=(255, 255, 255, 30), width=2)
        for y in range(0, height, step):
            draw.line([(0, y), (width, y)], fill=(255, 255, 255, 30), width=2)
    elif style['decoration'] == 'lines':
        for i in range(20):
            x = random.randint(0, width)
            y = random.randint(0, height)
            draw.line([(x, y), (x + 200, y + 200)], fill=(255, 255, 255, 40), width=3)
    elif style['decoration'] == 'noise':
        for i in range(5000):
            x = random.randint(0, width)
            y = random.randint(0, height)
            draw.point((x, y), fill=(0, 0, 0, 15))
            
    # Text
    text_color = (255, 255, 255)
    if style['colors'][0].lower() in ['#ffffff', '#f4e4bc']:
        text_color = (50, 50, 50)
        
    font = None
    # Try loading Chinese fonts common on Windows
    font_names = ["msyh.ttc", "simhei.ttf", "simsun.ttc", "arial.ttf"]
    for font_name in font_names:
        try:
            font = ImageFont.truetype(font_name, 60)
            break
        except:
            continue
            
    if font is None:
        font = ImageFont.load_default()
        
    chars_per_line = 7
    lines = [text[i:i+chars_per_line] for i in range(0, len(text), chars_per_line)]
    
    total_height = len(lines) * 80
    current_y = (height - total_height) / 2
    
    for line in lines:
        bbox = draw.textbbox((0, 0), line, font=font)
        text_w = bbox[2] - bbox[0]
        draw.text(((width - text_w) / 2, current_y), line, font=font, fill=text_color)
        current_y += 80
        
    # Watermark
    wm_font = None
    for font_name in font_names:
        try:
            wm_font = ImageFont.truetype(font_name, 24)
            break
        except:
            continue
    if wm_font is None:
        wm_font = ImageFont.load_default()
        
    draw.text((width/2 - 60, height - 50), "GoodShare", font=wm_font, fill=text_color)
    
    img_byte_arr = io.BytesIO()
    img.save(img_byte_arr, format='JPEG', quality=85)
    img_byte_arr.seek(0)
    return img_byte_arr

def upload_image_file(image_data, token):
    url = f"{BASE_URL}/api/upload"
    files = {'file': ('cover.jpg', image_data, 'image/jpeg')}
    headers = {'Authorization': f'Bearer {token}'}
    try:
        res = requests.post(url, files=files, headers=headers)
        if res.status_code == 200:
            return res.json().get('url')
    except Exception as e:
        print(f"Upload error: {e}")
    return None

print("Creating posts...")
for tag, items in categories.items():
    print(f"Processing tag: {tag}")
    for item in items:
        user_key = random.choice(users)
        token = user_tokens.get(user_key)
        
        # Generate custom cover image using Publish.vue logic
        img_bytes = draw_cover(item['title'])
        image_url = upload_image_file(img_bytes, token)
        
        if not image_url:
             # Fallback
             tag_en = category_en_map.get(tag, "GoodShare")
             img_text = urllib.parse.quote(f"{tag_en} - {item['title'][:5]}...")
             image_url = f"https://placehold.co/800x600/e0e0e0/333333?text={img_text}"

        post_body = {
            "title": item["title"],
            "content": item["content"],
            "tags": [tag],
            "imageUrls": [image_url]
        }
        
        headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        }
        
        try:
            res = requests.post(f"{BASE_URL}/api/posts", json=post_body, headers=headers)
            if res.status_code == 200:
                print(f"  [OK] Created post: {item['title']} by {user_key}")
            else:
                print(f"  [FAIL] Failed to create post: {item['title']} - {res.text}")
        except Exception as e:
            print(f"  [FAIL] Error creating post: {item['title']} - {e}")
        
        time.sleep(0.2)

print("Batch creation completed.")
