package com.townbasket;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * The heart of milestone M1: Spring Modulith verifies that the modules under
 * {@code com.townbasket} respect their boundaries.
 *
 * <p>{@link ApplicationModules#verify()} fails the build if any module reaches
 * into another module's internals (anything other than the module's API
 * package or the OPEN {@code shared} module) or if a cyclic dependency is
 * introduced. This is the boundary enforcement CI relies on.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(TownBasketApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    /**
     * Renders the module canvas / PlantUML component diagrams under
     * {@code build/spring-modulith-docs}. Useful living documentation; not a
     * boundary gate, but cheap to keep green.
     */
    @Test
    void writesDocumentation() {
        new Documenter(modules)
                .writeDocumentation()
                .writeIndividualModulesAsPlantUml();
    }
}
