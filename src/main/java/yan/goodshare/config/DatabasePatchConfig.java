package yan.goodshare.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabasePatchConfig implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabasePatchConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Checking database schema...");
        try {
            // Try to add the column. If it exists, it will throw an exception which we catch.
            // This is a simple way to ensure the column exists.
            jdbcTemplate.execute("ALTER TABLE posts ADD COLUMN cover_url VARCHAR(255)");
            System.out.println("Successfully added cover_url column to posts table.");
        } catch (Exception e) {
            if (e.getMessage().toLowerCase().contains("duplicate column")) {
                System.out.println("Column cover_url already exists in posts table.");
            } else {
                System.out.println("Warning during schema update: " + e.getMessage());
            }
        }

        try {
            // Upgrade content column to LONGTEXT to support rich text
            jdbcTemplate.execute("ALTER TABLE posts MODIFY COLUMN content LONGTEXT");
            System.out.println("Successfully modified content column to LONGTEXT in posts table.");
        } catch (Exception e) {
            System.out.println("Warning during content column modification: " + e.getMessage());
        }

        try {
            // Add images column to store multiple image URLs (JSON array)
            jdbcTemplate.execute("ALTER TABLE posts ADD COLUMN images LONGTEXT");
            System.out.println("Successfully added images column to posts table.");
        } catch (Exception e) {
            String msg = e.getMessage().toLowerCase();
            if (msg.contains("duplicate column")) {
                System.out.println("Column images already exists in posts table.");
            } else {
                System.out.println("Warning during schema update (images): " + e.getMessage());
                if (e.getCause() != null) {
                    System.out.println("Cause: " + e.getCause().getMessage());
                }
            }
        }
    }
}
