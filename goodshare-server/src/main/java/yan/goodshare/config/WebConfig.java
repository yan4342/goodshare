package yan.goodshare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        String windowsPath = "file:D:/IntelliJ IDEA Community Edition 2024.1/IdeaProjects/goodshare/uploads/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(
                    "file:/app/uploads/",
                    windowsPath,
                    "file:uploads/",
                    "file:../uploads/"
                )
                .resourceChain(true)
                .addResolver(new UploadsThumbFallbackResolver());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173", "http://localhost:8080", "http://localhost:8088")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    private static class UploadsThumbFallbackResolver extends PathResourceResolver {
        @Override
        protected org.springframework.core.io.Resource getResource(String resourcePath, org.springframework.core.io.Resource location) throws java.io.IOException {
            org.springframework.core.io.Resource resource = super.getResource(resourcePath, location);
            if (resource != null) {
                return resource;
            }
            int thumbIndex = resourcePath.lastIndexOf("_thumb.");
            if (thumbIndex >= 0) {
                String originalPath = resourcePath.substring(0, thumbIndex) + "." + resourcePath.substring(thumbIndex + 7);
                return super.getResource(originalPath, location);
            }
            return null;
        }
    }
}
