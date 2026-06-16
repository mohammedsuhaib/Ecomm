package com.townbasket.notifications.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for the notification log. */
interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, Long> {
}
