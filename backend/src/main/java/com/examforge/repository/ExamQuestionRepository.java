package com.examforge.repository;

import com.examforge.entity.ExamQuestion;
import com.examforge.entity.enums.ExamStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {

    boolean existsByQuestionId(Long questionId);

    boolean existsByQuestionIdAndExamStatus(Long questionId, ExamStatus status);
}
