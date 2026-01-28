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

    // Redis cache key prefix
    private static final String REDIS_KEY_PREFIX = "crawler:product:";
    private static final String HISTORY_KEY_PREFIX = "crawler:history:";
    private static final long CACHE_DURATION_HOURS = 24;

    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    public CrawlerService(org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<ProductPriceDTO> searchProducts(String keyword) {
        String cacheKey = REDIS_KEY_PREFIX + keyword;
        
        // Check Redis Cache
        try {
            Object cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                System.out.println("Returning cached results from Redis for: " + keyword);
                // Depending on how Redis deserializes, this might be a List or LinkedHashMap
                // For simplicity assuming Jackson handles it correctly with the config we added
                return (List<ProductPriceDTO>) cachedData;
            }
        } catch (Exception e) {
            System.err.println("Redis cache error: " + e.getMessage());
        }

        List<ProductPriceDTO> results = new ArrayList<>();
        
        // 1. Crawl JD
        // try {
        //     System.out.println("Starting JD crawl for: " + keyword);
        //     List<ProductPriceDTO> jdResults = crawlJD(keyword);
        //     System.out.println("JD results count: " + jdResults.size());
        //     results.addAll(jdResults);
        // } catch (Exception e) {
        //     System.err.println("JD Crawl failed: " + e.getMessage());
        //     e.printStackTrace();
        // }

        // 2. Crawl Taobao (Using Python Selenium)
        // try {
        //     System.out.println("Starting Taobao crawl for: " + keyword);
        //     List<ProductPriceDTO> taobaoResults = crawlTaobao(keyword);
        //     System.out.println("Taobao results count: " + taobaoResults.size());
        //     results.addAll(taobaoResults);
        // } catch (Exception e) {
        //     System.err.println("Taobao Crawl failed: " + e.getMessage());
        // }

        // 3. Crawl Pinduoduo (Best Effort)
        // try {
        //     System.out.println("Starting Pinduoduo crawl for: " + keyword);
        //     List<ProductPriceDTO> pddResults = crawlPinduoduo(keyword);
        //     System.out.println("Pinduoduo results count: " + pddResults.size());
        //     results.addAll(pddResults);
        // } catch (Exception e) {
        //     System.err.println("Pinduoduo Crawl failed: " + e.getMessage());
        // }

        // 4. Crawl Manmanbuy (Aggregation Source)
        try {
            System.out.println("Starting Manmanbuy crawl for: " + keyword);
            List<ProductPriceDTO> mmbResults = crawlManmanbuy(keyword);
            System.out.println("Manmanbuy results count: " + mmbResults.size());
            results.addAll(mmbResults);
        } catch (Exception e) {
            System.err.println("Manmanbuy Crawl failed: " + e.getMessage());
        }

        // 5. Fallback to Mock Data if no results found
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

        // Update Cache
        if (!results.isEmpty()) {
            try {
                redisTemplate.opsForValue().set(cacheKey, results, java.time.Duration.ofHours(CACHE_DURATION_HOURS));
                // Record history
                recordHistory(keyword, results);
            } catch (Exception e) {
                System.err.println("Failed to cache results in Redis: " + e.getMessage());
            }
        }

        return results;
    }

    private void recordHistory(String keyword, List<ProductPriceDTO> results) {
        if (results == null || results.isEmpty()) return;

        // Calculate stats
        BigDecimal minPrice = null;
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;

        for (ProductPriceDTO dto : results) {
            if (dto.getPrice() != null && dto.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                if (minPrice == null || dto.getPrice().compareTo(minPrice) < 0) {
                    minPrice = dto.getPrice();
                }
                total = total.add(dto.getPrice());
                count++;
            }
        }

        if (count > 0 && minPrice != null) {
            BigDecimal avg = total.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP);
            String today = java.time.LocalDate.now().toString();
            String key = HISTORY_KEY_PREFIX + keyword;

            yan.goodshare.dto.ProductHistoryDTO historyDTO = new yan.goodshare.dto.ProductHistoryDTO(today, minPrice, avg);

            // Check if today's record already exists to avoid duplicates
            // Get last element
            Object lastObj = redisTemplate.opsForList().index(key, -1);
            if (lastObj instanceof yan.goodshare.dto.ProductHistoryDTO) {
                yan.goodshare.dto.ProductHistoryDTO last = (yan.goodshare.dto.ProductHistoryDTO) lastObj;
                if (last.getDate().equals(today)) {
                    // Update today's record (pop and push)
                    redisTemplate.opsForList().rightPop(key);
                }
            }
            
            redisTemplate.opsForList().rightPush(key, historyDTO);
        }
    }

    public List<yan.goodshare.dto.ProductHistoryDTO> getProductHistory(String keyword) {
        String key = HISTORY_KEY_PREFIX + keyword;
        try {
            List<Object> list = redisTemplate.opsForList().range(key, 0, -1);
            if (list == null) return new ArrayList<>();
            
            List<yan.goodshare.dto.ProductHistoryDTO> history = new ArrayList<>();
            for (Object obj : list) {
                if (obj instanceof yan.goodshare.dto.ProductHistoryDTO) {
                    history.add((yan.goodshare.dto.ProductHistoryDTO) obj);
                } else if (obj instanceof java.util.LinkedHashMap) {
                     // Handle Jackson deserialization to LinkedHashMap if generic type info is lost
                     // This often happens with RedisTemplate<String, Object>
                     try {
                         com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                         yan.goodshare.dto.ProductHistoryDTO dto = mapper.convertValue(obj, yan.goodshare.dto.ProductHistoryDTO.class);
                         history.add(dto);
                     } catch (Exception e) {
                         System.err.println("Failed to convert history object: " + e.getMessage());
                     }
                }
            }
            
            // If empty, generate some mock history for demo purposes (so the user sees the chart)
            if (history.isEmpty()) {
                 return generateMockHistory();
            }
            
            return history;
        } catch (Exception e) {
            System.err.println("Failed to get history: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<yan.goodshare.dto.ProductHistoryDTO> generateMockHistory() {
        List<yan.goodshare.dto.ProductHistoryDTO> mock = new ArrayList<>();
        java.time.LocalDate date = java.time.LocalDate.now().minusDays(6);
        Random random = new Random();
        double basePrice = 100 + random.nextDouble() * 900;
        
        for (int i = 0; i < 7; i++) {
            BigDecimal price = BigDecimal.valueOf(basePrice + random.nextDouble() * 50 - 25).setScale(2, java.math.RoundingMode.HALF_UP);
            mock.add(new yan.goodshare.dto.ProductHistoryDTO(
                date.plusDays(i).toString(),
                price,
                price.add(BigDecimal.valueOf(20))
            ));
        }
        return mock;
    }

    private List<ProductPriceDTO> crawlManmanbuy(String keyword) {
        List<ProductPriceDTO> list = new ArrayList<>();
        try {
            // Locate the Python script
            String projectDir = System.getProperty("user.dir");
            String scriptPath = projectDir + "/scripts/manmanbuy_spider.py";
            
            // Build command: python script.py keyword limit
            // Limit to 20 items to prevent anti-scraping blocks
            ProcessBuilder processBuilder = new ProcessBuilder("python", scriptPath, keyword, "8");
            processBuilder.redirectErrorStream(true); // Merge stderr to stdout for debugging
            
            Process process = processBuilder.start();
            
            // Read output
            java.io.InputStream inputStream = process.getInputStream();
            java.util.Scanner s = new java.util.Scanner(inputStream, "UTF-8").useDelimiter("\\A");
            String output = s.hasNext() ? s.next() : "";
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("Manmanbuy Python script exited with code " + exitCode);
                System.err.println("Output: " + output);
                return list;
            }

            // Parse JSON output
            int jsonStart = output.indexOf("[");
            int jsonEnd = output.lastIndexOf("]");
            
            if (jsonStart != -1 && jsonEnd != -1) {
                String jsonStr = output.substring(jsonStart, jsonEnd + 1);
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<java.util.Map<String, String>> items = mapper.readValue(jsonStr, new com.fasterxml.jackson.core.type.TypeReference<List<java.util.Map<String, String>>>(){});
                
                for (java.util.Map<String, String> item : items) {
                    ProductPriceDTO dto = new ProductPriceDTO();
                    dto.setPlatform(item.get("shop")); // Use shop as platform for aggregator
                    dto.setName(item.get("title"));
                    try {
                        String priceStr = item.get("price").replace("¥", "").replace("￥", "").trim();
                        dto.setPrice(new BigDecimal(priceStr));
                    } catch (Exception e) {
                        dto.setPrice(BigDecimal.ZERO);
                    }
                    dto.setProductUrl(item.get("link"));
                    dto.setImageUrl(item.get("image"));
                    dto.setShopName(item.get("shop"));
                    list.add(dto);
                }
            } else {
                System.err.println("No JSON found in Manmanbuy Python output: " + output);
            }
            
        } catch (Exception e) {
            System.err.println("Failed to run Manmanbuy crawler: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    private List<ProductPriceDTO> crawlTaobao(String keyword) {
        List<ProductPriceDTO> list = new ArrayList<>();
        try {
            // Locate the Python script
            String projectDir = System.getProperty("user.dir");
            String scriptPath = projectDir + "/scripts/taobao_spider.py";
            
            // Build command: python script.py keyword
            // Ensure python is in PATH or specify absolute path
            ProcessBuilder processBuilder = new ProcessBuilder("python", scriptPath, keyword);
            processBuilder.redirectErrorStream(true); // Merge stderr to stdout for debugging
            
            Process process = processBuilder.start();
            
            // Read output
            java.io.InputStream inputStream = process.getInputStream();
            java.util.Scanner s = new java.util.Scanner(inputStream, "UTF-8").useDelimiter("\\A");
            String output = s.hasNext() ? s.next() : "";
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("Python script exited with code " + exitCode);
                System.err.println("Output: " + output);
                return list;
            }

            // Parse JSON output
            // Find the JSON part (in case there are other logs)
            // Assuming the script prints only JSON to stdout at the end, or we scan for [ ... ]
            int jsonStart = output.indexOf("[");
            int jsonEnd = output.lastIndexOf("]");
            
            if (jsonStart != -1 && jsonEnd != -1) {
                String jsonStr = output.substring(jsonStart, jsonEnd + 1);
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<java.util.Map<String, String>> items = mapper.readValue(jsonStr, new com.fasterxml.jackson.core.type.TypeReference<List<java.util.Map<String, String>>>(){});
                
                for (java.util.Map<String, String> item : items) {
                    ProductPriceDTO dto = new ProductPriceDTO();
                    dto.setPlatform(item.get("platform"));
                    dto.setName(item.get("title"));
                    try {
                        String priceStr = item.get("price").replace("¥", "").trim();
                        dto.setPrice(new BigDecimal(priceStr));
                    } catch (Exception e) {
                        dto.setPrice(BigDecimal.ZERO);
                    }
                    dto.setProductUrl(item.get("url"));
                    dto.setImageUrl(item.get("imageUrl"));
                    dto.setShopName(item.get("shopName"));
                    list.add(dto);
                }
            } else {
                System.err.println("No JSON found in Python output: " + output);
            }
            
        } catch (Exception e) {
            System.err.println("Failed to run Python crawler: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    private List<ProductPriceDTO> crawlPinduoduo(String keyword) throws IOException {
        List<ProductPriceDTO> list = new ArrayList<>();
        // Pinduoduo Mobile Search URL
        String url = "http://mobile.yangkeduo.com/search_result.html?search_key=" + keyword;
        
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                    .timeout(5000)
                    .get();

            // Pinduoduo is heavily JS rendered. Jsoup usually gets a skeleton.
            // Simplified logic as placeholder
            Elements scripts = doc.select("script");
            for (Element script : scripts) {
                String html = script.html();
                if (html.contains("goods_name")) {
                     // Parsing logic would go here
                }
            }
        } catch (Exception e) {
            // Pinduoduo often fails without proper headers/cookies
            System.err.println("PDD Jsoup error: " + e.getMessage());
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

        Elements items = doc.select("#J_goodsList ul li");
        
        if (items.isEmpty()) {
             items = doc.select(".gl-item");
        }

        for (Element item : items) {
            try {
                // Title
                String name = item.select(".p-name a em").text();
                if (name.isEmpty()) {
                    name = item.select(".p-name em").text();
                }

                // Price
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

        return results;
    }
}
