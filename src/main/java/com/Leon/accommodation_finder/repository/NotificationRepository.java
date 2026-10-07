package com.Leon.accommodation_finder.repository;

import com.Leon.accommodation_finder.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByEventId(String eventId);

    @Query("SELECT n FROM Notification n WHERE LOWER(n.landlord.email) = LOWER(:email)")
    Page<Notification> findMyNotifications(@Param("email") String email, Pageable pageable);
}
