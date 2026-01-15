package yan.goodshare;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("yan.goodshare.mapper")
public class GoodshareApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodshareApplication.class, args);
    }

}
