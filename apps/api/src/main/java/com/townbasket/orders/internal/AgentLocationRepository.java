package com.townbasket.orders.internal;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Module-internal Spring Data repository for riders' current positions. */
interface AgentLocationRepository extends JpaRepository<AgentLocationEntity, Long> {

    /**
     * Record the rider's latest fix, creating the row on their first ping and
     * overwriting it thereafter.
     *
     * <p>One statement on purpose. A phone reports every few seconds over a
     * mobile connection, so two pings can easily be in flight together; a
     * load-then-save would race itself into a duplicate-key error on the very
     * first pair, whereas {@code ON CONFLICT} lets Postgres serialise them and
     * the later write simply wins.
     *
     * @param recordedAt the STORE clock at acceptance — never the phone's own
     *                   timestamp, which is what would let a device with a wrong
     *                   clock pass off an old fix as current
     */
    // A @Modifying query needs a read-write transaction of its own: the
    // repository default is read-only, under which Postgres refuses the INSERT.
    // Inside OrderServiceImpl this simply joins the service transaction.
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO orders.agent_locations (agent_id, lat, lng, accuracy_m, recorded_at)
            VALUES (:agentId, :lat, :lng, :accuracy, :recordedAt)
            ON CONFLICT (agent_id) DO UPDATE
               SET lat = EXCLUDED.lat,
                   lng = EXCLUDED.lng,
                   accuracy_m = EXCLUDED.accuracy_m,
                   recorded_at = EXCLUDED.recorded_at
            """, nativeQuery = true)
    void upsert(@Param("agentId") Long agentId,
                @Param("lat") double lat,
                @Param("lng") double lng,
                @Param("accuracy") Double accuracy,
                @Param("recordedAt") Instant recordedAt);
}
