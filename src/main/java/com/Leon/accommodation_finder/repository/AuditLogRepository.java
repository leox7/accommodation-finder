package com.Leon.accommodation_finder.repository;

import com.Leon.accommodation_finder.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    boolean existsByEventId(String eventId);

    // every filter is optional, a null parameter means "do not filter on this".
    // from is inclusive, to is exclusive.
    @Query("SELECT a FROM AuditLog a WHERE " +
            "(:action IS NULL OR a.action = :action) " +
            "AND (:from IS NULL OR a.timestamp >= :from) " +
            "AND (:to IS NULL OR a.timestamp < :to)")
    Page<AuditLog> search(@Param("action") String action,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          Pageable pageable);
}
