package com.examforge.repository;

import com.examforge.entity.Exam;
import com.examforge.entity.enums.ExamStatus;
import com.examforge.entity.enums.ExamType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long>, JpaSpecificationExecutor<Exam> {

    /** Rows of [categoryId, count] for exams with the given status. */
    @Query("select e.category.id, count(e) from Exam e where e.status = :status group by e.category.id")
    List<Object[]> countGroupedByCategory(@Param("status") ExamStatus status);

    long countByCategoryIdAndStatus(Long categoryId, ExamStatus status);

    long countByStatus(ExamStatus status);

    boolean existsByCategoryId(Long categoryId);

    boolean existsBySlug(String slug);

    Optional<Exam> findBySlugAndStatus(String slug, ExamStatus status);

    List<Exam> findByStatusAndTypeAndEndsAtAfterOrderByStartsAtAsc(ExamStatus status, ExamType type, java.time.Instant endsAfter);
}
