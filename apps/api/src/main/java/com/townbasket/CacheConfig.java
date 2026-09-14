package com.townbasket;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * In-process caching for the two reads that dominate the request mix.
 *
 * <p><strong>Why Caffeine and not Redis.</strong> The app is one JVM on one
 * droplet serving a single supermarket (ARCHITECTURE §7). A cache server would
 * add a process to operate, a network hop, and a serialization format, all to
 * replace a {@code ConcurrentHashMap} lookup that costs nanoseconds. Redis earns
 * its place when several app instances must share cached state; when that day
 * comes, the switch is a {@code CacheManager} bean — every {@code @Cacheable}
 * stays as written. Until then this is the right size of solution.
 *
 * <p><strong>What is cached.</strong> Only data that is read on nearly every
 * request and written by a human a few times a month:
 * <ul>
 *   <li>{@value #ACTIVE_STORE} — the single active store row. It is read twice per
 *       checkout, on every {@code /serviceability/check}, on every storefront SSR
 *       render, and by each open tab's store-status poll. The cached value is an
 *       immutable snapshot of the row, never the JPA entity, and never the
 *       {@code StoreDto}: "are we open" is derived from the clock on every call,
 *       so caching cannot freeze the open/closed flag.</li>
 *   <li>{@value #CATEGORIES} — the category nav, on every storefront page.</li>
 * </ul>
 * Deliberately NOT cached: product listings and search (they carry live stock
 * counts, and their cost is already an index scan after V6_10/V3_x), carts,
 * orders and anything user-scoped.
 *
 * <p><strong>Correctness.</strong> Both caches are evicted explicitly by the admin
 * write paths that change them, so a staff edit shows up on the next request
 * rather than after a timeout. The short TTLs are a safety net for changes made
 * out of band — the seed script, a {@code psql} session, a restored backup — not
 * the primary invalidation mechanism.
 *
 * <p>Caching advice is ordered OUTSIDE the transaction advice
 * ({@code @EnableCaching(order = HIGHEST_PRECEDENCE)}, against transactions'
 * default {@code LOWEST_PRECEDENCE}). Two consequences, both wanted: a cache hit
 * returns without opening a database transaction at all — so these reads stop
 * borrowing a Hikari connection — and {@code @CacheEvict} runs after the writing
 * transaction has committed, so a concurrent reader cannot repopulate the cache
 * with the pre-commit row.
 */
@Configuration
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
class CacheConfig {

    /** The active store row (one entry, keyed by the no-arg call). */
    static final String ACTIVE_STORE = "activeStore";

    /** The storefront category navigation (one entry). */
    static final String CATEGORIES = "categories";

    /**
     * Explicit caches with per-cache expiry. {@link SimpleCacheManager} rather
     * than {@code CaffeineCacheManager} so that a typo in a cache name fails
     * loudly (no cache is created on demand) and each cache's expiry is stated
     * next to it.
     */
    @Bean
    SimpleCacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                // 60s: the store row backs the open/closed banner, so an
                // out-of-band change should surface within about a minute.
                caffeine(ACTIVE_STORE, Duration.ofSeconds(60), 1),
                // 5 min: categories are edited a few times a year.
                caffeine(CATEGORIES, Duration.ofMinutes(5), 1)));
        return manager;
    }

    private static CaffeineCache caffeine(String name, Duration ttl, long maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .build());
    }
}
