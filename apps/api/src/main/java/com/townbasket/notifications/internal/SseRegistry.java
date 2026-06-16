package com.townbasket.notifications.internal;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory registry of live {@link SseEmitter}s (servlet stack). Fine for the
 * single-instance MVP deployment (ARCHITECTURE §3.8): per-order emitters drive
 * the customer tracking page, and a shared set of admin emitters drive the
 * order-queue feed. SSE clients auto-reconnect, so losing emitters on restart
 * is acceptable.
 */
@Component
public class SseRegistry {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    /** order id -> emitters watching that order's tracking page. */
    private final Map<Long, List<SseEmitter>> orderEmitters = new ConcurrentHashMap<>();

    /** emitters watching the admin order queue. */
    private final List<SseEmitter> adminEmitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribeToOrder(Long orderId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = orderEmitters.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>());
        register(list, emitter);
        return emitter;
    }

    public SseEmitter subscribeToAdmin() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        register(adminEmitters, emitter);
        return emitter;
    }

    void publishToOrder(Long orderId, String eventName, Object payload) {
        List<SseEmitter> list = orderEmitters.get(orderId);
        if (list != null) {
            send(list, eventName, payload);
        }
    }

    void publishToAdmin(String eventName, Object payload) {
        send(adminEmitters, eventName, payload);
    }

    private void register(List<SseEmitter> list, SseEmitter emitter) {
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(e -> list.remove(emitter));
    }

    private void send(List<SseEmitter> list, String eventName, Object payload) {
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException e) {
                // Client gone; drop it. Notification delivery is best-effort.
                emitter.completeWithError(e);
                list.remove(emitter);
            }
        }
    }
}
