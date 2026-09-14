package com.townbasket.serviceability.internal;

import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The cached read of the single active store row.
 *
 * <p>Its own bean, not a method on {@link ServiceabilityServiceImpl}, because
 * Spring's caching is proxy-based: a {@code @Cacheable} method called from
 * another method of the same object bypasses the proxy and the cache silently
 * does nothing. The service calls this collaborator, so the proxy is always in
 * the path.
 *
 * <p>Worth caching because this one row is read on nearly every request that
 * matters: twice per checkout (serviceability gate, then minimum-order-value),
 * on every {@code /serviceability/check} from the location picker, on every
 * storefront server render, and by each open tab's store-status poll. It changes
 * when a staff member edits store settings or closes the shop — a few times a
 * month.
 *
 * <p>The cache's TTL, and the advice ordering that lets a hit skip the
 * transaction entirely, live in {@code CacheConfig} in the application's root
 * package. The cache name is repeated as a literal rather than imported from it:
 * a module must not depend on the application config, and an unknown cache name
 * fails loudly on first call because the manager creates no cache on demand.
 */
@Component
class ActiveStoreCache {

    private final StoreRepository storeRepository;

    ActiveStoreCache(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    /**
     * The active store, or empty when none is configured. {@code sync = true} so
     * that a burst of concurrent misses (a cold start under load) runs one query
     * rather than one per thread.
     */
    @Cacheable(cacheNames = "activeStore", sync = true)
    @Transactional(readOnly = true)
    Optional<StoreSnapshot> get() {
        return storeRepository.findFirstByActiveTrueOrderByIdAsc().map(StoreSnapshot::of);
    }

    // Invalidation is NOT here. It is @CacheEvict on the write methods of
    // ServiceabilityServiceImpl, which are also the @Transactional boundary:
    // because caching advice is ordered outside transaction advice, the eviction
    // then runs after the write commits. An invalidate() method on this bean
    // would instead be called from inside the writer's transaction, leaving a
    // window in which a concurrent reader repopulates the cache with the
    // uncommitted row.
}
