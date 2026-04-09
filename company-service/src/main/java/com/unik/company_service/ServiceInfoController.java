package com.unik.company_service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RefreshScope
@RestController
public class ServiceInfoController {

    @Value("${service.description:undefined}")
    private String description;

    @GetMapping("/description")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public String description() {
        return description;
    }
}
