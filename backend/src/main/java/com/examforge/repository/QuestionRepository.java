package com.examforge.repository;

import com.examforge.entity.Question;
import com.examforge.entity.enums.QuestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {

    /** Rows of [subjectId, count]. */
    @Query("select q.subject.id, count(q) from Question q group by q.subject.id")
    List<Object[]> countGroupedBySubject();

    boolean existsBySubjectId(Long subjectId);

    long countByStatus(QuestionStatus status);
}
