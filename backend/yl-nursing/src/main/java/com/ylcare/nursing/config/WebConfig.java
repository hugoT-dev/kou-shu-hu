package com.ylcare.nursing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${yl.frontend.dir:}")
    private String configured;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        List<String> locations = new ArrayList<>();
        for (Path p : candidates()) {
            if (Files.isDirectory(p)) {
                locations.add(p.toUri().toString());
            }
        }
        if (!locations.isEmpty()) {
            registry.addResourceHandler("/", "/index.html", "/assets/**")
                    .addResourceLocations(locations.toArray(String[]::new));
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOriginPatterns("*").allowedMethods("*").allowedHeaders("*");
    }

    private List<Path> candidates() {
        List<Path> list = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            list.add(Path.of(configured).toAbsolutePath().normalize());
        }
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        list.add(cwd.resolve("../../frontend/dist").normalize());
        list.add(cwd.resolve("../frontend/dist").normalize());
        list.add(Path.of("D:/yl-care/frontend/dist"));
        return list;
    }
}
