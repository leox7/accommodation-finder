package com.Leon.accommodation_finder.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

// Serves saved listing images, so /uploads/listings/<file-name> opens the image in a browser
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadDir;

    @Autowired
    public WebConfig(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().toUri().toString();
        // Spring needs the folder location to end with a slash
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/listings/**")
                .addResourceLocations(location);
    }
}
