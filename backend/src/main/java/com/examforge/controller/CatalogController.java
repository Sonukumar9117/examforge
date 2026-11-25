package com.examforge.controller;

import com.examforge.dto.response.CategoryResponse;
import com.examforge.dto.response.SubjectResponse;
import com.examforge.service.CatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public catalog: active exam categories and subjects (no login needed, used by signup/explore screens). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalog")
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/exam-categories")
    public List<CategoryResponse> categories() {
        return catalog.listCategories(false);
    }

    @GetMapping("/exam-categories/{slug}")
    public CategoryResponse category(@PathVariable String slug) {
        return catalog.getCategory(slug);
    }

    @GetMapping("/subjects")
    public List<SubjectResponse> subjects() {
        return catalog.listSubjects(false);
    }
}
