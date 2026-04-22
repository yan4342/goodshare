import pytest
import time
import os
import random
import sys

# Fix Windows console encoding
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')
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

# Data Pool for generating realistic test data (imported from separate module)
from test_data_pool import DATA_POOL

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
TEST_USERS.append(generate_user_data(5, is_ai=False))

@pytest.fixture(scope="module")
def browser():
    """Setup Playwright browser"""
    with sync_playwright() as p:
        # 使用系统安装的 Chrome（自动查找）
        browser = p.chromium.launch(
            headless=True,
            channel="msedge"  # 可选: "chrome", "msedge", "chrome-beta", "chrome-dev"
        )
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
                editor = page.locator(".ql-editor").first
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

        time.sleep(2)
        page.wait_for_selector("text=发布成功", timeout=2500)
        return True

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
    
    # First post using original user data
    assert publish_post_action(page, user_data) is True, f"First publish failed for {user_data['username']}"
    
    # Generate second post data with different content
    second_post_data = user_data.copy()
    # Select a different tag and post from the data pool
    available_tags = list(DATA_POOL.keys())
    if available_tags:
        new_tag = random.choice(available_tags)
        new_post = random.choice(DATA_POOL[new_tag])
        second_post_data['tags'] = [new_tag]
        second_post_data['post_title'] = f"{new_post['title']} - {random.randint(1, 100)}"
        second_post_data['post_content'] = new_post['content']
        # Keep the same cover style and color as original
        # Ensure ai_keyword is removed for non-AI posts
        if 'ai_keyword' in second_post_data:
            del second_post_data['ai_keyword']
    
    # Second post
    assert publish_post_action(page, second_post_data) is True, f"Second publish failed for {user_data['username']}"


if __name__ == "__main__":
    pytest.main(["-v", "-s", __file__])
