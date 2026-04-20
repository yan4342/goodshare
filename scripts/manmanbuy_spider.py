import re
import json
import sys
import io
import time
import random
import urllib.parse
import os
from playwright.sync_api import sync_playwright, TimeoutError as PlaywrightTimeoutError

# Unset proxy to avoid ERR_PROXY_CONNECTION_FAILED in Docker
if 'http_proxy' in os.environ: del os.environ['http_proxy']
if 'https_proxy' in os.environ: del os.environ['https_proxy']
if 'HTTP_PROXY' in os.environ: del os.environ['HTTP_PROXY']
if 'HTTPS_PROXY' in os.environ: del os.environ['HTTPS_PROXY']

# Force UTF-8 output for Windows console/pipe compatibility
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

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
        # Check for direct src key
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

def find_link_url(node):
    if isinstance(node, dict):
        # Check for href prop directly in props
        if 'props' in node:
            href = node['props'].get('href')
            if href and isinstance(href, str):
                return href
        # Check for direct href key
        if 'href' in node and isinstance(node['href'], str):
            return node['href']
            
        # Recursive search
        for key, value in node.items():
            res = find_link_url(value)
            if res: return res
    elif isinstance(node, list):
        # ["$", type, key, props]
        if len(node) > 3 and isinstance(node[3], dict):
             href = node[3].get('href')
             if href and isinstance(href, str):
                return href
        
        for item in node:
            res = find_link_url(item)
            if res: return res
    return ""

def parse_nextjs_data(next_f_data):
    products = []
    
    for item in next_f_data:
        if not isinstance(item, list) or len(item) < 2: continue
        
        data_str = item[1]
        if not isinstance(data_str, str): continue
        
        try:
            if ':' not in data_str: continue
            id_part, json_str = data_str.split(':', 1)
            
            if not (json_str.startswith('[') or json_str.startswith('{')):
                continue
                
            data = json.loads(json_str)
            
            boxes = []
            find_nodes_by_class(data, "DiscountItemPC_box", boxes)
            
            for box in boxes:
                try:
                    title_nodes = []
                    find_nodes_by_class(box, "DiscountItemPC_itemTitle", title_nodes)
                    title = extract_text_from_node(title_nodes[0]) if title_nodes else ""
                    
                    price_nodes = []
                    find_nodes_by_class(box, "DiscountItemPC_itemSubTitle", price_nodes)
                    price_text = extract_text_from_node(price_nodes[0]) if price_nodes else ""
                    price_match = re.search(r'(\d+(\.\d{1,2})?)', price_text)
                    price = price_match.group(1) if price_match else "0"
                    
                    img_nodes = []
                    find_nodes_by_class(box, "DiscountItemPC_itemCover", img_nodes)
                    img = ""
                    if img_nodes:
                        img = find_image_url(img_nodes[0])
                    
                    link_nodes = []
                    find_nodes_by_class(box, "DiscountItemPC_itemGoBuy", link_nodes)
                    link = ""
                    if link_nodes:
                         link = find_link_url(link_nodes[0])
                    
                    if not link:
                        if title_nodes:
                            link = find_link_url(title_nodes[0])
                        if not link and img_nodes:
                            link = find_link_url(img_nodes[0])
                    
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
                except Exception:
                    pass
                    
        except Exception:
            pass
            
    return products

def get_manmanbuy_products(keyword, limit=0):
    start_time = time.time()
    results = []
    
    try:
        with sync_playwright() as p:
            # Setup browser launch arguments
            launch_args = {
                "headless": True,
                "args": [
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-blink-features=AutomationControlled",
                ]
            }
            
            # Explicitly set binary location for Alpine/Docker if present
            if os.path.exists("/usr/bin/chromium-browser"):
                launch_args["executable_path"] = "/usr/bin/chromium-browser"
            elif os.path.exists("/usr/bin/chromium"):
                launch_args["executable_path"] = "/usr/bin/chromium"

            browser = None
            try:
                # Priority 1: Default or System Chromium
                browser = p.chromium.launch(**launch_args)
            except Exception as e:
                # print(f"Chromium launch failed: {e}", file=sys.stderr)
                # Fallback to Edge if Chromium failed
                if "executable_path" in launch_args:
                    del launch_args["executable_path"]
                launch_args["channel"] = "msedge"
                try:
                    browser = p.chromium.launch(**launch_args)
                except Exception as e:
                    # print(f"Edge fallback failed: {e}", file=sys.stderr)
                    print("[]")
                    return

            context = browser.new_context(
                user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                viewport={"width": 1920, "height": 1080}
            )

            # Anti-detection: Hide webdriver
            context.add_init_script("""
                Object.defineProperty(navigator, 'webdriver', {
                    get: () => undefined
                })
            """)

            page = context.new_page()
            
            # Optimization: Block unnecessary resources but allow scripts and fetches
            page.route("**/*", lambda route: route.continue_() if route.request.resource_type not in ["image", "media", "font"] else route.abort())

            try:
                encoded_keyword = urllib.parse.quote(keyword)
                search_url = f"https://s.manmanbuy.com/pc/search/result?c=discount&keyword={encoded_keyword}"
                
                # Navigate to page and wait for DOM
                # Instead of waiting for domcontentloaded which might hang if some script is blocked,
                # we wait for commit and let the script find elements dynamically.
                page.goto(search_url, wait_until="commit", timeout=15000)
                
                # Wait up to 5s for the Next.js hydration data to appear
                try:
                    page.wait_for_function("() => window.self.__next_f !== undefined && window.self.__next_f.length > 0", timeout=5000)
                except PlaywrightTimeoutError:
                    pass

                # Strategy 1: Try extracting from Next.js hydration data
                try:
                    next_f = page.evaluate("window.self.__next_f")
                    if next_f:
                        results = parse_nextjs_data(next_f)
                except Exception:
                    pass

                # Strategy 2: Fallback to DOM crawling if Strategy 1 failed or returned empty
                if not results:
                    try:
                        # Wait for items to be present
                        page.wait_for_selector('div[class*="DiscountItemPC_box"]', timeout=5000)
                        items = page.query_selector_all('div[class*="DiscountItemPC_box"]')
                        for item in items:
                            try:
                                title_el = item.query_selector('div[class*="DiscountItemPC_itemTitle"]')
                                title = title_el.inner_text().strip() if title_el else ""
                                
                                price_el = item.query_selector('div[class*="DiscountItemPC_itemSubTitle"]')
                                price_text = price_el.inner_text().strip() if price_el else ""
                                price_match = re.search(r'(\d+(\.\d{1,2})?)', price_text)
                                price = price_match.group(1) if price_match else "0"
                                
                                img_el = item.query_selector('div[class*="DiscountItemPC_itemCover"] img')
                                img = img_el.get_attribute("src") if img_el else ""
                                
                                link_el = item.query_selector('div[class*="DiscountItemPC_itemGoBuy"] a')
                                link = link_el.get_attribute("href") if link_el else ""
                                
                                shop_el = item.query_selector('div[class*="DiscountItemPC_itemMall"]')
                                shop_text = shop_el.inner_text().strip() if shop_el else ""
                                shop = shop_text if shop_text else "Manmanbuy"
                                
                                if title:
                                    results.append({
                                        "shop": fix_encoding(shop),
                                        "title": fix_encoding(title),
                                        "price": price,
                                        "image": img,
                                        "link": link,
                                        "deal": fix_encoding(shop)
                                    })
                            except Exception:
                                continue
                    except PlaywrightTimeoutError:
                        pass
            except Exception as e:
                # print(f"Error during crawl: {e}", file=sys.stderr)
                pass
            finally:
                browser.close()
    except Exception as e:
        # print(f"Playwright initialization error: {e}", file=sys.stderr)
        pass

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