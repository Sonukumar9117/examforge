package com.examforge.service;

import com.examforge.dto.request.CategoryRequest;
import com.examforge.dto.request.SubjectRequest;
import com.examforge.dto.response.CategoryResponse;
import com.examforge.dto.response.SubjectResponse;
import com.examforge.entity.ExamCategory;
import com.examforge.entity.Subject;
import com.examforge.entity.User;
import com.examforge.entity.enums.ExamStatus;
import com.examforge.exception.ApiException;
import com.examforge.mapper.CatalogMapper;
import com.examforge.repository.ExamCategoryRepository;
import com.examforge.repository.ExamRepository;
import com.examforge.repository.QuestionRepository;
import com.examforge.repository.SubjectRepository;
import com.examforge.util.Slugs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Exam categories (UPSC, SSC …) and subjects. */
@Service
@RequiredArgsConstructor
public class CatalogService {
    private final ExamCategoryRepository categories;
    private final SubjectRepository subjects;
    private final ExamRepository exams;
    private final QuestionRepository questions;
    private final AuditService audit;


    // ------------------------------------------------------------------ public reads

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(boolean includeInactive) {
        Map<Long, Long> published = countMap(exams.countGroupedByCategory(ExamStatus.PUBLISHED));
        List<ExamCategory> list = includeInactive
                ? categories.findAllByOrderByDisplayOrderAscNameAsc()
                : categories.findByActiveTrueOrderByDisplayOrderAscNameAsc();
        return list.stream().map(c -> CatalogMapper.toResponse(c, published.getOrDefault(c.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategory(String slug) {
        ExamCategory c = categories.findBySlug(slug).filter(ExamCategory::isActive)
                .orElseThrow(() -> ApiException.notFound("Exam category"));
        return CatalogMapper.toResponse(c, exams.countByCategoryIdAndStatus(c.getId(), ExamStatus.PUBLISHED));
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> listSubjects(boolean withCounts) {
        Map<Long, Long> counts = withCounts ? countMap(questions.countGroupedBySubject()) : Map.of();
        return subjects.findAllByOrderByNameAsc().stream()
                .map(s -> withCounts ? CatalogMapper.toResponse(s, counts.getOrDefault(s.getId(), 0L)) : CatalogMapper.toResponse(s))
                .toList();
    }

    @Transactional(readOnly = true)
    public ExamCategory requireCategory(Long id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("Exam category"));
    }

    @Transactional(readOnly = true)
    public Subject requireSubject(Long id) {
        return subjects.findById(id).orElseThrow(() -> ApiException.notFound("Subject"));
    }

    // ------------------------------------------------------------------ admin: categories

    @Transactional
    public CategoryResponse createCategory(CategoryRequest req, User actor, String ip) {
        String name = req.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("CATEGORY_EXISTS", "A category with this name already exists");
        }
        ExamCategory c = new ExamCategory(name, Slugs.unique(name, categories::existsBySlug));
        apply(c, req);
        categories.save(c);
        audit.record(actor, "CATEGORY_CREATED", "ExamCategory", c.getId(), name, ip);
        return CatalogMapper.toResponse(c, 0);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest req, User actor, String ip) {
        ExamCategory c = requireCategory(id);
        String name = req.name().trim();
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("CATEGORY_EXISTS", "A category with this name already exists");
        }
        c.setName(name); // slug stays stable so existing links keep working
        apply(c, req);
        audit.record(actor, "CATEGORY_UPDATED", "ExamCategory", id, name, ip);
        return CatalogMapper.toResponse(c, exams.countByCategoryIdAndStatus(id, ExamStatus.PUBLISHED));
    }

    @Transactional
    public void deleteCategory(Long id, User actor, String ip) {
        ExamCategory c = requireCategory(id);
        if (exams.existsByCategoryId(id)) {
            throw ApiException.conflict("CATEGORY_IN_USE",
                    "This category has exams. Move or delete them first, or mark the category inactive.");
        }
        categories.delete(c);
        audit.record(actor, "CATEGORY_DELETED", "ExamCategory", id, c.getName(), ip);
    }

    private void apply(ExamCategory c, CategoryRequest req) {
        c.setDescription(blankToNull(req.description()));
        c.setIcon(blankToNull(req.icon()));
        c.setDisplayOrder(req.displayOrder());
        c.setActive(req.active());
        Set<Long> ids = req.subjectIds() == null ? Set.of() : req.subjectIds();
        List<Subject> found = subjects.findAllById(ids);
        if (found.size() != ids.size()) throw ApiException.badRequest("SUBJECT_NOT_FOUND", "One or more subjects do not exist");
        c.getSubjects().clear();
        c.getSubjects().addAll(new HashSet<>(found));
    }

    // ------------------------------------------------------------------ admin: subjects

    @Transactional
    public SubjectResponse createSubject(SubjectRequest req, User actor, String ip) {
        String name = req.name().trim();
        if (subjects.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("SUBJECT_EXISTS", "A subject with this name already exists");
        }
        Subject s = subjects.save(new Subject(name, Slugs.unique(name, subjects::existsBySlug), blankToNull(req.description())));
        audit.record(actor, "SUBJECT_CREATED", "Subject", s.getId(), name, ip);
        return CatalogMapper.toResponse(s, 0);
    }

    @Transactional
    public SubjectResponse updateSubject(Long id, SubjectRequest req, User actor, String ip) {
        Subject s = requireSubject(id);
        String name = req.name().trim();
        if (subjects.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("SUBJECT_EXISTS", "A subject with this name already exists");
        }
        s.setName(name);
        s.setDescription(blankToNull(req.description()));
        audit.record(actor, "SUBJECT_UPDATED", "Subject", id, name, ip);
        return CatalogMapper.toResponse(s);
    }

    @Transactional
    public void deleteSubject(Long id, User actor, String ip) {
        Subject s = requireSubject(id);
        if (questions.existsBySubjectId(id)) {
            throw ApiException.conflict("SUBJECT_IN_USE", "This subject has questions. Move or delete them first.");
        }
        subjects.delete(s);
        audit.record(actor, "SUBJECT_DELETED", "Subject", id, s.getName(), ip);
    }

    // ------------------------------------------------------------------ helpers

    private static Map<Long, Long> countMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
