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
                System.out.println("Ensured post_tags table exists.");
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
                System.out.println("Ensured notifications table exists.");
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
        };
    }
}
