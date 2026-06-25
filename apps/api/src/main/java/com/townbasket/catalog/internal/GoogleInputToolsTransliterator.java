package com.townbasket.catalog.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.ProxySelector;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link ProductNameTransliterator} backed by Google Input Tools (free, no API
 * key). Active only when transliteration is enabled. The response shape is
 * {@code ["SUCCESS",[[ input, [candidate, ...], ... ]]]}; we take the top
 * candidate. Any failure (network, non-200, unexpected body) yields
 * {@link Optional#empty()} so a product simply keeps its English name and is
 * retried on a later sweep.
 *
 * <p>The endpoint is unofficial — fine for the occasional, cached backfill it is
 * used for here. Swap this adapter for a keyed/SLA-backed provider (e.g. Azure
 * Translator transliterate) or a self-hosted model behind the same port for a
 * higher-volume production setup.
 */
@Component
@ConditionalOnProperty(prefix = "townbasket.catalog.transliteration", name = "enabled", havingValue = "true")
class GoogleInputToolsTransliterator implements ProductNameTransliterator {

    private static final Logger log = LoggerFactory.getLogger(GoogleInputToolsTransliterator.class);

    private final TransliterationProperties props;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            // Honour a configured system proxy (-Dhttps.proxyHost/Port) for
            // egress-controlled deployments; direct otherwise.
            .proxy(ProxySelector.getDefault())
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    GoogleInputToolsTransliterator(TransliterationProperties props) {
        this.props = props;
    }

    @Override
    public Optional<String> toKannada(String englishName) {
        if (englishName == null || englishName.isBlank()) {
            return Optional.empty();
        }
        try {
            String url = props.endpoint()
                    + "?text=" + URLEncoder.encode(englishName, StandardCharsets.UTF_8)
                    + "&itc=" + props.itc()
                    + "&num=1&cp=0&cs=1&ie=utf-8&oe=utf-8";
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(8))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray() || root.size() < 2 || !"SUCCESS".equals(root.get(0).asText())) {
                return Optional.empty();
            }
            // root[1][0][1] is the list of candidate transliterations.
            JsonNode candidates = root.path(1).path(0).path(1);
            if (!candidates.isArray() || candidates.isEmpty()) {
                return Optional.empty();
            }
            String transliterated = candidates.get(0).asText();
            return transliterated.isBlank() ? Optional.empty() : Optional.of(transliterated);
        } catch (Exception e) {
            // Per-item at debug to avoid flooding the log when the endpoint is
            // down; the backfill job logs one summary line per sweep.
            log.debug("Transliteration failed for \"{}\": {}", englishName, e.toString());
            return Optional.empty();
        }
    }
}
