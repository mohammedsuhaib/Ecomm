package com.townbasket.identity.internal;

import com.townbasket.identity.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for users. */
interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByPhone(String phone);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Active users of a role, name-sorted (e.g. delivery agents for dispatch). */
    List<UserEntity> findByRoleAndActiveTrueOrderByNameAsc(Role role);

    /** All users of a role incl. inactive, name-sorted (admin roster management). */
    List<UserEntity> findByRoleOrderByNameAsc(Role role);

    /** True if the id refers to an active user with the given role (dispatch validation). */
    boolean existsByIdAndRoleAndActiveTrue(Long id, Role role);

    /** Active AND on duty: the only riders who may receive a NEW assignment. */
    boolean existsByIdAndRoleAndActiveTrueAndOnDutyTrue(Long id, Role role);
}
