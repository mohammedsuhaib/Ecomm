package com.townbasket.identity.internal;

import com.townbasket.identity.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for users. */
interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByPhone(String phone);

    Optional<UserEntity> findByEmail(String email);

    /** Active users of a role, name-sorted (e.g. delivery agents for dispatch). */
    List<UserEntity> findByRoleAndActiveTrueOrderByNameAsc(Role role);
}
