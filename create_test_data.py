import pytest
import time
import os
import random
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.edge.options import Options as EdgeOptions
from selenium.webdriver.chrome.options import Options as ChromeOptions
from selenium.common.exceptions import TimeoutException, NoSuchElementException
import selenium;

# Configuration
UI_BASE_URL = "http://localhost:8088"
API_BASE_URL = "http://localhost:8080"

# Unset proxy to avoid ERR_PROXY_CONNECTION_FAILED in Docker or local env
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
        {"title": "Switch 游戏推荐", "content": "塞尔达传说王国之泪真的太好玩了，自由度超高，每天都能发现新的玩法。"},
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
    '生活': [
        {"title": "今日心情日记", "content": "今天天气真好，去公园散了步，看到很多人在放风筝，感觉生活节奏慢下来挺好的。"},
        {"title": "培养早起习惯的第10天", "content": "早起后发现一天的时间变长了，可以从容地吃个早餐，读几页书，状态比以前好太多。"},
        {"title": "健身打卡一个月的变化", "content": "虽然体重只减了2斤，但是腰围细了3厘米，精神状态也好了很多，继续坚持！"},
        {"title": "极简护肤理念分享", "content": "简化步骤后皮肤反而变好了，现在只用洁面、保湿和防晒，少即是多真的没错。"},
        {"title": "周末去爬山了", "content": "爬了三个小时到山顶，虽然累但是视野太开阔了，所有的烦恼都忘了，下次还要去。"},
        {"title": "断舍离第一天", "content": "收拾出三大袋不穿的衣服和不用的杂物，捐给了回收站，家里清爽了，心情也变好了。"}
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
        # For AI user, pick a random tag and generate a related keyword
        tag = random.choice(list(DATA_POOL.keys()))
        user_data["tags"] = [tag]
        # Use a title from the pool as the AI keyword prompt
        sample_data = random.choice(DATA_POOL[tag])
        user_data["ai_keyword"] = f"'{sample_data['title']}'"
    else:
        # For normal user, pick a random tag and corresponding content
        tag = random.choice(list(DATA_POOL.keys()))
        post_data = random.choice(DATA_POOL[tag])
        
        user_data["tags"] = [tag]
        # Append random string to title/content to ensure uniqueness if needed, 
        # but the random username/email already prevents DB conflicts. 
        # Kept slight randomization for visual variety.
        user_data["post_title"] = f"{post_data['title']} - {random.randint(1, 100)}"
        user_data["post_content"] = post_data['content']
        
    return user_data

# Generate 5 test users dynamically
TEST_USERS = [generate_user_data(i) for i in range(1, 5)]
# Add one AI specific user
TEST_USERS.append(generate_user_data(5, is_ai=True))

@pytest.fixture(scope="module")
def driver():
    """Setup Selenium WebDriver (Chrome preferred, fallback to Edge)"""
    print(selenium.__version__)
    _driver = None
    try:
        print("\nAttempting to start Chrome driver...")
        options = ChromeOptions()
        options.add_argument("--start-maximized")
        options.add_argument("--no-sandbox")
        options.add_argument("--disable-dev-shm-usage")
        options.add_argument("--disable-extensions")
        options.add_argument("--remote-allow-origins=*")
        # options.add_argument("--headless") # Optional: Run headless
        _driver = webdriver.Chrome(options=options)
    except Exception as e:
        print(f"Chrome driver failed: {e}")
        try:
            print("Attempting to start Edge driver...")
            options = EdgeOptions()
            options.add_argument("--start-maximized")
            options.add_argument("--no-sandbox")
            options.add_argument("--disable-dev-shm-usage")
            options.add_argument("--disable-extensions")
            options.add_argument("--remote-allow-origins=*")
            _driver = webdriver.Edge(options=options)
        except Exception as e:
            print(f"Edge driver failed: {e}")
            raise Exception("No suitable WebDriver found. Please ensure Chrome or Edge driver is installed.")
            
    yield _driver
    
    if _driver:
        print("\nClosing driver...")
        _driver.quit()

#为下一次测试清理测试数据
@pytest.fixture(autouse=True)
def run_around_tests(driver):
    """Cleanup after each test case automatically"""
    yield
    print(f"\nCleaning up session...")
    try:
        driver.delete_all_cookies()
        driver.execute_script("window.localStorage.clear();")
        driver.execute_script("window.sessionStorage.clear();")
        driver.refresh()
        time.sleep(2)
    except Exception as e:
        print(f"Cleanup failed: {e}")

def register_user_action(driver, user):
    """Helper: Register user action""" # 注册用户，填写注册表单，提交注册请求，返回注册结果
    print(f"Navigating to register page: {UI_BASE_URL}/register")
    driver.get(f"{UI_BASE_URL}/register")
    
    wait = WebDriverWait(driver, 5)
    
    try:
        wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
        
        driver.find_element(By.CSS_SELECTOR, "input[type='text']").send_keys(user['username'])
        driver.find_element(By.CSS_SELECTOR, "input[type='password']").send_keys(user['password'])
        driver.find_element(By.XPATH, "//input[@placeholder='邮箱']").send_keys(user['email'])
        driver.find_element(By.XPATH, "//input[@placeholder='确认密码']").send_keys(user['password'])
        driver.find_element(By.XPATH, "//input[@placeholder='验证码']").send_keys("123456")
        
        register_btn = driver.find_element(By.CSS_SELECTOR, "button.register-btn")
        register_btn.click()
        
        try:
            # Wait for redirect to login
            # Use a shorter wait initially to catch error messages if they appear quickly
            wait_short = WebDriverWait(driver, 3)
            wait_short.until(EC.url_contains("/login"))
            print("Redirected to login page.")
            return True
        except TimeoutException:
            # Check for error message
            try:
                error = driver.find_element(By.CSS_SELECTOR, ".el-message--error")
                error_text = error.text
                print(f"Registration message: {error_text}")
                
                # Check for various "exists" messages (English or Chinese)
                # Added 'taken' for "Username is already taken"
                if any(x in error_text for x in ["exist", "已存在", "重复", "taken", "Duplicate"]):
                    print("User already exists, proceeding to login.")
                    # Force navigate to login page if not redirected
                    driver.get(f"{UI_BASE_URL}/login")
                    return True
                return False
            except NoSuchElementException:
                # If no error message and no redirect, check URL again just in case
                if "/login" in driver.current_url:
                    return True
                print("Registration timed out or error message missed. Assuming user exists, proceeding to login check.")
                driver.get(f"{UI_BASE_URL}/login")
                return True
            
    except Exception as e:
        print(f"Registration exception: {e}")
        return False

def login_action(driver, username, password):
    """Helper: Login action""" # 登录用户，填写登录表单，提交登录请求，返回登录结果
    print(f"Navigating to login page: {UI_BASE_URL}/login")
    driver.get(f"{UI_BASE_URL}/login")
    
    wait = WebDriverWait(driver, 10)
    
    try:
        username_input = wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[type='text']")))
        password_input = driver.find_element(By.CSS_SELECTOR, "input[type='password']")
        login_btn = driver.find_element(By.CSS_SELECTOR, "button.el-button--primary")
        
        username_input.clear()
        username_input.send_keys(username)
        password_input.clear()
        password_input.send_keys(password)
        
        login_btn.click()
        
        # Wait for redirect
        wait.until(EC.url_changes(f"{UI_BASE_URL}/login"))
        time.sleep(2) 
        return True
    except Exception as e:
        print(f"Login exception: {e}")
        return False

def publish_post_action(driver, user_data):
    """Helper: Publish post action""" # 发布帖子，填写发布表单，提交发布请求，返回发布结果
    print(f"Navigating to publish page: {UI_BASE_URL}/publish")
    driver.get(f"{UI_BASE_URL}/publish")
    
    wait = WebDriverWait(driver, 15)
    
    try:
        # AI Generation
        if 'ai_keyword' in user_data:
            print(f"Using AI Generation with keyword: {user_data['ai_keyword']}")
            ai_input = wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, ".ai-input-group input")))
            ai_input.clear()
            ai_input.send_keys(user_data['ai_keyword'])
            
            # Click Generate Button (inside append slot)
            generate_btn = driver.find_element(By.CSS_SELECTOR, ".ai-input-group button")
            generate_btn.click()
            
            # Wait for content to be generated
            print("Waiting for AI generation completion message...")
            # Wait for success message (toast) containing "AI生成完成"
            try:
                # Use a generous timeout as AI generation can be slow
                ai_wait = WebDriverWait(driver, 60)
                # XPath to find element containing the specific text
                success_msg = ai_wait.until(EC.presence_of_element_located((By.XPATH, "//*[contains(text(), 'AI生成完成')]")))
                print("AI generation completed successfully!")
                # Give a small buffer for UI updates after message appears
                time.sleep(2)
            except TimeoutException:
                print("Warning: AI generation success message not detected within timeout.")
        else:
            # Manual Title & Content
            if 'post_title' in user_data:
                title_input = wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, "input[placeholder*='填写标题']")))
                title_input.send_keys(user_data['post_title'])
            
            if 'post_content' in user_data:
                editor = driver.find_element(By.CSS_SELECTOR, ".ql-editor")
                editor.click()
                editor.send_keys(user_data['post_content'])
        
        # Cover Style Selection
        if 'cover_style' in user_data:
            style_name = user_data['cover_style']
            print(f"Selecting cover style: {style_name}")
            # Find style card by text
            style_cards = driver.find_elements(By.CSS_SELECTOR, ".style-card")
            for card in style_cards:
                if style_name in card.text:
                    card.click()
                    break
            time.sleep(1)

        # 配色 (Updated to use index based selection as UI uses color circles without text)
        if 'cover_color' in user_data:
            print(f"Selecting a random cover color...")
            # Find color circles
            color_circles = driver.find_elements(By.CSS_SELECTOR, ".color-circle")
            if color_circles:
                random_index = random.randint(0, len(color_circles) - 1)
                color_circles[random_index].click()
                print(f"Selected color index: {random_index}")
            else:
                print("No color options found.")
            time.sleep(1)
            
        # Tags (Optional based on config)
        if 'tags' in user_data and user_data['tags']:
            print(f"Selecting tags: {user_data['tags']}")
            tag_select = driver.find_element(By.CSS_SELECTOR, ".el-select")
            tag_select.click()
            time.sleep(1)
            
            tag_input = driver.find_element(By.CSS_SELECTOR, ".el-select__input")
            for tag in user_data['tags']:
                tag_input.send_keys(tag)
                time.sleep(0.5)
                tag_input.send_keys(u'\ue007') # Enter
                time.sleep(0.5)
                
            driver.find_element(By.TAG_NAME, "body").click()
        else:
            print("Skipping tags selection (Optional)")
        
        # Submit
        print("Clicking submit...")
        submit_btn = driver.find_element(By.CSS_SELECTOR, ".submit-btn")
        submit_btn.click()
        
        # Confirm
        print("Waiting for confirm dialog...")
        confirm_btn = wait.until(EC.element_to_be_clickable((By.XPATH, "//button[contains(., '确认发布')]")))
        confirm_btn.click()
        
        # Verify
        time.sleep(3)
        return driver.current_url != f"{UI_BASE_URL}/publish"
            
    except Exception as e:
        print(f"Publish exception: {e}")
        driver.save_screenshot(f"publish_failed_{user_data['username']}.png")
        return False

@pytest.mark.parametrize("user_data", TEST_USERS)
def test_user_publish_flow(driver, user_data):
    """End-to-End Test: Register -> Login -> Publish for each user""" # 测试每个用户的注册、登录、发布流程
    print(f"\n--- Testing User: {user_data['username']} ---")

    # 1. Register
    assert register_user_action(driver, user_data) is True, f"Registration failed for {user_data['username']}"
    
    # 2. Login
    assert login_action(driver, user_data['username'], user_data['password']) is True, f"Login failed for {user_data['username']}"
    
    # 3. Publish
    assert publish_post_action(driver, user_data) is True, f"Publish failed for {user_data['username']}"
    
    # 4. Logout (Cleanup for next test iteration)
    # Cleanup handled by autouse fixture now

if __name__ == "__main__":
    # Allow running directly with python
    pytest.main(["-v", "-s", __file__])
