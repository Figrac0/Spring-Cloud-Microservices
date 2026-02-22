package com.unik.user_service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RefreshScope
@RestController
public class ServiceInfoController {

    // @Value("${service.description}")
    // private String description;

    @Value("${service.description:undefined}")
    private String description;

    @GetMapping("/description")
    public String description() {
        return description;
    }
}