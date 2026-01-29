package yan.goodshare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        // Map /uploads/** to the project root uploads directory
        // Using absolute path to avoid relative path issues in different execution contexts
        String uploadsPath = "file:D:/IntelliJ IDEA Community Edition 2024.1/IdeaProjects/goodshare/uploads/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadsPath, "file:../uploads/", "file:uploads/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173", "http://localhost:8080") // Allow Vue dev server and local access
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
