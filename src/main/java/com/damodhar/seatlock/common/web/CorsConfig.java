package com.damodhar.seatlock.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets the web app (and only the web app) call the API from the browser. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String webOrigin;

    public CorsConfig(@Value("${seatlock.web-origin}") String webOrigin) {
        this.webOrigin = webOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(webOrigin.split(","))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
