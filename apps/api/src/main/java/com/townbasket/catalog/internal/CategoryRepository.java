package com.townbasket.catalog.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for categories. */
interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    List<CategoryEntity> findAllByOrderBySortOrderAscNameAsc();

    /** Unique-slug lookup used by the admin write path (create + slug-collision suffixing). */
    Optional<CategoryEntity> findBySlug(String slug);
}
