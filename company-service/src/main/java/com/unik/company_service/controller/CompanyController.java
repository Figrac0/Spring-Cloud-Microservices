package com.unik.company_service.controller;

import com.unik.company_service.dto.CompanyCreateRequest;
import com.unik.company_service.dto.CompanyResponse;
import com.unik.company_service.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public List<CompanyResponse> getAll() {
        return companyService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(@Valid @RequestBody CompanyCreateRequest req) {
        return companyService.create(req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        companyService.delete(id);
    }

    @GetMapping("/exists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void exists(@PathVariable Long id) {
        companyService.assertExists(id);
    }

    @GetMapping("/{id}/name")
    public String getName(@PathVariable Long id) {
        return companyService.getCompanyName(id);
    }
}
