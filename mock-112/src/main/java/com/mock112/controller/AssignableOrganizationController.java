package com.mock112.controller;

import com.mock112.assignment.AssignableOrganization;
import com.mock112.assignment.AssignableOrganizationCatalog;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock-112/assignable-organizations")
public class AssignableOrganizationController {

    private final AssignableOrganizationCatalog catalog;

    public AssignableOrganizationController(AssignableOrganizationCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ResponseEntity<List<AssignableOrganization>> list() {
        return ResponseEntity.ok(catalog.findAll());
    }
}
