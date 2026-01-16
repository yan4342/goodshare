package yan.goodshare;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import yan.goodshare.auth.AuthService;

@SpringBootApplication
@MapperScan("yan.goodshare.mapper")
public class GoodshareApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodshareApplication.class, args);
    }

    @Bean
    CommandLineRunner run(AuthService authService) {
        return args -> {
            authService.createAdminIfNotExists();
        };
    }
}
