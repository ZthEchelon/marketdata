package com.zubairmuwwakil.marketdata.config;

import com.zubairmuwwakil.marketdata.security.ApiKeyAuthFilter;
import com.zubairmuwwakil.marketdata.security.ApiKeyService;
import com.zubairmuwwakil.marketdata.security.RateLimitFilter;
import com.zubairmuwwakil.marketdata.service.ingestion.QuotaService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ApiKeyService apiKeyService,
                                                   com.zubairmuwwakil.marketdata.security.AppKeyQuotaService appKeyQuotaService,
                                                   RateLimitProperties rateLimitProperties,
                                                   QuotaService quotaService) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/watchlist.html",
                                "/indicators.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api/v1/health",
                                "/actuator/health/**",
                                "/actuator/info/**"
                        ).permitAll()
                        .requestMatchers("/api/v1/admin/api-key", "/api/v1/admin/quota").hasAnyRole("ADMIN", "USER")
                        .requestMatchers("/api/v1/admin/**", "/actuator/prometheus").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "USER")
                        .anyRequest().permitAll()
                )
                .addFilterBefore(new ApiKeyAuthFilter(apiKeyService, appKeyQuotaService), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new RateLimitFilter(rateLimitProperties, quotaService), ApiKeyAuthFilter.class);

        return http.build();
    }
}