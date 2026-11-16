package com.townbasket.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.townbasket.AbstractIntegrationTest;
import com.townbasket.identity.AuthService;
import com.townbasket.identity.PhoneVerifyRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The customer counts on the admin dashboard, against a real Postgres. A customer
 * account is created by phone login, so signing in new numbers must move the
 * total and the "new in period" figure, and signing in again with a number that
 * already exists must not.
 */
class CustomerStatsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    AnalyticsService analytics;

    @Autowired
    AuthService authService;

    @Test
    void newCustomersAreCountedOnceAndOrderedNeverExceedsTotal() {
        CustomerStatsDto before = analytics.getCustomerStats(1L, 7);

        authService.phoneVerify(new PhoneVerifyRequest("dev:9888800001"));
        authService.phoneVerify(new PhoneVerifyRequest("dev:9888800002"));
        // The same person logging in again is not a new customer.
        authService.phoneVerify(new PhoneVerifyRequest("dev:9888800001"));

        CustomerStatsDto after = analytics.getCustomerStats(1L, 7);
        assertThat(after.total()).isEqualTo(before.total() + 2);
        assertThat(after.newInPeriod()).isEqualTo(before.newInPeriod() + 2);

        // Having ordered is a subset of being a customer, and repeat a subset of that.
        assertThat(after.withOrders()).isLessThanOrEqualTo(after.total());
        assertThat(after.repeatCustomers()).isLessThanOrEqualTo(after.withOrders());
    }
}
