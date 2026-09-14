package com.townbasket.orders.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Module-internal repository for the per-financial-year invoice counter.
 *
 * <p>Allocation is two steps inside the caller's transaction: {@link
 * #ensureSeries(String)} to make sure the year's row exists, then {@link
 * #findAndLockByFy(String)} to take it under a row lock and increment. Both
 * steps are concurrency-safe on their own, so no retry loop is needed — see
 * each method for why.
 */
interface InvoiceSeriesRepository extends JpaRepository<InvoiceSeriesEntity, String> {

    /**
     * Create this financial year's counter row if it is not there yet, starting
     * at zero. {@code ON CONFLICT DO NOTHING} makes it idempotent and safe when
     * two invoices race on the first day of a new financial year: one inserts,
     * the other no-ops (blocking briefly on the same key), and neither sees an
     * exception that would poison the transaction.
     */
    @Modifying
    @Query(value = """
            INSERT INTO orders.invoice_series (fy, last_seq)
            VALUES (:fy, 0)
            ON CONFLICT (fy) DO NOTHING
            """, nativeQuery = true)
    void ensureSeries(@Param("fy") String fy);

    /**
     * Take this financial year's counter row under a write lock
     * ({@code SELECT ... FOR UPDATE}), so concurrent issues serialise and can
     * never hand out the same number twice. Call {@link #ensureSeries} first;
     * the row is then guaranteed present, including in this same transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InvoiceSeriesEntity s WHERE s.fy = :fy")
    Optional<InvoiceSeriesEntity> findAndLockByFy(@Param("fy") String fy);
}
