package org.wikipedia.ro.translit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Loads transliteration maps from JSON inputs.
 *
 * <p>This module is optional and isolates JSON/Jackson support from the core module.
 */
public final class TransliterationMapLoader {

    private TransliterationMapLoader() {
    }

    /**
     * Parses transliteration maps from JSON text.
     *
     * @param json JSON content
     * @return language map keyed by language code
     * @throws IOException if parsing fails
     */
    public static Map<String, Map<String, TransliterationValue>> parseFromJson(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        return normalize(root);
    }

    /**
     * Loads transliteration maps from a classpath resource.
     *
     * @param resourcePath classpath path, for example /langdata.json
     * @return language map keyed by language code
     */
    public static Map<String, Map<String, TransliterationValue>> loadFromResource(String resourcePath) {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = TransliterationMapLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Unable to find transliteration resource: " + resourcePath);
            }
            JsonNode root = mapper.readTree(in);
            return normalize(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to parse transliteration resource: " + resourcePath, ex);
        }
    }

    /**
     * Loads transliteration maps from a JSON file.
     *
     * @param path JSON file path
     * @return language map keyed by language code
     * @throws IOException if reading or parsing fails
     */
    public static Map<String, Map<String, TransliterationValue>> loadFromFile(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return parseFromJson(json);
    }

    private static Map<String, Map<String, TransliterationValue>> normalize(JsonNode root) {
        if (root == null || !root.isObject()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, Map<String, TransliterationValue>> result = new LinkedHashMap<>();
        root.fields().forEachRemaining(entry -> {
            if (entry.getValue().isObject()) {
                result.put(entry.getKey(), Collections.unmodifiableMap(convertMap(entry.getValue())));
            }
        });
        if (result.containsKey("sr") && !result.containsKey("sr-Cyrl")) {
            result.put("sr-Cyrl", result.get("sr"));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, TransliterationValue> convertMap(JsonNode node) {
        LinkedHashMap<String, TransliterationValue> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), convertValue(entry.getValue())));
        return result;
    }

    private static TransliterationValue convertValue(JsonNode node) {
        if (node.isTextual()) {
            return TransliterationValue.text(node.asText());
        }
        if (node.isObject()) {
            return TransliterationValue.object(convertMap(node));
        }
        return TransliterationValue.unsupported();
    }
}
