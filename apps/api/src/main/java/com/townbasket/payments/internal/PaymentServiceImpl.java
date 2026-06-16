package com.townbasket.payments.internal;

import com.townbasket.payments.PaymentMethod;
import com.townbasket.payments.PaymentResult;
import com.townbasket.payments.PaymentService;
import com.townbasket.payments.PaymentStatus;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module-internal implementation of {@link PaymentService}. Dispatches to the
 * registered {@link PaymentProvider} for the chosen method, records a
 * {@code payments.payments} row, and returns the outcome to the orders checkout.
 */
@Service
@Transactional
class PaymentServiceImpl implements PaymentService {

    private final Map<PaymentMethod, PaymentProvider> providers = new EnumMap<>(PaymentMethod.class);
    private final PaymentRepository payments;

    PaymentServiceImpl(List<PaymentProvider> providerBeans, PaymentRepository payments) {
        // For UPI the @Primary FakeProvider wins; PaytmProvider is not a bean (M5).
        for (PaymentProvider p : providerBeans) {
            providers.putIfAbsent(p.method(), p);
        }
        this.payments = payments;
    }

    @Override
    public PaymentResult charge(Long orderId, PaymentMethod method, BigDecimal amount) {
        PaymentProvider provider = providers.get(method);
        if (provider == null) {
            throw new IllegalArgumentException("No payment provider for method " + method);
        }
        PaymentProvider.Charge outcome = provider.charge(orderId, amount);
        PaymentStatus status = outcome.status();
        PaymentEntity saved = payments.save(new PaymentEntity(
                orderId, method.name(), status.name(), amount, outcome.reference()));
        return new PaymentResult(saved.getId(), method, status, outcome.reference());
    }
}
