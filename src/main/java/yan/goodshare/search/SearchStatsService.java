package yan.goodshare.search;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import yan.goodshare.entity.SearchStats;
import yan.goodshare.mapper.SearchStatsMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SearchStatsService {

    private final SearchStatsMapper searchStatsMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String HOT_KEYWORDS_CACHE_KEY = "hot_keywords";

    public SearchStatsService(SearchStatsMapper searchStatsMapper, RedisTemplate<String, Object> redisTemplate) {
        this.searchStatsMapper = searchStatsMapper;
        this.redisTemplate = redisTemplate;
    }

    @Async
    public void incrementSearchCount(String keyword) {
        try {
            searchStatsMapper.incrementSearchCount(keyword);
            // We can optionally invalidate cache here, but for "Hot Search", 
            // a small delay (e.g. 10 mins cache) is usually acceptable and better for performance.
            // If real-time is required: redisTemplate.delete(HOT_KEYWORDS_CACHE_KEY);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public List<SearchStats> getHotKeywords() {
        // Try cache
        try {
            List<SearchStats> cached = (List<SearchStats>) redisTemplate.opsForValue().get(HOT_KEYWORDS_CACHE_KEY);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            // Ignore cache errors (e.g. serialization issues) and fallback to DB
            e.printStackTrace();
        }

        // DB fallback
        List<SearchStats> stats = searchStatsMapper.selectTopKeywords(10);

        // Write cache (10 min TTL)
        if (stats != null && !stats.isEmpty()) {
            try {
                redisTemplate.opsForValue().set(HOT_KEYWORDS_CACHE_KEY, stats, 10, TimeUnit.MINUTES);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return stats;
    }
}
