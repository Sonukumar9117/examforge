package com.examforge.repository;

import com.examforge.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Row lock used to serialise "start attempt" requests from the same student. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    long countByCreatedAtAfter(Instant since);

    long countByEmailVerifiedTrue();

    @Query("select count(u) from User u join u.roles r where r.name = :role")
    long countByRole(@Param("role") String role);

    @Query("""
            select distinct u from User u left join u.roles r
            where (:q is null or lower(u.username) like lower(concat('%', :q, '%'))
                              or lower(u.email) like lower(concat('%', :q, '%'))
                              or lower(u.fullName) like lower(concat('%', :q, '%')))
              and (:role is null or r.name = :role)
            """)
    Page<User> search(@Param("q") String q, @Param("role") String role, Pageable pageable);
}
