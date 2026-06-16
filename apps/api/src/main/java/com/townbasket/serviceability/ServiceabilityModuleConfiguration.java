package com.townbasket.serviceability;

import org.springframework.context.annotation.Configuration;

/**
 * Module configuration anchor for {@code serviceability}. The store entity,
 * repository and {@link ServiceabilityService} implementation live in the
 * non-exported {@code internal} sub-package; only DTOs,
 * {@link ServiceabilityService} and the controller form the public API.
 */
@Configuration
class ServiceabilityModuleConfiguration {
}
