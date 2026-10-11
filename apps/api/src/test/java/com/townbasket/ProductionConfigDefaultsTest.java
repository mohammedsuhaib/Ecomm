package com.townbasket;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.UrlResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Pins what the PRODUCTION {@code application.yml} does when no environment
 * variable overrides it.
 *
 * <p>Reads the main file directly rather than booting the app: the test source
 * set has its own {@code application.yml} that shadows the main one, so an
 * integration test never sees production's defaults. The environment is empty on
 * purpose, so a developer's own {@code SPRINGDOC_ENABLED} cannot change the result.
 */
class ProductionConfigDefaultsTest {

    private static MockEnvironment productionDefaults() throws IOException {
        URL main = null;
        for (URL url : Collections.list(
                ProductionConfigDefaultsTest.class.getClassLoader().getResources("application.yml"))) {
            if (!url.getPath().contains("test-classes")) {
                main = url;
            }
        }
        assertThat(main).as("main application.yml on the classpath").isNotNull();
        MockEnvironment env = new MockEnvironment();
        new YamlPropertySourceLoader()
                .load("production", new UrlResource(main))
                .forEach(env.getPropertySources()::addLast);
        return env;
    }

    @Test
    void apiDocsAreOffUnlessAnEnvironmentOptsIn() throws IOException {
        MockEnvironment env = productionDefaults();
        assertThat(env.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(env.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    @Test
    void onlyHealthAndInfoAreExposedByActuator() throws IOException {
        // Health is what the deploy check polls; metrics would publish per-endpoint
        // request counts to the internet (the API host is proxied wholesale).
        assertThat(productionDefaults().getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info");
    }
}
