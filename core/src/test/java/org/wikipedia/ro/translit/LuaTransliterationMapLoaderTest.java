package org.wikipedia.ro.translit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import org.junit.Test;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class LuaTransliterationMapLoaderTest {

    // Minimal inline Lua map sufficient for round-trip tests
    private static final String SIMPLE_LUA =
            "local map = {\n" +
            "  ['sr'] = {\n" +
            "    ['\u0430'] = 'a', ['\u0431'] = 'b', ['\u0449'] = '\u0161',\n" +
            "    ['\u0459'] = 'lj', ['\u045a'] = 'nj'\n" +
            "  }\n" +
            "}\n" +
            "return map";

    @Test
    public void parsesSimpleLuaContentIntoLanguageMap() {
        Map<String, Map<String, TransliterationValue>> maps =
                LuaTransliterationMapLoader.parseFromLuaContent(SIMPLE_LUA);

        assertTrue("sr language map should be present", maps.containsKey("sr"));
        Map<String, TransliterationValue> sr = maps.get("sr");
        assertEquals("a",  sr.get("\u0430").asText());   // а -> a
        assertEquals("b",  sr.get("\u0431").asText());   // б -> b
        assertEquals("lj", sr.get("\u0459").asText());   // љ -> lj
    }

    @Test
    public void parsesContextualRulesFromInlineLua() {
        String lua =
                "local map = {\n" +
                "  ['ru'] = {\n" +
                "    ['\u044f'] = { def = 'ea',\n" +         // я
                "      bh = { [''] = 'ia', [' '] = 'ia',\n" +
                "              ['\u0430'] = 'ia' }\n" +       // а -> ia
                "    }\n" +
                "  }\n" +
                "}\n" +
                "return map";

        Map<String, Map<String, TransliterationValue>> maps =
                LuaTransliterationMapLoader.parseFromLuaContent(lua);

        assertTrue(maps.containsKey("ru"));
        TransliterationValue ya = maps.get("ru").get("\u044f");
        assertNotNull(ya);
        assertTrue(ya.isObject());
        assertEquals("ea", ya.get("def").asText());
        assertEquals("ia", ya.get("bh").get("").asText());
        assertEquals("ia", ya.get("bh").get(" ").asText());
    }

    @Test
    public void transliteratesSerbianViaLuaLoadedMap() throws IOException {
        // Load the actual project Lua file using a file:// URL
        File luaFile = new File("translit-langdata.lua");
        if (!luaFile.exists()) {
            System.err.println("translit-langdata.lua not found; skipping file-based test");
            return;
        }
        IcuTransliterationService service =
                IcuTransliterationService.fromLuaUrl(luaFile.toURI().toString());

        assertTrue(service.isTransliterationSupported("sr"));
        assertEquals("ljubav", service.transliterate("\u0459\u0443\u0431\u0430\u0432", "sr"));
    }

    @Test
    public void transliteratesRussianContextualRuleViaLuaLoadedMap() throws IOException {
        File luaFile = new File("translit-langdata.lua");
        if (!luaFile.exists()) {
            System.err.println("translit-langdata.lua not found; skipping file-based test");
            return;
        }
        IcuTransliterationService service =
                IcuTransliterationService.fromLuaUrl(luaFile.toURI().toString());

        // маяк  -> ma + ia + k  (я after а uses bh[а]='ia')
        assertEquals("maiak", service.transliterate("\u043c\u0430\u044f\u043a", "ru"));
    }

    @Test
    public void parsesLuaDecimalByteEscapesAsUtf8() {
        // \226\140\166 in a Lua string is 3 decimal byte escapes: 0xE2 0x8C 0xA6
        // which is the UTF-8 encoding of U+2326 (⌦ ERASE TO THE RIGHT).
        // 'B\\226\\140\\166' in the Java source produces the Lua source: 'B\226\140\166'
        String lua =
                "local map = { ['\u03bc'] = { ['\u03c0'] = { def = 'B\\226\\140\\166' } } }\n" +
                "return map";
        Map<String, Map<String, TransliterationValue>> maps =
                LuaTransliterationMapLoader.parseFromLuaContent(lua);
        assertTrue(maps.containsKey("\u03bc"));
        TransliterationValue rule = maps.get("\u03bc").get("\u03c0");
        assertNotNull(rule);
        assertEquals("B\u2326", rule.get("def").asText());
    }

    @Test
    public void parsesLuaSkipWhitespaceEscape() {
        String lua =
                "local map = { ['a'] = { ['b'] = 'A\\z   B' } }\n" +
                "return map";
        Map<String, Map<String, TransliterationValue>> maps = LuaTransliterationMapLoader.parseFromLuaContent(lua);
        assertEquals("AB", maps.get("a").get("b").asText());
    }

    @Test
    public void parsesLuaSpecialEscapes() {
        String lua =
                "local map = { ['a'] = { ['b'] = '\\a\\b\\f\\v' } }\n" +
                "return map";
        Map<String, Map<String, TransliterationValue>> maps = LuaTransliterationMapLoader.parseFromLuaContent(lua);
        assertEquals("\u0007\b\f\u000b", maps.get("a").get("b").asText());
    }

    @Test
    public void parsesLuaDefaultEscapeFallback() {
        String lua =
                "local map = { ['a'] = { ['b'] = '\\q' } }\n" +
                "return map";
        Map<String, Map<String, TransliterationValue>> maps = LuaTransliterationMapLoader.parseFromLuaContent(lua);
        assertEquals("q", maps.get("a").get("b").asText());
    }

    @Test
    public void parsesCommentsAndIgnoresUnknownTopLevelText() {
        String lua =
                "xxx\n" +
                "-- line comment\n" +
                "local map = { ['a'] = { ['b'] = 'x' } }\n" +
                "--[[ block comment ]]\n" +
                "return map";
        Map<String, Map<String, TransliterationValue>> maps = LuaTransliterationMapLoader.parseFromLuaContent(lua);
        assertEquals("x", maps.get("a").get("b").asText());
    }

    @Test
    public void ignoresAliasAssignmentWhenRhsIdentIsReservedWord() {
        String lua =
                "local map = { ['a'] = { ['x'] = 'y' } }\n" +
                "foo['b'] = return['a']\n" +
                "return map";
        Map<String, Map<String, TransliterationValue>> maps = LuaTransliterationMapLoader.parseFromLuaContent(lua);
        assertEquals("y", maps.get("a").get("x").asText());
        assertTrue(!maps.containsKey("b"));
    }

    @Test
    public void loadFromUrlThrowsOnNon2xxHttpStatus() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            try {
                LuaTransliterationMapLoader.loadFromUrl(url);
                fail("Expected IOException for non-2xx status");
            } catch (IOException ex) {
                assertTrue(ex.getMessage().contains("HTTP 404"));
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void rejectsUnterminatedStringLiteral() {
        String lua = "local map = { ['a'] = 'foo\nreturn map";
        try {
            LuaTransliterationMapLoader.parseFromLuaContent(lua);
            fail("Expected runtime exception for unterminated string literal");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("Unterminated string literal"));
        }
    }

    @Test
    public void rejectsUnterminatedEscapeSequence() {
        String lua = "local map = { ['a'] = 'foo\\";
        try {
            LuaTransliterationMapLoader.parseFromLuaContent(lua);
            fail("Expected runtime exception for unterminated escape sequence");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("Unterminated escape sequence"));
        }
    }
}
