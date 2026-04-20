package yan.goodshare.crawler.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import yan.goodshare.crawler.dto.ProductPriceDTO;
import yan.goodshare.crawler.dto.ProductHistoryDTO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class CrawlerService {

    // crawlProductInfo removed to avoid dependency on core Product entity
    
    // Redis cache key prefix
    private static final String REDIS_KEY_PREFIX = "crawler:product:";
    private static final String HISTORY_KEY_PREFIX = "crawler:history:";
    private static final long CACHE_DURATION_HOURS = 24;

    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    public CrawlerService(org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate, JdbcTemplate jdbcTemplate) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clearCache(String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            redisTemplate.delete(REDIS_KEY_PREFIX + keyword);
            System.out.println("Cleared product cache for keyword: " + keyword);
        } else {
            java.util.Set<String> productKeys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            if (productKeys != null && !productKeys.isEmpty()) {
                redisTemplate.delete(productKeys);
            }
            System.out.println("Cleared all product search cache.");
        }
    }

    public List<ProductPriceDTO> searchProducts(String keyword, boolean refresh) {
        String cacheKey = REDIS_KEY_PREFIX + keyword;
        
        if (refresh) {
            redisTemplate.delete(cacheKey);
            System.out.println("Refresh requested. Cleared product cache for: " + keyword);
        }

        // Check Redis Cache
        try {
            if (!refresh) {
                Object cachedData = redisTemplate.opsForValue().get(cacheKey);
                if (cachedData != null) {
                    System.out.println("Returning cached results from Redis for: " + keyword);
                    return (List<ProductPriceDTO>) cachedData;
                }
            }
        } catch (Exception e) {
            System.err.println("Redis cache error: " + e.getMessage());
        }

        List<ProductPriceDTO> results = new ArrayList<>();
        
        // 1. Crawl JD (Disabled)
        /*
         try {
             System.out.println("Starting JD crawl for: " + keyword);
             List<ProductPriceDTO> jdResults = crawlJD(keyword);
             System.out.println("JD results count: " + jdResults.size());
             results.addAll(jdResults);
         } catch (Exception e) {
             System.err.println("JD Crawl failed: " + e.getMessage());
             e.printStackTrace();
         }
         */

        // 2. Crawl Taobao (Disabled)
        /*
         try {
             System.out.println("Starting Taobao crawl for: " + keyword);
             List<ProductPriceDTO> taobaoResults = crawlTaobao(keyword);
             System.out.println("Taobao results count: " + taobaoResults.size());
             results.addAll(taobaoResults);
         } catch (Exception e) {
             System.err.println("Taobao Crawl failed: " + e.getMessage());
         }
         */

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

        // 5. Fallback to Mock Data (Deprecated)
        // if (results.isEmpty()) {
        //     System.out.println("No real results found, skipping mock data as per request.");
        //     // results.addAll(getMockData(keyword));
        // }
        
        // Sort by price ascending
        Collections.sort(results, (p1, p2) -> {
            if (p1.getPrice() == 0 && p2.getPrice() == 0) return 0;
            if (p1.getPrice() == 0) return 1;
            if (p2.getPrice() == 0) return -1;
            return Double.compare(p1.getPrice(), p2.getPrice());
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
        double minPrice = -1;
        double total = 0;
        int count = 0;

        for (ProductPriceDTO dto : results) {
            if (dto.getPrice() > 0) {
                if (minPrice == -1 || dto.getPrice() < minPrice) {
                    minPrice = dto.getPrice();
                }
                total += dto.getPrice();
                count++;
            }
        }

        if (count > 0 && minPrice != -1) {
            // Simple avg
            double avg = total / count;
            // Round to 2 decimal places
            avg = Math.round(avg * 100.0) / 100.0;
            
            String today = java.time.LocalDate.now().toString();
            String key = HISTORY_KEY_PREFIX + keyword;

            ProductHistoryDTO historyDTO = new ProductHistoryDTO(today, minPrice, avg, null);

            Object lastObj = redisTemplate.opsForList().index(key, -1);
            if (lastObj instanceof ProductHistoryDTO) {
                ProductHistoryDTO last = (ProductHistoryDTO) lastObj;
                if (last.getDate().equals(today)) {
                    redisTemplate.opsForList().rightPop(key);
                }
            }
            
            redisTemplate.opsForList().rightPush(key, historyDTO);
            try {
                jdbcTemplate.update(
                        "INSERT INTO price_history(keyword, record_date, min_price, avg_price, note) VALUES (?, ?, ?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE min_price = VALUES(min_price), avg_price = VALUES(avg_price), note = VALUES(note)",
                        keyword, java.sql.Date.valueOf(today), minPrice, avg, historyDTO.getNote()
                );
            } catch (Exception e) {
                System.err.println("Failed to save history to DB: " + e.getMessage());
            }
        }
    }

    public List<ProductHistoryDTO> getProductHistory(String keyword) {
        try {
            return jdbcTemplate.query(
                    "SELECT record_date, min_price, avg_price, note FROM price_history WHERE keyword = ? ORDER BY record_date ASC",
                    (rs, rowNum) -> new ProductHistoryDTO(
                            rs.getDate("record_date").toString(),
                            rs.getDouble("min_price"),
                            rs.getDouble("avg_price"),
                            rs.getString("note")
                    ),
                    keyword
            );
        } catch (Exception e) {
            System.err.println("Failed to get history from DB: " + e.getMessage());
            return getHistoryFromRedis(keyword);
        }
    }

    private List<ProductHistoryDTO> getHistoryFromRedis(String keyword) {
        String key = HISTORY_KEY_PREFIX + keyword;
        try {
            List<Object> list = redisTemplate.opsForList().range(key, 0, -1);
            if (list == null) return new ArrayList<>();

            List<ProductHistoryDTO> history = new ArrayList<>();
            for (Object obj : list) {
                if (obj instanceof ProductHistoryDTO) {
                    history.add((ProductHistoryDTO) obj);
                } else if (obj instanceof java.util.LinkedHashMap) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        ProductHistoryDTO dto = mapper.convertValue(obj, ProductHistoryDTO.class);
                        history.add(dto);
                    } catch (Exception e) {
                        System.err.println("Failed to convert history object: " + e.getMessage());
                    }
                }
            }
            return history;
        } catch (Exception e) {
            System.err.println("Failed to get history from Redis: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @jakarta.annotation.PostConstruct
    private void initHistoryTable() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS price_history (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "keyword VARCHAR(255) NOT NULL," +
                    "record_date DATE NOT NULL," +
                    "min_price DOUBLE NOT NULL," +
                    "avg_price DOUBLE NOT NULL," +
                    "note VARCHAR(255)," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE KEY uk_price_history (keyword, record_date)" +
                    ")");
        } catch (Exception e) {
            System.err.println("Failed to ensure price_history table: " + e.getMessage());
        }
    }

    private List<ProductPriceDTO> crawlManmanbuy(String keyword) {
        List<ProductPriceDTO> list = new ArrayList<>();
        try {
            // Locate the Python script
            String projectDir = System.getProperty("user.dir");
            // Assuming running from crawler-service root or adjusting path
            String scriptPath = projectDir + "/scripts/manmanbuy_spider.py";
            // Check if file exists, if not try relative to crawler-service
            if (!new java.io.File(scriptPath).exists()) {
                 scriptPath = "scripts/manmanbuy_spider.py";
            }
            
            // Build command: python3 script.py keyword 8
            // Limit to 20 items to prevent anti-scraping blocks
            String pythonCmd = "python3";
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pythonCmd = "python";
            }
            
            ProcessBuilder processBuilder = new ProcessBuilder(pythonCmd, scriptPath, keyword, "8");
            processBuilder.redirectErrorStream(true); // Merge stderr to stdout for debugging
            
            Process process = processBuilder.start();
            
            // Read output
            java.io.InputStream inputStream = process.getInputStream();
            java.util.Scanner s = new java.util.Scanner(inputStream, "UTF-8").useDelimiter("\\A");
            String output = s.hasNext() ? s.next() : "";
            
            boolean finished = process.waitFor(90, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                System.err.println("Manmanbuy Python script timed out.");
                System.err.println("Output/Logs before timeout: \n" + output);
                return list;
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                System.err.println("Manmanbuy Python script exited with code " + exitCode);
                System.err.println("Output/Logs: \n" + output);
                return list;
            }
            
            // Always print output for debugging in console
            System.out.println("--- Python Script Output/Logs ---");
            System.out.println(output);
            System.out.println("---------------------------------");

            // Parse JSON output
            // The python script might output logs like "{'headless': True, 'args': ['...']}"
            // which can confuse a simple indexOf("["). We need to find the last valid JSON array.
            // A simple robust way is to just find the LAST "[" and LAST "]" or regex
            // But since the python script ALWAYS prints the result as the very last line,
            // we can split by newline and parse the last non-empty line.
            String[] lines = output.split("\\r?\\n");
            String jsonStr = "";
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                if (line.startsWith("[") && line.endsWith("]")) {
                    jsonStr = line;
                    break;
                }
            }
            
            if (!jsonStr.isEmpty()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<java.util.Map<String, String>> items = mapper.readValue(jsonStr, new com.fasterxml.jackson.core.type.TypeReference<List<java.util.Map<String, String>>>(){});
                
                for (java.util.Map<String, String> item : items) {
                    ProductPriceDTO dto = new ProductPriceDTO();
                    dto.setPlatform(item.get("shop")); // Use shop as platform for aggregator
                    dto.setTitle(item.get("title"));
                    try {
                        String priceStr = item.get("price").replace("¥", "").replace("￥", "").trim();
                        dto.setPrice(Double.parseDouble(priceStr));
                    } catch (Exception e) {
                        dto.setPrice(0.0);
                    }
                    dto.setUrl(item.get("link"));
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
             if (!new java.io.File(scriptPath).exists()) {
                 scriptPath = "scripts/taobao_spider.py";
            }
            
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
                    dto.setTitle(item.get("title"));
                    try {
                        String priceStr = item.get("price").replace("¥", "").trim();
                        dto.setPrice(Double.parseDouble(priceStr));
                    } catch (Exception e) {
                        dto.setPrice(0.0);
                    }
                    dto.setUrl(item.get("url"));
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
                            Double.parseDouble(priceStr),
                            link,
                            imgUrl,
                            shopName
                    ));
                }
            } catch (Exception e) {
                // Continue
            }
        }
        return list;
    }
    
    private List<ProductPriceDTO> getMockData(String keyword) {
        List<ProductPriceDTO> mock = new ArrayList<>();
        mock.add(new ProductPriceDTO("MockJD", "Mock Product " + keyword + " 1", 100.0, "http://example.com", "", "Mock Shop"));
        mock.add(new ProductPriceDTO("MockTaobao", "Mock Product " + keyword + " 2", 120.0, "http://example.com", "", "Mock Shop 2"));
        return mock;
    }
}
