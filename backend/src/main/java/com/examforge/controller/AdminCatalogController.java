package com.examforge.controller;

import com.examforge.dto.request.CategoryRequest;
import com.examforge.dto.request.SubjectRequest;
import com.examforge.dto.response.CategoryResponse;
import com.examforge.dto.response.SubjectResponse;
import com.examforge.security.CurrentUser;
import com.examforge.service.CatalogService;
import com.examforge.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — catalog")
public class AdminCatalogController {
    private final CatalogService catalog;
    private final UserService users;

    public AdminCatalogController(CatalogService catalog, UserService users) {
        this.catalog = catalog;
        this.users = users;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalog.listCategories(true);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CategoryRequest req,
                                           HttpServletRequest http) {
        return catalog.createCategory(req, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }

    @PutMapping("/categories/{id}")
    public CategoryResponse updateCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                           @Valid @RequestBody CategoryRequest req, HttpServletRequest http) {
        return catalog.updateCategory(id, req, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, HttpServletRequest http) {
        catalog.deleteCategory(id, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }

    @GetMapping("/subjects")
    public List<SubjectResponse> subjects() {
        return catalog.listSubjects(true);
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectResponse createSubject(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SubjectRequest req,
                                         HttpServletRequest http) {
        return catalog.createSubject(req, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }

    @PutMapping("/subjects/{id}")
    public SubjectResponse updateSubject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                         @Valid @RequestBody SubjectRequest req, HttpServletRequest http) {
        return catalog.updateSubject(id, req, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }

    @DeleteMapping("/subjects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSubject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, HttpServletRequest http) {
        catalog.deleteSubject(id, users.require(CurrentUser.id(jwt)), http.getRemoteAddr());
    }
}
