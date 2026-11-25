package com.examforge.repository;

import com.examforge.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHashAndType(String tokenHash, AuthToken.Type type);

    /** Invalidate older unused tokens of the same type when a new one is issued. */
    @Modifying
    @Query("update AuthToken t set t.usedAt = :now where t.user.id = :userId and t.type = :type and t.usedAt is null")
    int invalidateAll(@Param("userId") Long userId, @Param("type") AuthToken.Type type, @Param("now") Instant now);

    @Modifying
    @Query("delete from AuthToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
