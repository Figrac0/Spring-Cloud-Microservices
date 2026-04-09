package com.unik.company_service.controller;

import com.unik.company_service.dto.CompanyCreateRequest;
import com.unik.company_service.dto.CompanyResponse;
import com.unik.company_service.service.CompanyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<CompanyResponse> getAll(HttpServletRequest request) {
        touchSecurityContext(request);
        return companyService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CompanyResponse create(@Valid @RequestBody CompanyCreateRequest req, HttpServletRequest request) {
        touchSecurityContext(request);
        return companyService.create(req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id, HttpServletRequest request) {
        touchSecurityContext(request);
        companyService.delete(id);
    }

    @GetMapping("/exists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void exists(@PathVariable Long id, HttpServletRequest request) {
        touchSecurityContext(request);
        companyService.assertExists(id);
    }

    @GetMapping("/{id}/name")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public String getName(@PathVariable Long id, HttpServletRequest request) {
        touchSecurityContext(request);
        return companyService.getCompanyName(id);
    }

    private void touchSecurityContext(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        request.isUserInRole("USER");
        request.isUserInRole("ADMIN");
        if (principal == null) {
            throw new IllegalStateException("Authenticated principal is required");
        }
    }
}
