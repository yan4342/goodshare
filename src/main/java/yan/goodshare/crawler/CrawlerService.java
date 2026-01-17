package yan.goodshare.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import yan.goodshare.dto.ProductPriceDTO;
import yan.goodshare.entity.Product;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
public class CrawlerService {

    public Product crawlProductInfo(String url) throws IOException {
        // Example implementation for crawling a generic product page.
        // This will need to be adapted for specific websites like Taobao.
        Document doc = Jsoup.connect(url).get();

        String name = doc.select("meta[property=og:title]").attr("content");
        if (name.isEmpty()) {
            name = doc.title();
        }

        String description = doc.select("meta[property=og:description]").attr("content");
        if (description.isEmpty()) {
            description = doc.select("meta[name=description]").attr("content");
        }

        String imageUrl = doc.select("meta[property=og:image]").attr("content");

        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setImageUrl(imageUrl);

        return product;
    }

    public List<ProductPriceDTO> searchProducts(String keyword) {
        List<ProductPriceDTO> results = new ArrayList<>();
        
        // 1. Crawl JD
        try {
            System.out.println("Starting JD crawl for: " + keyword);
            List<ProductPriceDTO> jdResults = crawlJD(keyword);
            System.out.println("JD results count: " + jdResults.size());
            results.addAll(jdResults);
        } catch (Exception e) {
            System.err.println("JD Crawl failed: " + e.getMessage());
            e.printStackTrace();
        }

        // 2. Crawl Taobao (Best Effort)
        try {
            System.out.println("Starting Taobao crawl for: " + keyword);
            List<ProductPriceDTO> taobaoResults = crawlTaobao(keyword);
            System.out.println("Taobao results count: " + taobaoResults.size());
            results.addAll(taobaoResults);
        } catch (Exception e) {
            System.err.println("Taobao Crawl failed: " + e.getMessage());
        }

        // 3. Crawl Pinduoduo (Best Effort)
        try {
            System.out.println("Starting Pinduoduo crawl for: " + keyword);
            List<ProductPriceDTO> pddResults = crawlPinduoduo(keyword);
            System.out.println("Pinduoduo results count: " + pddResults.size());
            results.addAll(pddResults);
        } catch (Exception e) {
            System.err.println("Pinduoduo Crawl failed: " + e.getMessage());
        }

        // 4. Fallback to Mock Data if no results found
        if (results.isEmpty()) {
            System.out.println("No real results found, using mock data.");
            results.addAll(getMockData(keyword));
        }
        
        // Sort by price ascending
        Collections.sort(results, (p1, p2) -> {
            if (p1.getPrice() == null && p2.getPrice() == null) return 0;
            if (p1.getPrice() == null) return 1;
            if (p2.getPrice() == null) return -1;
            return p1.getPrice().compareTo(p2.getPrice());
        });

        return results;
    }

    private List<ProductPriceDTO> crawlPinduoduo(String keyword) throws IOException {
        List<ProductPriceDTO> list = new ArrayList<>();
        // Pinduoduo Mobile Search URL
        String url = "http://mobile.yangkeduo.com/search_result.html?search_key=" + keyword;
        
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                .timeout(5000)
                .get();

        // Pinduoduo is heavily JS rendered. Jsoup usually gets a skeleton.
        // We try to find any embedded data or basic list items if SSR is active.
        // Note: This is highly likely to fail or return empty without a headless browser.
        // We look for common container classes or JSON data in scripts.
        
        // Attempt to find script containing window.rawData or similar
        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String html = script.html();
            if (html.contains("goods_name")) {
                 // Very complex parsing would be needed here, simplified for this context
                 // If we find raw data, we might be able to regex it.
                 // For now, we assume if we can't find standard elements, we return empty.
            }
        }
        
        return list;
    }

    private List<ProductPriceDTO> crawlJD(String keyword) throws IOException {
        List<ProductPriceDTO> list = new ArrayList<>();
        String url = "https://search.jd.com/search?keyword=" + keyword + "&wq=" + keyword + "&enc=utf-8";
        
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .timeout(10000)
                .get();

        // User suggested selector: //*[@id="J_goodsList"]/ul/li
        Elements items = doc.select("#J_goodsList ul li");
        
        if (items.isEmpty()) {
             items = doc.select(".gl-item");
        }

        for (Element item : items) {
            try {
                // Title
                // User suggested: .//div[@class="p-name p-name-type-2"]/a/em/text()
                String name = item.select(".p-name a em").text();
                if (name.isEmpty()) {
                    name = item.select(".p-name em").text();
                }

                // Price
                // User suggested: .//div[@class="p-price"]/strong/i/text()
                String priceStr = item.select(".p-price strong i").text();
                
                // Image
                String imgUrl = item.select(".p-img img").attr("data-lazy-img");
                if (imgUrl.isEmpty()) {
                    imgUrl = item.select(".p-img img").attr("src");
                }
                if (!imgUrl.startsWith("http") && !imgUrl.isEmpty()) {
                    imgUrl = "https:" + imgUrl;
                }
                
                // Link
                // User suggested extracting ID from: .//div[@class="p-commit"]/strong/a/@id
                // id format: "J_comment_{skuId}"
                String link = item.select(".p-img a").attr("href");
                if (link.isEmpty()) {
                    String commentId = item.select(".p-commit strong a").attr("id");
                    if (commentId != null && commentId.startsWith("J_comment_")) {
                        String skuId = commentId.replace("J_comment_", "");
                        link = "https://item.jd.com/" + skuId + ".html";
                    }
                }
                
                if (!link.startsWith("http") && !link.isEmpty()) {
                    link = "https:" + link;
                }
                
                String shopName = item.select(".p-shop a").text();

                if (!name.isEmpty() && !priceStr.isEmpty()) {
                    list.add(new ProductPriceDTO(
                            "JD",
                            name,
                            new BigDecimal(priceStr),
                            imgUrl,
                            link,
                            shopName
                    ));
                }
            } catch (Exception e) {
                // Ignore single item parse error
            }
            if (list.size() >= 5) break; // Limit to 5 items
        }
        return list;
    }

    private List<ProductPriceDTO> crawlTaobao(String keyword) throws IOException {
        List<ProductPriceDTO> list = new ArrayList<>();
        String url = "https://s.taobao.com/search?q=" + keyword;
        
        // Taobao is very strict. This simple request often redirects to login.
        // In a real production environment, you would need Selenium or Puppeteer.
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://www.taobao.com/")
                .timeout(5000)
                .get();

        // Taobao structure changes frequently and is rendered by JS. 
        // Jsoup sees the initial HTML which might be empty or a login page.
        // We attempt to find typical elements but expect failure without browser automation.
        
        // Example selector (might be outdated due to dynamic rendering):
        var items = doc.select(".item.J_MouserOnverReq"); 
        
        for (var item : items) {
            try {
                String name = item.select(".title a").text().trim();
                String priceStr = item.select(".price strong").text();
                String imgUrl = item.select(".pic img").attr("data-src");
                if (imgUrl.isEmpty()) imgUrl = item.select(".pic img").attr("src");
                if (!imgUrl.startsWith("http")) imgUrl = "https:" + imgUrl;
                
                String link = item.select(".pic a").attr("href");
                if (!link.startsWith("http")) link = "https:" + link;
                
                String shopName = item.select(".shop a").text().trim();

                if (!name.isEmpty() && !priceStr.isEmpty()) {
                    list.add(new ProductPriceDTO(
                            "Taobao",
                            name,
                            new BigDecimal(priceStr),
                            imgUrl,
                            link,
                            shopName
                    ));
                }
            } catch (Exception e) {
                // Ignore
            }
            if (list.size() >= 5) break;
        }
        return list;
    }

    private List<ProductPriceDTO> getMockData(String keyword) {
        List<ProductPriceDTO> results = new ArrayList<>();
        Random random = new Random();

        // Mock Taobao Data
        for (int i = 0; i < 3; i++) {
            BigDecimal price = BigDecimal.valueOf(random.nextDouble() * 1000 + 50).setScale(2, BigDecimal.ROUND_HALF_UP);
            results.add(new ProductPriceDTO(
                    "Taobao (Mock)",
                    "Taobao: " + keyword + " Premium Selection " + (i + 1),
                    price,
                    "https://placehold.co/200x200/ff5000/ffffff?text=Taobao",
                    "https://www.taobao.com",
                    "Taobao Official Shop " + (i + 1)
            ));
        }

        // Mock JD Data
        for (int i = 0; i < 3; i++) {
            BigDecimal price = BigDecimal.valueOf(random.nextDouble() * 1000 + 50).setScale(2, BigDecimal.ROUND_HALF_UP);
            results.add(new ProductPriceDTO(
                    "JD (Mock)",
                    "JD: " + keyword + " Fast Delivery " + (i + 1),
                    price,
                    "https://placehold.co/200x200/e1251b/ffffff?text=JD",
                    "https://www.jd.com",
                    "JD Self-operated " + (i + 1)
            ));
        }

        // Mock Pinduoduo Data
        for (int i = 0; i < 3; i++) {
            BigDecimal price = BigDecimal.valueOf(random.nextDouble() * 800 + 20).setScale(2, BigDecimal.ROUND_HALF_UP);
            results.add(new ProductPriceDTO(
                    "Pinduoduo (Mock)",
                    "PDD: " + keyword + " Group Buy " + (i + 1),
                    price,
                    "https://placehold.co/200x200/e02e24/ffffff?text=PDD",
                    "https://www.pinduoduo.com",
                    "PDD Flagship " + (i + 1)
            ));
        }
        
        return results;
    }
}
