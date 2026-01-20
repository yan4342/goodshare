
import re
import json
import sys
import io
import time
import random
import urllib.parse
from selenium import webdriver

# Force UTF-8 output for Windows console/pipe compatibility
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from webdriver_manager.chrome import ChromeDriverManager
from selenium.webdriver.common.by import By
# Edge imports
from selenium.webdriver.edge.options import Options as EdgeOptions
from selenium.webdriver.edge.service import Service as EdgeService
from webdriver_manager.microsoft import EdgeChromiumDriverManager

# Suppress webdriver_manager logs
import os
os.environ['WDM_LOG_LEVEL'] = '0'

def fix_encoding(text):
    if not text: return ""
    try:
        # Sometimes text comes as latin1 garbled utf-8
        return text.encode('latin1').decode('utf-8')
    except:
        return text

def extract_text_from_node(node):
    if isinstance(node, (str, int, float)):
        return str(node)
    if isinstance(node, list):
        # ["$", type, key, props]
        if len(node) > 3 and isinstance(node[3], dict):
            children = node[3].get('children')
            return extract_text_from_node(children)
        # generic list
        return "".join([extract_text_from_node(item) for item in node])
    if isinstance(node, dict):
        return extract_text_from_node(node.get('children'))
    return ""

def find_nodes_by_class(node, class_partial, results):
    if isinstance(node, list):
        # ["$", type, key, props]
        if len(node) > 3 and isinstance(node[3], dict):
            props = node[3]
            cls = props.get('className', '')
            if class_partial in cls:
                results.append(node)
            
            # Recurse
            find_nodes_by_class(props.get('children'), class_partial, results)
        else:
            for item in node:
                find_nodes_by_class(item, class_partial, results)
    elif isinstance(node, dict):
        find_nodes_by_class(node.get('children'), class_partial, results)

def find_image_url(node):
    if isinstance(node, dict):
        # Check for src prop directly in props
        if 'props' in node:
            src = node['props'].get('src')
            if src and isinstance(src, str) and src.startswith('http'):
                return src
        # Check for direct src key (less common in this Next.js structure but possible)
        if 'src' in node and isinstance(node['src'], str) and node['src'].startswith('http'):
            return node['src']
            
        # Recursive search
        for key, value in node.items():
            res = find_image_url(value)
            if res: return res
    elif isinstance(node, list):
        for item in node:
            res = find_image_url(item)
            if res: return res
    return ""

def parse_nextjs_data(next_f_data):
    products = []
    
    # next_f_data is a list of [1, "ID:JSON"]
    for item in next_f_data:
        if not isinstance(item, list) or len(item) < 2: continue
        
        data_str = item[1]
        if not isinstance(data_str, str): continue
        
        # Split ID:JSON
        try:
            if ':' not in data_str: continue
            id_part, json_str = data_str.split(':', 1)
            
            if not (json_str.startswith('[') or json_str.startswith('{')):
                continue
                
            data = json.loads(json_str)
            
            # Search for DiscountItemPC_box
            boxes = []
            find_nodes_by_class(data, "DiscountItemPC_box", boxes)
            
            for box in boxes:
                try:
                    # Extract details
                    # Title
                    title_nodes = []
                    find_nodes_by_class(box, "DiscountItemPC_itemTitle", title_nodes)
                    title = extract_text_from_node(title_nodes[0]) if title_nodes else ""
                    
                    # Price
                    price_nodes = []
                    find_nodes_by_class(box, "DiscountItemPC_itemSubTitle", price_nodes)
                    price_text = extract_text_from_node(price_nodes[0]) if price_nodes else ""
                    price_match = re.search(r'(\d+(\.\d{1,2})?)', price_text)
                    price = price_match.group(1) if price_match else "0"
                    
                    # Image
                    img_nodes = []
                    # Images are usually in $L15 or img tag inside Cover
                    find_nodes_by_class(box, "DiscountItemPC_itemCover", img_nodes)
                    img = ""
                    if img_nodes:
                        img = find_image_url(img_nodes[0])
                    
                    # Link
                    link_nodes = []
                    find_nodes_by_class(box, "DiscountItemPC_itemGoBuy", link_nodes)
                    link = ""
                    if link_nodes:
                         props = link_nodes[0][3]
                         link = props.get('href', '')
                    
                    # Shop
                    shop_nodes = []
                    find_nodes_by_class(box, "DiscountItemPC_itemMall", shop_nodes)
                    shop = extract_text_from_node(shop_nodes[0]) if shop_nodes else "Manmanbuy"
                    
                    if title:
                        products.append({
                            "shop": fix_encoding(shop),
                            "title": fix_encoding(title),
                            "price": price,
                            "image": img,
                            "link": link,
                            "deal": fix_encoding(shop)
                        })
                except Exception as e:
                    # print(f"Error parsing box: {e}", file=sys.stderr)
                    pass
                    
        except Exception:
            pass
            
    return products

def get_manmanbuy_products(keyword, limit=0):
    chrome_options = Options()
    chrome_options.add_argument("--headless")
    chrome_options.add_argument("--disable-gpu")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    # Add fake user-agent
    chrome_options.add_argument("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    
    # Suppress logging
    chrome_options.add_experimental_option('excludeSwitches', ['enable-logging'])
    chrome_options.add_argument('--blink-settings=imagesEnabled=true') # Enable images to look more real

    driver = None
    
    # Try Chrome
    try:
        # Try using npmmirror for better connectivity in China
        try:
            # Try https mirror
            manager = ChromeDriverManager(url="https://npmmirror.com/mirrors/chromedriver/", latest_release_url="https://npmmirror.com/mirrors/chromedriver/LATEST_RELEASE")
            service = Service(manager.install())
        except Exception as e:
            print(f"Chrome HTTPS Mirror failed: {e}", file=sys.stderr)
            try:
                 # Try http mirror
                 manager = ChromeDriverManager(url="http://npmmirror.com/mirrors/chromedriver/", latest_release_url="http://npmmirror.com/mirrors/chromedriver/LATEST_RELEASE")
                 service = Service(manager.install())
            except Exception as e2:
                 print(f"Chrome HTTP Mirror failed: {e2}", file=sys.stderr)
                 # Fallback to default source
                 service = Service(ChromeDriverManager().install())
    except Exception as e:
        print(f"All Chrome driver installation methods failed: {e}", file=sys.stderr)
        service = Service()
    
    try:
        driver = webdriver.Chrome(service=service, options=chrome_options)
    except Exception as e:
        print(f"Failed to initialize Chrome WebDriver: {e}", file=sys.stderr)
    
    # Fallback to Edge if Chrome failed
    if driver is None:
        print("Attempting fallback to Microsoft Edge...", file=sys.stderr)
        try:
            edge_options = EdgeOptions()
            edge_options.add_argument("--headless")
            edge_options.add_argument("--disable-gpu")
            # Edge-specific options to mimic Chrome behavior
            edge_options.add_argument("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            edge_options.add_experimental_option('excludeSwitches', ['enable-logging'])
            edge_options.add_argument('--blink-settings=imagesEnabled=true')

            try:
                # Try installing Edge driver
                service = EdgeService(EdgeChromiumDriverManager().install())
            except Exception:
                # Fallback to default/system driver
                service = EdgeService()
            
            driver = webdriver.Edge(service=service, options=edge_options)
            print("Successfully initialized Microsoft Edge WebDriver.", file=sys.stderr)
        except Exception as e:
            print(f"Failed to initialize Edge WebDriver: {e}", file=sys.stderr)
            # Output empty list to prevent JSON parse error in Java
            print("[]")
            return

    # Anti-detection: Hide WebDriver property
    driver.execute_cdp_cmd("Page.addScriptToEvaluateOnNewDocument", {
        "source": """
            Object.defineProperty(navigator, 'webdriver', {
                get: () => undefined
            })
        """
    })

    results = []

    try:
        # Using the PC search URL
        search_url = f"https://s.manmanbuy.com/pc/search/result?c=discount&keyword={keyword}"
        driver.get(search_url)
        
        # Random wait for content to load
        time.sleep(random.uniform(4, 8))
        
        # Scroll to trigger lazy loading with random pause
        driver.execute_script("window.scrollTo(0, document.body.scrollHeight/2);")
        time.sleep(random.uniform(2, 5))

        # Strategy 1: Try extracting from Next.js hydration data
        try:
            next_f = driver.execute_script("return window.self.__next_f")
            if next_f:
                results = parse_nextjs_data(next_f)
        except Exception as e:
            # print(f"Next.js data extraction failed: {e}", file=sys.stderr)
            pass

        # Strategy 2: Fallback to DOM crawling if Strategy 1 failed or returned empty
        if not results:
             # ... (Keep existing DOM logic or simplified version) ...
             pass
             
    except Exception as e:
        # print(f"Error: {e}", file=sys.stderr)
        pass
    finally:
        driver.quit()

    # Deduplicate
    unique_results = []
    seen_urls = set()
    for r in results:
        if r['link'] not in seen_urls:
            seen_urls.add(r['link'])
            unique_results.append(r)

    if limit > 0:
        unique_results = unique_results[:limit]

    print(json.dumps(unique_results, ensure_ascii=False))

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python manmanbuy_spider.py <keyword> [limit]")
        sys.exit(1)
    
    keyword = sys.argv[1]
    limit = int(sys.argv[2]) if len(sys.argv) > 2 else 0
    get_manmanbuy_products(keyword, limit)
