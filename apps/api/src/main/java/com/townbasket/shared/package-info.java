/**
 * {@code shared} module — domain event types, money/quantity value types and
 * the common error model. Contains no business logic.
 *
 * <p>Declared {@link org.springframework.modulith.ApplicationModule.Type#OPEN OPEN}
 * so any other module may depend on its types directly; this is the one module
 * all others are allowed to reach into.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Shared Kernel"
)
package com.townbasket.shared;
