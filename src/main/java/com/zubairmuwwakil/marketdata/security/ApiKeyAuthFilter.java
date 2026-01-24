package com.zubairmuwwakil.marketdata.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;
    private final AppKeyQuotaService quotaService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ApiKeyAuthFilter(ApiKeyService apiKeyService, AppKeyQuotaService quotaService) {
        this.apiKeyService = apiKeyService;
        this.quotaService = quotaService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return isPublicPath(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        var principalOpt = apiKeyService.authenticate(apiKey);

        if (principalOpt.isEmpty()) {
            respondUnauthorized(response);
            return;
        }

        ApiKeyPrincipal principal = principalOpt.get();
        try {
            quotaService.consume(apiKey);
        } catch (IllegalStateException ex) {
            respondTooManyRequests(response);
            return;
        }
        var auth = new UsernamePasswordAuthenticationToken(
            principal.key(),
            apiKey,
            List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        MDC.put("apiKeyId", fingerprint(apiKey));
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("apiKeyId");
        }
    }

    private boolean isPublicPath(String path) {
        return pathMatcher.match("/", path)
                || pathMatcher.match("/**/*.html", path)
                || pathMatcher.match("/index.html", path)
                || pathMatcher.match("/watchlist.html", path)
                || pathMatcher.match("/indicators.html", path)
                || pathMatcher.match("/error", path)
                || pathMatcher.match("/swagger-ui/**", path)
                || pathMatcher.match("/v3/api-docs/**", path)
            || pathMatcher.match("/api/v1/health", path)
                || pathMatcher.match("/actuator/health/**", path)
                || pathMatcher.match("/actuator/info/**", path)
                || pathMatcher.match("/css/**", path)
                || pathMatcher.match("/js/**", path)
                || pathMatcher.match("/assets/**", path)
                || pathMatcher.match("/images/**", path);
    }

    private void respondUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Missing or invalid API key.\"}");
    }

    private void respondTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"title\":\"Quota Exceeded\",\"status\":429,\"detail\":\"MarketLens API key quota exceeded.\"}");
    }

    private String fingerprint(String apiKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < digest.length; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return "unknown";
        }
    }
}
