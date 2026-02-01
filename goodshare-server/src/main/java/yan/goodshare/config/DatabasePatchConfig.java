package yan.goodshare.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DatabasePatchConfig {

    @Bean
    public CommandLineRunner initDatabase(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. Ensure created_at column in posts
            try {
                jdbcTemplate.execute("ALTER TABLE posts ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                System.out.println("Added created_at column to posts table.");
            } catch (Exception e) {
                // Column likely exists
            }

            // 2. Ensure post_tags table exists
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS post_tags (" +
                        "post_id BIGINT NOT NULL," +
                        "tag_id BIGINT NOT NULL," +
                        "PRIMARY KEY (post_id, tag_id)" +
                        ")");
                //System.out.println("Ensured post_tags table exists.");
            } catch (Exception e) {
                System.err.println("Error creating post_tags table: " + e.getMessage());
            }

            // 3. Ensure nickname column in users
            try {
                jdbcTemplate.execute("ALTER TABLE users ADD COLUMN nickname VARCHAR(50)");
                System.out.println("Added nickname column to users table.");
            } catch (Exception e) {
                // Column likely exists
            }

            // 4. Ensure admin user exists with correct role
            try {
                String adminUsername = "admin";
                String checkSql = "SELECT count(*) FROM users WHERE username = ?";
                Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, adminUsername);
                
                if (count == null || count == 0) {
                    // Create admin user
                    String insertSql = "INSERT INTO users (username, password, email, role, nickname) VALUES (?, ?, ?, ?, ?)";
                    jdbcTemplate.update(insertSql, adminUsername, passwordEncoder.encode("admin123"), "admin@goodshare.com", "ROLE_ADMIN", "Administrator");
                    System.out.println("Created admin user 'admin' with password 'admin123'.");
                } else {
                    // Update existing admin role and password to ensure access
                    String updateSql = "UPDATE users SET role = 'ROLE_ADMIN', password = ? WHERE username = ?";
                    jdbcTemplate.update(updateSql, passwordEncoder.encode("admin123"), adminUsername);
                    System.out.println("Updated admin user role to ROLE_ADMIN and reset password to 'admin123'.");
                }
            } catch (Exception e) {
                System.err.println("Error setting up admin user: " + e.getMessage());
            }

            // 5. Ensure notifications table exists
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS notifications (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "recipient_id BIGINT NOT NULL," +
                        "sender_id BIGINT NOT NULL," +
                        "type VARCHAR(50) NOT NULL," +
                        "related_id BIGINT," +
                        "is_read BOOLEAN DEFAULT FALSE," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")");
                //System.out.println("Ensured notifications table exists.");
            } catch (Exception e) {
                System.err.println("Error creating notifications table: " + e.getMessage());
            }

            // 6. Increase images column size to LONGTEXT
            try {
                jdbcTemplate.execute("ALTER TABLE posts MODIFY COLUMN images LONGTEXT");
                System.out.println("Modified images column in posts table to LONGTEXT.");
            } catch (Exception e) {
                System.err.println("Error modifying images column: " + e.getMessage());
            }

            // 7. Add view_count column to posts
            try {
                jdbcTemplate.execute("ALTER TABLE posts ADD COLUMN view_count INT DEFAULT 0");
                System.out.println("Added view_count column to posts table.");
            } catch (Exception e) {
                // Column likely exists
            }

            // 8. Create post_views table
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS post_views (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "user_id BIGINT NOT NULL," +
                        "post_id BIGINT NOT NULL," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        "UNIQUE KEY unique_user_post (user_id, post_id)" +
                        ")");
                //System.out.println("Ensured post_views table exists.");
            } catch (Exception e) {
                System.err.println("Error creating post_views table: " + e.getMessage());
            }

            // 9. Add status column to posts
            try {
                jdbcTemplate.execute("ALTER TABLE posts ADD COLUMN status INT DEFAULT 0");
                System.out.println("Added status column to posts table.");
            } catch (Exception e) {
                // Column likely exists
            }

            // 9. Ensure app_configs table exists and has defaults
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_configs (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "config_key VARCHAR(100) NOT NULL UNIQUE," +
                        "config_value VARCHAR(255) NOT NULL," +
                        "description VARCHAR(255)," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                        ")");
                //System.out.println("Ensured app_configs table exists.");

                // Insert defaults if not exist
                String checkSql = "SELECT count(*) FROM app_configs WHERE config_key = ?";
                
                // View Weight
                if (jdbcTemplate.queryForObject(checkSql, Integer.class, "weight.view") == 0) {
                    jdbcTemplate.update("INSERT INTO app_configs (config_key, config_value, description) VALUES (?, ?, ?)", 
                            "weight.view", "0.5", "Weight for View interaction");
                }
                // Like Weight
                if (jdbcTemplate.queryForObject(checkSql, Integer.class, "weight.like") == 0) {
                    jdbcTemplate.update("INSERT INTO app_configs (config_key, config_value, description) VALUES (?, ?, ?)", 
                            "weight.like", "1.0", "Weight for Like interaction");
                }
                // Favorite Weight
                if (jdbcTemplate.queryForObject(checkSql, Integer.class, "weight.favorite") == 0) {
                    jdbcTemplate.update("INSERT INTO app_configs (config_key, config_value, description) VALUES (?, ?, ?)", 
                            "weight.favorite", "2.0", "Weight for Favorite interaction");
                }
                // Comment Weight
                if (jdbcTemplate.queryForObject(checkSql, Integer.class, "weight.comment") == 0) {
                    jdbcTemplate.update("INSERT INTO app_configs (config_key, config_value, description) VALUES (?, ?, ?)", 
                            "weight.comment", "3.0", "Weight for Comment interaction");
                }
                // Comment Count Weight
                if (jdbcTemplate.queryForObject(checkSql, Integer.class, "weight.comment_count") == 0) {
                    jdbcTemplate.update("INSERT INTO app_configs (config_key, config_value, description) VALUES (?, ?, ?)", 
                            "weight.comment_count", "0.1", "Weight for Post Comment Count (Popularity)");
                }
                
                //System.out.println("Ensured default app_configs values.");
            } catch (Exception e) {
                System.err.println("Error setting up app_configs: " + e.getMessage());
            }
            // 10. Create search_stats table
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS search_stats (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "keyword VARCHAR(255) NOT NULL UNIQUE," +
                        "search_count BIGINT DEFAULT 1," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                        ")");
                //System.out.println("Ensured search_stats table exists.");
            } catch (Exception e) {
                System.err.println("Error creating search_stats table: " + e.getMessage());
            }

            // 11. Create follows table
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS follows (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "follower_id BIGINT NOT NULL," +
                        "followed_id BIGINT NOT NULL," +
                        "UNIQUE KEY uk_follow (follower_id, followed_id)" +
                        ")");
                //System.out.println("Ensured follows table exists.");
            } catch (Exception e) {
                System.err.println("Error creating follows table: " + e.getMessage());
            }

            // 12. Create messages table
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS messages (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "sender_id BIGINT NOT NULL," +
                        "receiver_id BIGINT NOT NULL," +
                        "content TEXT," +
                        "is_read BOOLEAN DEFAULT FALSE," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")");
                //System.out.println("Ensured messages table exists.");
            } catch (Exception e) {
                System.err.println("Error creating messages table: " + e.getMessage());
            }

            // 13. Create appraisals table
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS appraisals (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "user_id BIGINT NOT NULL," +
                        "product_name VARCHAR(255) NOT NULL," +
                        "description TEXT," +
                        "images LONGTEXT," +
                        "status INT DEFAULT 0," +
                        "real_votes INT DEFAULT 0," +
                        "fake_votes INT DEFAULT 0," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")");
                //System.out.println("Ensured appraisals table exists.");
            } catch (Exception e) {
                System.err.println("Error creating appraisals table: " + e.getMessage());
            }

            // 14. Create appraisal_votes table
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS appraisal_votes (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "appraisal_id BIGINT NOT NULL," +
                        "user_id BIGINT NOT NULL," +
                        "vote_type INT NOT NULL," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")");
                //System.out.println("Ensured appraisal_votes table exists.");
            } catch (Exception e) {
                System.err.println("Error creating appraisal_votes table: " + e.getMessage());
            }
        };
    }
}
