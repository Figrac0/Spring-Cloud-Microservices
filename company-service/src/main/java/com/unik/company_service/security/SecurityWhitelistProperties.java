package com.unik.company_service.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "security")
public class SecurityWhitelistProperties {
    private List<String> whitelist = new ArrayList<>(List.of("/auth/**", "/actuator/**", "/eureka/**"));

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }
}
