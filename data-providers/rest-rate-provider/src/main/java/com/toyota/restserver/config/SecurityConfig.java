package com.toyota.restserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${app.security.username}")
    private String username;

    @Value("${app.security.password}")
    private String password;

    @PostConstruct
    public void logCredentials() {
        logger.info("REST Provider Security Configuration Loaded");
        logger.info("REST Provider Security - Username: '{}'", username);
        logger.info("REST Provider Security - Password configured: {}", 
                   password != null && !password.isEmpty() && !"defaultpass".equals(password));
        
        if ("defaultuser".equals(username) || "defaultpass".equals(password)) {
            logger.warn("REST Provider is using default credentials! Please configure APP_SECURITY_USERNAME and APP_SECURITY_PASSWORD environment variables.");
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Development/Test için NoOpPasswordEncoder kullanıyoruz
        // Production'da BCryptPasswordEncoder kullanılmalı
        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        logger.debug("Creating UserDetailsService with username: '{}', password configured: {}", 
                    username, password != null && !password.isEmpty());

        UserDetails user = User.builder()
                .username(username)
                .password(password) // NoOpPasswordEncoder kullandığımız için {noop} prefix'ine gerek yok
                .roles("USER")
                .build();

        logger.info("REST Provider user created successfully: '{}'", username);
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        logger.info("Configuring REST Provider Security Filter Chain");
        
        http
                .csrf(csrf -> csrf.disable()) // Stateless API için CSRF kapatılır
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/actuator/health").permitAll() // Health check kimlik doğrulaması gerektirmez
                        .anyRequest().authenticated() // Diğer tüm endpoints kimlik doğrulaması gerektirir
                )
                .httpBasic(basic -> basic.realmName("REST Rate Provider")) // Basic Auth etkinleştir
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)); // Stateless

        logger.info("REST Provider Security Filter Chain configured successfully");
        return http.build();
    }
}