package com.examforge.repository;

import com.examforge.entity.Result;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ResultRepository extends JpaRepository<Result, Long> {

    Optional<Result> findByAttemptId(Long attemptId);

    List<Result> findByAttemptIdIn(List<Long> attemptIds);

    List<Result> findByExamIdOrderByComputedAtAsc(Long examId);

    long countByExamId(Long examId);

    long countByExamIdAndScoreGreaterThan(Long examId, BigDecimal score);

    long countByExamIdAndScoreLessThan(Long examId, BigDecimal score);

    List<Result> findByUserIdOrderByComputedAtDesc(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    /** [avg percentage, max percentage, sum correct, sum incorrect, sum time] for a student. */
    @Query("""
            select avg(r.percentage), max(r.percentage), coalesce(sum(r.correctCount), 0),
                   coalesce(sum(r.incorrectCount), 0), coalesce(sum(r.timeTakenSeconds), 0)
            from Result r where r.userId = :userId""")
    List<Object[]> aggregateForUser(@Param("userId") Long userId);

    /** [subjectId, subjectName, correct, incorrect, unanswered] summed over all of a student's results. */
    @Query("""
            select s.subject.id, s.subject.name, sum(s.correctCount), sum(s.incorrectCount), sum(s.unansweredCount)
            from ResultSubjectScore s where s.result.userId = :userId
            group by s.subject.id, s.subject.name""")
    List<Object[]> subjectTotalsForUser(@Param("userId") Long userId);
}
