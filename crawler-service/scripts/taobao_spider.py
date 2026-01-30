import sys
import json
import time
import random
import os

# Silence webdriver_manager logs
os.environ['WDM_LOG'] = '0'

from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from webdriver_manager.chrome import ChromeDriverManager

def get_taobao_products(keyword):
    # Setup Chrome options
    chrome_options = Options()
    chrome_options.add_argument("--headless")  # Run in headless mode
    chrome_options.add_argument("--disable-gpu")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    # Anti-detection
    chrome_options.add_argument("--disable-blink-features=AutomationControlled")
    chrome_options.add_experimental_option("excludeSwitches", ["enable-automation"])
    chrome_options.add_experimental_option('useAutomationExtension', False)
    chrome_options.add_argument("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")

    service = None
    if os.path.exists("/usr/bin/chromedriver"):
        service = Service("/usr/bin/chromedriver")
    else:
        service = Service(ChromeDriverManager().install())

    driver = webdriver.Chrome(service=service, options=chrome_options)
    
    # Bypass selenium detection
    driver.execute_cdp_cmd("Page.addScriptToEvaluateOnNewDocument", {
        "source": """
            Object.defineProperty(navigator, 'webdriver', {
                get: () => undefined
            })
        """
    })

    results = []
    try:
        search_url = f"https://s.taobao.com/search?q={keyword}"
        driver.get(search_url)
        
        # Wait for items to load (wait for price element as indicator)
        # Note: Taobao's class names are dynamic or obfuscated, often using 'priceWrapper', 'price', etc.
        # It's better to look for common structures. 
        # In recent Taobao versions, classes are like 'Card--doubleCardWrapper--...', 'Price--priceWrapper--...'
        # We'll try a generic wait and then flexible xpath
        time.sleep(random.uniform(2, 4)) 

        # Scroll down to load more items
        driver.execute_script("window.scrollTo(0, document.body.scrollHeight/2);")
        time.sleep(1)
        driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
        time.sleep(2)

        # Extract items
        # Strategy: Find elements that look like product cards
        # Trying a broad strategy suitable for recent Taobao layouts
        
        # 2024/2025 Taobao layout often uses div with class starting with 'Card--' or containing 'item'
        # Let's try to find elements that contain price and title
        
        # This XPath looks for elements that have a price-like structure
        items = driver.find_elements(By.XPATH, "//div[contains(@class, 'Content--content--')]//div[contains(@class, 'Card--doubleCardWrapper--')] | //div[contains(@class, 'item-service')] | //div[contains(@class, 'ctx-box')]")
        
        if not items:
             # Fallback for other layouts
             items = driver.find_elements(By.CSS_SELECTOR, ".item.J_MouserOnverReq")

        if not items:
            # Fallback 2: Generic search for anything with a price
            items = driver.find_elements(By.XPATH, "//div[contains(@class, 'Card--')]")

        count = 0
        for item in items:
            if count >= 10: break # Limit to 10 items for speed
            try:
                # Title
                try:
                    title_el = item.find_element(By.XPATH, ".//div[contains(@class, 'Title--title--')]//span")
                    title = title_el.text
                except:
                    continue # Skip if no title

                # Price
                try:
                    price_int = item.find_element(By.XPATH, ".//span[contains(@class, 'Price--priceInt--')]").text
                    price_float = item.find_element(By.XPATH, ".//span[contains(@class, 'Price--priceFloat--')]").text
                    price = f"{price_int}{price_float}"
                except:
                    try:
                        price = item.find_element(By.XPATH, ".//div[contains(@class, 'price')]").text
                    except:
                        price = "0.00"

                # Link
                try:
                    link_el = item.find_element(By.XPATH, ".//a[contains(@class, 'Card--doubleCardWrapper--')]")
                    link = link_el.get_attribute("href")
                    if link and not link.startswith("http"):
                        link = "https:" + link
                except:
                    link = ""

                # Image
                try:
                    img_el = item.find_element(By.XPATH, ".//img[contains(@class, 'MainPic--mainPic--')]")
                    image = img_el.get_attribute("src")
                    if image and not image.startswith("http"):
                        image = "https:" + image
                except:
                    image = ""

                # Shop
                try:
                    shop_el = item.find_element(By.XPATH, ".//a[contains(@class, 'ShopInfo--shopName--')]")
                    shop = shop_el.text
                except:
                    shop = "Taobao Shop"

                if title and price:
                    results.append({
                        "platform": "Taobao",
                        "title": title,
                        "price": price,
                        "url": link,
                        "imageUrl": image,
                        "shopName": shop
                    })
                    count += 1
            except Exception as e:
                # print(f"Error parsing item: {e}", file=sys.stderr)
                continue

    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
    finally:
        driver.quit()

    return results

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python taobao_spider.py <keyword>")
        sys.exit(1)
    
    keyword = sys.argv[1]
    data = get_taobao_products(keyword)
    print(json.dumps(data, ensure_ascii=False))
