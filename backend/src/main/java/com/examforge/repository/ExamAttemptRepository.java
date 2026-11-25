package com.examforge.repository;

import com.examforge.entity.Exam;
import com.examforge.entity.ExamAttempt;
import com.examforge.entity.enums.AttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    /** Row lock so a student's submit and the expiry job can never score the same attempt twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExamAttempt a where a.id = :id")
    Optional<ExamAttempt> findByIdForUpdate(@Param("id") Long id);

    Optional<ExamAttempt> findFirstByExamIdAndUserIdAndStatus(Long examId, Long userId, AttemptStatus status);

    long countByExamIdAndUserId(Long examId, Long userId);

    @Query("select coalesce(max(a.attemptNo), 0) from ExamAttempt a where a.exam.id = :examId and a.userId = :userId")
    int maxAttemptNo(@Param("examId") Long examId, @Param("userId") Long userId);

    @Query("select a.id from ExamAttempt a where a.status = :status and a.deadlineAt < :before")
    List<Long> findIdsByStatusAndDeadlineBefore(@Param("status") AttemptStatus status, @Param("before") Instant before);

    Page<ExamAttempt> findByUserIdOrderByStartedAtDesc(Long userId, Pageable pageable);

    List<ExamAttempt> findByUserIdAndStatusOrderByStartedAtDesc(Long userId, AttemptStatus status);

    boolean existsByExamId(Long examId);

    /** Exams of the student's finished attempts that contain the given question. */
    @Query("""
            select distinct a.exam from ExamAttempt a join a.exam.questions eq
            where a.userId = :userId and a.status <> com.examforge.entity.enums.AttemptStatus.IN_PROGRESS
              and eq.question.id = :questionId""")
    List<Exam> findFinishedExamsContainingQuestion(@Param("userId") Long userId,
                                                   @Param("questionId") Long questionId);

    long countByStatusNot(AttemptStatus status);

    Page<ExamAttempt> findByExamIdOrderByStartedAtDesc(Long examId, Pageable pageable);

    List<ExamAttempt> findByExamIdOrderByStartedAtAsc(Long examId);

    long countByExamId(Long examId);

    long countByExamIdAndStatus(Long examId, AttemptStatus status);

    /** [questionId, isCorrect, count] over the finished attempts of an exam (isCorrect null = left blank). */
    @Query("""
            select aa.questionId, aa.correct, count(aa) from AttemptAnswer aa
            where aa.attempt.exam.id = :examId and aa.attempt.status <> com.examforge.entity.enums.AttemptStatus.IN_PROGRESS
            group by aa.questionId, aa.correct""")
    List<Object[]> answerOutcomes(@Param("examId") Long examId);

    List<ExamAttempt> findByUserIdAndExamIdIn(Long userId, java.util.Collection<Long> examIds);
}
