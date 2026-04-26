package org.wikipedia.ro.translit;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.Test;

public class TransliterationMapLoaderTest {

    @Test
    public void parsesJsonFromString() throws IOException {
        String json = "{\n" +
                "  \"sr\": {\n" +
                "    \"\u0430\": \"a\",\n" +
                "    \"\u0459\": \"lj\"\n" +
                "  }\n" +
                "}\n";

        Map<String, Map<String, TransliterationValue>> maps = TransliterationMapLoader.parseFromJson(json);
        assertEquals("a", maps.get("sr").get("\u0430").asText());
        assertEquals("lj", maps.get("sr").get("\u0459").asText());
    }

    @Test
    public void loadsJsonFromFile() throws IOException {
        String json = "{\n" +
                "  \"ru\": {\n" +
                "    \"\u0430\": \"a\",\n" +
                "    \"\u044f\": { \"def\": \"ya\" }\n" +
                "  }\n" +
                "}\n";
        Path file = Files.createTempFile("translit", ".json");
        Files.writeString(file, json, StandardCharsets.UTF_8);

        Map<String, Map<String, TransliterationValue>> maps = TransliterationMapLoader.loadFromFile(file);
        assertEquals("a", maps.get("ru").get("\u0430").asText());
        assertEquals("ya", maps.get("ru").get("\u044f").get("def").asText());
    }
}
