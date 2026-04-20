import pytest
import time
import os
import random
from playwright.sync_api import sync_playwright, Page, expect
from playwright.sync_api import TimeoutError as PlaywrightTimeoutError

# Configuration
UI_BASE_URL = "http://localhost:8088"
API_BASE_URL = "http://localhost:8080"

# Unset proxy to avoid connection issues
if 'http_proxy' in os.environ: del os.environ['http_proxy']
if 'https_proxy' in os.environ: del os.environ['https_proxy']
if 'HTTP_PROXY' in os.environ: del os.environ['HTTP_PROXY']
if 'HTTPS_PROXY' in os.environ: del os.environ['HTTPS_PROXY']

# Data Pool for generating realistic test data
DATA_POOL = {
    '书籍': [
        {"title": "强烈推荐《百年孤独》", "content": "魔幻现实主义的巅峰之作，读完后久久不能平静，家族的宿命感太强烈了。"},
        {"title": "技术人必读：架构整洁之道", "content": "书中对软件架构的本质进行了深刻的剖析，对日常开发和系统设计有极大的启发。"},
        {"title": "周末读物分享", "content": "找个安静的下午，泡一杯咖啡，读一本好书，是放松身心最好的方式。"},
        {"title": "《三体》三部曲读后感", "content": "看完后对宇宙的敬畏感油然而生，黑暗森林法则让人不寒而栗，大刘的想象力太震撼了。"},
        {"title": "推荐一本心理学入门书", "content": "《被讨厌的勇气》用对话形式解读阿德勒心理学，很多观点颠覆认知，读完后对人际关系有了新的理解。"},
        {"title": "最近在读《人类群星闪耀时》", "content": "茨威格笔下的历史瞬间太精彩了，每个故事都像一部电影，尤其是发现太平洋的那一章，看得热血沸腾。"}
    ],
    '家居': [
        {"title": "我的温馨小窝改造记", "content": "刚刚布置好的客厅，换了暖色调的地毯和窗帘，阳光洒进来太舒服了！"},
        {"title": "提升幸福感的家居好物", "content": "这款香薰机真的是提升居家幸福感的神器，味道不刺鼻，晚上开着有助于睡眠。"},
        {"title": "极简主义卧室分享", "content": "丢掉不必要的杂物，只保留最核心的家具，整个空间变得通透又清爽。"},
        {"title": "厨房收纳心得分享", "content": "买了各种收纳盒，把抽屉分区后，找东西方便多了，调料瓶也都统一换成了同款，看起来整洁不少。"},
        {"title": "绿植让家更有生机", "content": "在客厅角落放了几盆大型绿植，龟背竹和琴叶榕特别好养，现在每天回家都觉得空气清新了很多。"},
        {"title": "小户型如何利用垂直空间", "content": "安装了洞洞板和壁挂置物架，把墙面利用起来，地面空间解放了，家里显大很多。"}
    ],
    '数码': [
        {"title": "评论: iPhone 16 Pro 深度体验", "content": "钛金属外观非常棒，手感轻了不少！不过高负载下电池续航还是有点不够看。"},
        {"title": "索尼降噪耳机开箱", "content": "降噪效果确实是行业第一梯队，地铁上的轰鸣声基本能被完全隔绝，音质也还不错。"},
        {"title": "机械键盘选购指南", "content": "红轴打字确实舒服，声音也不算太大。如果你是在办公室用，建议还是选静音红轴。"},
        {"title": "iPad Pro 真的能替代电脑吗", "content": "用了两个月，画画和记笔记体验无敌，但多任务处理和文件管理还是不如电脑，定位不同吧。"},
        {"title": "MacBook Air M2 体验", "content": "续航真的强，一天办公下来还能剩不少电，无风扇设计完全静音，就是高负载时会降频。"},
        {"title": "智能手表健康监测有用吗", "content": "血氧和心率监测挺准的，有次提醒我心率异常，去医院检查才发现确实有点问题，算是救了我一命。"}
    ],
    '文具': [
        {"title": "手帐爱好者的本子推荐", "content": "这本手帐本纸质非常顺滑，用钢笔写字完全不会洇墨，排版设计也很合理。"},
        {"title": "好用的中性笔测评", "content": "对比了市面上主流的几款0.5mm中性笔，这款出水最流畅，而且握胶很舒服。"},
        {"title": "国誉笔记本使用感受", "content": "巴川纸真的太适合钢笔了，超薄不透墨，一本能用很久，就是价格有点小贵。"},
        {"title": "百乐果汁笔试色", "content": "金属色系列太美了，写手账标题特别出效果，顺滑度也满分，已经集齐一套了。"},
        {"title": "复古风文具分享", "content": "最近迷上了复古风，买了牛皮纸笔记本和火漆印章，写信的感觉特别有仪式感。"},
        {"title": "文具收纳盒推荐", "content": "亚克力透明收纳盒让桌面整洁多了，彩笔按颜色排列，看着就舒服，找起来也方便。"}
    ],
    '玩具': [
        {"title": "乐高保时捷911拼搭记录", "content": "花了整整三天时间终于拼完了，机械传动结构太精妙了，成就感满满！"},
        {"title": "盲盒开箱日常", "content": "今天手气爆发，竟然抽到了隐藏款！做工很精致，细节涂装非常到位。"},
        {"title": "高达模型喷涂初体验", "content": "第一次尝试喷涂，虽然有点翻车，但看着自己配色的机体，还是很有成就感的。"},
        {"title": "Switch 推荐", "content": "塞尔达传说王国之泪真的太好玩了，自由度超高，每天都能发现新的玩法。"},
        {"title": "桌游聚会分享", "content": "和朋友玩了阿瓦隆，全程飙戏，太欢乐了，比狼人杀更适合聚会，推荐大家试试。"},
        {"title": "泡泡玛特新系列分享", "content": "这个系列的做工比之前好太多了，每个细节都很精致，摆在办公桌上每天看着心情都好。"}
    ],
    '服装': [
        {"title": "初秋穿搭分享", "content": "卡其色风衣搭配基础款白T和牛仔裤，简单又高级，非常适合现在的天气。"},
        {"title": "微胖女孩怎么穿", "content": "A字裙真的是修饰梨形身材的利器，提高腰线不仅显瘦还能拉长腿部比例。"},
        {"title": "优衣库试衣间", "content": "今年的U系列真的可，这件衬衫版型很好，面料挺括，搭配西裤或者牛仔裤都可以。"},
        {"title": "职场通勤穿搭", "content": "西装套装不一定非要黑白灰，藏青色和烟灰色更显气质，搭配乐福鞋既正式又舒适。"},
        {"title": "冬季羽绒服选购指南", "content": "充绒量和蓬松度很重要，北方要选200g以上、650蓬以上的，南方150g左右就够了。"},
        {"title": "复古运动风穿搭", "content": "老爹鞋搭配卫裤和卫衣，再加个棒球帽，舒适又时髦，周末出门首选。"}
    ],
    '美食': [
        {"title": "美味家庭烘培：巧克力曲奇", "content": "周末尝试了自己烤曲奇，满屋子都是黄油和巧克力的香味，刚出炉的时候最好吃！"},
        {"title": "探店：藏在巷子里的宝藏面馆", "content": "汤头浓郁，面条劲道，特别是那块大排，炖得软烂入味，简直绝了。"},
        {"title": "零失败的意大利面做法", "content": "终于掌握了正宗培根蛋酱意面的做法，关键是使用猪颊肉和佩科里诺奶酪，不要放淡奶油！"},
        {"title": "懒人电饭煲食谱", "content": "用电饭煲做红烧肉，不用看火不会糊，把料放进去按煮饭键就行，软烂入味特别下饭。"},
        {"title": "咖啡入门器具推荐", "content": "新手建议从法压壶开始，便宜又好清洗，还能品尝到咖啡最原始的风味。"},
        {"title": "周末早午餐分享", "content": "做了班尼迪克蛋，荷兰汁第一次就成功了，搭配牛油果和烟熏三文鱼，完美复刻brunch店的味道。"}
    ],
 "生活": [
    {
      "title": "最近入手的空气炸锅，真香",
      "content": "纠结了好久终于买了，用下来太值了！做了炸鸡翅和薯条，不用油还酥脆，清洗也方便，懒人福音。"
    },
    {
      "title": "这个收纳盒拯救了我的衣柜",
      "content": "以前衣柜乱成一团，买了分层收纳盒和抽屉式整理箱，空间利用率翻倍，找衣服再也不用翻半天了。"
    },
    {
      "title": "回购三次的氨基酸洁面",
      "content": "温和不紧绷，洗完脸滑滑的，敏感肌完全没问题。性价比超高，几十块钱能用两三个月。"
    },
    {
      "title": "桌面加湿器提升幸福感",
      "content": "小小一个不占地方，水雾很细，开一整天也不会湿桌面。办公室和卧室各放了一个，秋冬必备。"
    },
    {
      "title": "无限回购的冻干咖啡",
      "content": "冷热水都能冲开，味道不酸不苦刚刚好。小包装随身带，出差旅行也能喝到好咖啡，比速溶强太多。"
    },
    {
      "title": "厨房湿巾真是懒人救星",
      "content": "每次做完饭抽一张擦灶台和油烟机，去油能力很强，用完就扔不用洗抹布，厨房一直干干净净。"
    },
    {
      "title": "入手了人体工学椅",
      "content": "腰托和头枕都很贴合，坐一天也不累。以前腰酸背痛的毛病好多了，虽然贵点但健康投资值得。"
    },
    {
      "title": "这个保温杯让我多喝了水",
      "content": "316不锈钢材质，保温效果很好，早上装的热水到下午还烫嘴。颜值也高，现在出门必带。"
    },
    {
      "title": "LED化妆镜的神奇之处",
      "content": "三色光可调，显色很真实，化妆不再有色差。底座还能放小物件，用了就回不去普通镜子了。"
    }
  ]
}

def generate_user_data(index, is_ai=False):
    """Generate random test user data from the pool"""
    user_data = {
        "username": f"test_user_{index}_{random.randint(1000, 9999)}",
        "email": f"test_{index}_{random.randint(1000, 9999)}@example.com",
        "password": "password123",
        "cover_style": random.choice(["备忘", "书本", "边框", "手写", "涂写"]),
        "cover_color": random.choice(["白色", "黄色", "蓝色", "紫色", "棕色"]),
    }

    if is_ai:
        tag = random.choice(list(DATA_POOL.keys()))
        user_data["tags"] = [tag]
        sample_data = random.choice(DATA_POOL[tag])
        user_data["ai_keyword"] = f"'{sample_data['title']}'"
    else:
        tag = random.choice(list(DATA_POOL.keys()))
        post_data = random.choice(DATA_POOL[tag])
        user_data["tags"] = [tag]
        user_data["post_title"] = f"{post_data['title']} - {random.randint(1, 100)}"
        user_data["post_content"] = post_data['content']

    return user_data

# Generate 5 test users dynamically
TEST_USERS = [generate_user_data(i) for i in range(1, 5)]
TEST_USERS.append(generate_user_data(5, is_ai=True))

@pytest.fixture(scope="module")
def browser():
    """Setup Playwright browser"""
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=False)
        yield browser
        browser.close()

@pytest.fixture(scope="function")
def page(browser):
    """Create a new page for each test"""
    context = browser.new_context(viewport={'width': 1920, 'height': 1080})
    page = context.new_page()
    yield page
    page.close()
    context.close()

def register_user_action(page: Page, user):
    """Helper: Register user action"""
    print(f"Navigating to register page: {UI_BASE_URL}/register")
    page.goto(f"{UI_BASE_URL}/register")

    try:
        page.wait_for_selector("input[type='text']", timeout=5000)

        page.fill("input[type='text']", user['username'])
        page.fill("input[type='password']", user['password'])
        page.fill("input[placeholder='邮箱']", user['email'])
        page.fill("input[placeholder='确认密码']", user['password'])
        page.fill("input[placeholder='验证码']", "123456")

        page.click("button.register-btn")

        try:
            page.wait_for_url("**/login", timeout=3000)
            print("Redirected to login page.")
            return True
        except PlaywrightTimeoutError:
            try:
                error = page.locator(".el-message--error").first
                if error.is_visible():
                    error_text = error.text_content()
                    print(f"Registration message: {error_text}")

                    if any(x in error_text for x in ["exist", "已存在", "重复", "taken", "Duplicate"]):
                        print("User already exists, proceeding to login.")
                        page.goto(f"{UI_BASE_URL}/login")
                        return True
                    return False
            except:
                if "/login" in page.url:
                    return True
                print("Registration timed out. Assuming user exists, proceeding to login.")
                page.goto(f"{UI_BASE_URL}/login")
                return True

    except Exception as e:
        print(f"Registration exception: {e}")
        return False

def login_action(page: Page, username, password):
    """Helper: Login action"""
    print(f"Navigating to login page: {UI_BASE_URL}/login")
    page.goto(f"{UI_BASE_URL}/login")

    try:
        page.wait_for_selector("input[type='text']", timeout=10000)

        page.fill("input[type='text']", username)
        page.fill("input[type='password']", password)
        page.click("button.el-button--primary")

        page.wait_for_url(lambda url: url != f"{UI_BASE_URL}/login", timeout=10000)
        time.sleep(2)
        return True
    except Exception as e:
        print(f"Login exception: {e}")
        return False

def publish_post_action(page: Page, user_data):
    """Helper: Publish post action"""
    print(f"Navigating to publish page: {UI_BASE_URL}/publish")
    page.goto(f"{UI_BASE_URL}/publish")

    try:
        if 'ai_keyword' in user_data:
            print(f"Using AI Generation with keyword: {user_data['ai_keyword']}")
            page.wait_for_selector(".ai-input-group input", timeout=15000)
            page.fill(".ai-input-group input", user_data['ai_keyword'])
            page.click(".ai-input-group button")

            print("Waiting for AI generation completion message...")
            try:
                page.wait_for_selector("text=AI生成完成", timeout=60000)
                print("AI generation completed successfully!")
                time.sleep(2)
            except PlaywrightTimeoutError:
                print("Warning: AI generation success message not detected within timeout.")
        else:
            if 'post_title' in user_data:
                page.wait_for_selector("input[placeholder*='填写标题']", timeout=15000)
                page.fill("input[placeholder*='填写标题']", user_data['post_title'])

            if 'post_content' in user_data:
                editor = page.locator(".ql-editor")
                editor.click()
                editor.fill(user_data['post_content'])

        if 'cover_style' in user_data:
            style_name = user_data['cover_style']
            print(f"Selecting cover style: {style_name}")
            style_cards = page.locator(".style-card")
            count = style_cards.count()
            for i in range(count):
                if style_name in style_cards.nth(i).text_content():
                    style_cards.nth(i).click()
                    break
            time.sleep(1)

        if 'cover_color' in user_data:
            print(f"Selecting a random cover color...")
            color_circles = page.locator(".color-circle")
            count = color_circles.count()
            if count > 0:
                random_index = random.randint(0, count - 1)
                color_circles.nth(random_index).click()
                print(f"Selected color index: {random_index}")
            else:
                print("No color options found.")
            time.sleep(1)

        if 'tags' in user_data and user_data['tags']:
            print(f"Selecting tags: {user_data['tags']}")
            page.click(".el-select")
            time.sleep(1)

            tag_input = page.locator(".el-select__input")
            for tag in user_data['tags']:
                tag_input.fill(tag)
                time.sleep(0.5)
                tag_input.press("Enter")
                time.sleep(0.5)

            page.locator("body").click()
        else:
            print("Skipping tags selection (Optional)")

        print("Clicking submit...")
        page.click(".submit-btn")

        print("Waiting for confirm dialog...")
        page.wait_for_selector("button:has-text('确认发布')", timeout=15000)
        page.click("button:has-text('确认发布')")

        time.sleep(3)
        return page.url != f"{UI_BASE_URL}/publish"

    except Exception as e:
        print(f"Publish exception: {e}")
        page.screenshot(path=f"publish_failed_{user_data['username']}.png")
        return False

@pytest.mark.parametrize("user_data", TEST_USERS)
def test_user_publish_flow(page, user_data):
    """End-to-End Test: Register -> Login -> Publish for each user"""
    print(f"\n--- Testing User: {user_data['username']} ---")

    assert register_user_action(page, user_data) is True, f"Registration failed for {user_data['username']}"
    assert login_action(page, user_data['username'], user_data['password']) is True, f"Login failed for {user_data['username']}"
    assert publish_post_action(page, user_data) is True, f"Publish failed for {user_data['username']}"

if __name__ == "__main__":
    pytest.main(["-v", "-s", __file__])
