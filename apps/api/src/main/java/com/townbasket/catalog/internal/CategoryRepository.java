package com.townbasket.catalog.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Module-internal Spring Data repository for categories. */
interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    List<CategoryEntity> findAllByOrderBySortOrderAscNameAsc();
}
