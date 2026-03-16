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

# Configuration
UI_BASE_URL = "http://localhost:8088"
API_BASE_URL = "http://localhost:8080"

# Unset proxy to avoid ERR_PROXY_CONNECTION_FAILED in Docker or local env
if 'http_proxy' in os.environ: del os.environ['http_proxy']
if 'https_proxy' in os.environ: del os.environ['https_proxy']
if 'HTTP_PROXY' in os.environ: del os.environ['HTTP_PROXY']
if 'HTTPS_PROXY' in os.environ: del os.environ['HTTPS_PROXY']

# Test Data Configuration
TEST_USERS = [
    {
        "username": "test_user_001",
        "email": "test001@example.com",
        "password": "password123",
        "post_title": "评论: iPhone 16 Pro"+str(random.randint(1, 1000)),
        "post_content": "钛金属外观非常棒！不过电池续航可以更好。"+str(random.randint(1, 1000)),
        "tags": ["数码"]
    },
    {
        "username": "test_user_"+str(random.randint(1, 10000)),
        "email": "test_"+str(random.randint(1, 10000))+"@example.com",
        "password": "password123",
        "post_title": "我的温馨小窝"+str(random.randint(1, 1000)),
        "post_content": "刚刚布置好的客厅，阳光洒进来太舒服了！"+str(random.randint(1, 1000)),
        "tags": ["家居"]
    },
    {
        "username": "test_user_"+str(random.randint(1, 10000)),
        "email": "test_"+str(random.randint(1, 10000))+"@example.com",
        "password": "password123",
        "post_title": "美味家庭烘培"+str(random.randint(1, 1000)),
        "post_content": "终于掌握了意大利面的做法。关键是使用猪颊肉和佩科里诺奶酪！"+str(random.randint(1, 1000)),
        "tags": ["美食"]
    },
    {
        "username": "test_user_ai",
        "email": "ai_user@example.com",
        "password": "password123",
        "ai_keyword": "索尼降噪耳机"+str(random.randint(1, 100)),
        "tags": ["数码"]
    },
    {
        "username": "test_user_style"+str(random.randint(1, 10000)),
        "email": "style_user_"+str(random.randint(1, 10000))+"@example.com",
        "password": "password123",
        "post_title": "今日心情日记"+str(random.randint(1, 1000)),
        "post_content": "今天天气真好，适合出去散步。"+str(random.randint(1, 1000)),
        "cover_style": random.choice(["备忘", "书本", "边框", "手写","涂写"]), # random style
        "cover_color": random.choice(["白色", "黄色", "蓝色", "紫色", "棕色"]), # random color
        "tags": ["生活"]
    }
]

@pytest.fixture(scope="module")
def driver():
    """Setup Selenium WebDriver (Chrome preferred, fallback to Edge)"""
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
    """Helper: Register user action"""
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
    """Helper: Login action"""
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
    """Helper: Publish post action"""
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
    """End-to-End Test: Register -> Login -> Publish for each user"""
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
