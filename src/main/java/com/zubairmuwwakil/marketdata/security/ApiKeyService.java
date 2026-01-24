package com.zubairmuwwakil.marketdata.security;

import com.zubairmuwwakil.marketdata.config.ApiKeyProperties;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ApiKeyService {

    private final ApiKeyProperties properties;

    public ApiKeyService(ApiKeyProperties properties) {
        this.properties = properties;
    }

    public Optional<ApiKeyPrincipal> authenticate(String apiKey) {
        return properties.findByKey(apiKey)
                .map(entry -> new ApiKeyPrincipal(apiKey, entry.role()));
    }
}