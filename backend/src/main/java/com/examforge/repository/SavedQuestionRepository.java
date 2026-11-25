package com.examforge.repository;

import com.examforge.entity.SavedQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface SavedQuestionRepository extends JpaRepository<SavedQuestion, Long> {

    Optional<SavedQuestion> findByUserIdAndQuestionId(Long userId, Long questionId);

    Page<SavedQuestion> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("select s.question.id from SavedQuestion s where s.userId = :userId and s.question.id in :ids")
    Set<Long> savedAmong(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);
}
