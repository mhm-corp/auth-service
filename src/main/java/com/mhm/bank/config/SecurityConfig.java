package com.mhm.bank.config;

import com.mhm.bank.service.external.keycloak.IKeycloakService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    private IKeycloakService keycloakService;



    public SecurityConfig(IKeycloakService keycloakService) {
        this.keycloakService = keycloakService;
        logger.info("Initializing SecurityConfig with KeycloakService");
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        logger.debug("Configuring SecurityFilterChain");
        try {
            SecurityFilterChain filterChain = http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(request -> request
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .anyRequest().permitAll()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
            logger.info("SecurityFilterChain configured successfully");
            return filterChain;
        } catch (Exception e) {
            logger.error("Failed to configure SecurityFilterChain: {}", e.getMessage(), e);
            throw e;
        }
    }


}
