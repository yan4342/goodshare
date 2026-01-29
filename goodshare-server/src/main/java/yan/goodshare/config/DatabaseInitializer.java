package yan.goodshare.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.PostConstruct;

@Configuration
public class DatabaseInitializer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        String sql = "CREATE TABLE IF NOT EXISTS user_tag_weights (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "tag_id BIGINT NOT NULL, " +
                "weight DOUBLE DEFAULT 1.0, " +
                "UNIQUE KEY uk_user_tag (user_id, tag_id)" +
                ")";
        jdbcTemplate.execute(sql);
        System.out.println("DatabaseInitializer: Ensure user_tag_weights table exists.");
    }
}
