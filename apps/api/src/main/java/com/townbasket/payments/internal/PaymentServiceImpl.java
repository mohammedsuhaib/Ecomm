package com.townbasket.payments.internal;

import com.townbasket.payments.PaymentMethod;
import com.townbasket.payments.PaymentResult;
import com.townbasket.payments.PaymentService;
import com.townbasket.payments.PaymentStatus;
import com.townbasket.shared.BusinessRuleException;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module-internal implementation of {@link PaymentService}. Dispatches to the
 * registered {@link PaymentProvider} for the chosen method, records a
 * {@code payments.payments} row, and returns the outcome to the orders checkout.
 */
@Service
@Transactional
@EnableConfigurationProperties(PaymentProperties.class)
class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final Map<PaymentMethod, PaymentProvider> providers = new EnumMap<>(PaymentMethod.class);
    private final PaymentRepository payments;
    private final PaymentProperties properties;

    PaymentServiceImpl(List<PaymentProvider> providerBeans, PaymentRepository payments,
                       PaymentProperties properties) {
        // For UPI the @Primary FakeProvider wins; a live provider is not a bean yet.
        for (PaymentProvider p : providerBeans) {
            providers.putIfAbsent(p.method(), p);
        }
        this.payments = payments;
        this.properties = properties;
    }

    @Override
    public List<PaymentMethod> enabledMethods() {
        return properties.upiEnabled()
                ? List.of(PaymentMethod.COD, PaymentMethod.UPI)
                : List.of(PaymentMethod.COD);
    }

    @Override
    public void requireEnabled(PaymentMethod method) {
        if (method == null || !enabledMethods().contains(method)) {
            throw new BusinessRuleException(
                    "Paying online in advance isn't available yet. Please choose Pay on Delivery.");
        }
    }

    @Override
    public PaymentResult charge(Long orderId, PaymentMethod method, BigDecimal amount) {
        // Defence in depth: checkout already refuses a disabled method, but a
        // fake provider must never be able to mark an order PAID by accident.
        requireEnabled(method);
        PaymentProvider provider = providers.get(method);
        if (provider == null) {
            throw new IllegalArgumentException("No payment provider for method " + method);
        }
        PaymentProvider.Charge outcome = provider.charge(orderId, amount);
        PaymentStatus status = outcome.status();
        payments.save(new PaymentEntity(
                orderId, method.name(), status.name(), amount, outcome.reference()));
        // Money: always worth a line, and a failure is worth a loud one — it
        // rolls the whole checkout back (see OrderServiceImpl#placeOrder), so
        // the customer sees an order that never appeared and this is the only
        // record of why.
        if (status == PaymentStatus.FAILED) {
            log.warn("Payment FAILED for order {}: {} {} (ref {})",
                    orderId, method, amount, outcome.reference());
        } else {
            log.info("Payment {} for order {}: {} {} (ref {})",
                    status, orderId, method, amount, outcome.reference());
        }
        return new PaymentResult(method, status, outcome.reference());
    }
}
