package com.townbasket.identity.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for users. */
interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByPhone(String phone);

    Optional<UserEntity> findByEmail(String email);
}
