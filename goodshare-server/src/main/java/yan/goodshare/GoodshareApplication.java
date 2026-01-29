package yan.goodshare;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import yan.goodshare.auth.AuthService;
import yan.goodshare.post.PostService;

@SpringBootApplication
@EnableAsync
@MapperScan("yan.goodshare.mapper")
public class GoodshareApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodshareApplication.class, args);
    }

    @Bean
    CommandLineRunner run(AuthService authService, PostService postService) {
        return args -> {
            try {
                authService.createAdminIfNotExists();
                System.out.println("Executing startup reindex...");
                postService.reindexAllPosts();
                System.out.println("Startup reindex completed.");
            } catch (Exception e) {
                System.err.println("Startup task failed: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
}
