package com.examforge.repository;

import com.examforge.entity.ExamCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamCategoryRepository extends JpaRepository<ExamCategory, Long> {

    @EntityGraph(attributePaths = "subjects")
    List<ExamCategory> findAllByOrderByDisplayOrderAscNameAsc();

    @EntityGraph(attributePaths = "subjects")
    List<ExamCategory> findByActiveTrueOrderByDisplayOrderAscNameAsc();

    @EntityGraph(attributePaths = "subjects")
    Optional<ExamCategory> findBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsBySlug(String slug);
}
