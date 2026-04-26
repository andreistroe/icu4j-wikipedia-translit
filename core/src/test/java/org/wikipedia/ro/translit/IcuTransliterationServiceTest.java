package org.wikipedia.ro.translit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.BeforeClass;
import org.junit.Test;

public class IcuTransliterationServiceTest {

    /**
     * Minimal inline Lua covering the characters needed by the five tests below.
     * Serbian: а б в у љ  (for "ljubav" / "Ljubav")
     * Russian: м а к з ы я with context rules  (for "iazîk" and "maiak")
     * Alias:   sr-Cyrl -> sr
     */
    private static final String TEST_LUA =
            "local map = {\n" +
            "  ['sr'] = {\n" +
            "    ['\u0430'] = 'a', ['\u0431'] = 'b', ['\u0432'] = 'v',\n" +
            "    ['\u0443'] = 'u', ['\u0459'] = 'lj'\n" +
            "  },\n" +
            "  ['ru'] = {\n" +
            "    ['\u043c'] = 'm', ['\u0430'] = 'a', ['\u043a'] = 'k',\n" +
            "    ['\u0437'] = 'z', ['\u044b'] = '\u00ee',\n" +
            "    ['\u044f'] = { def = 'ea',\n" +
            "      bh = { [''] = 'ia', [' '] = 'ia', ['\u0430'] = 'ia' } }\n" +
            "  }\n" +
            "}\n" +
            "map['sr-Cyrl'] = map['sr']\n" +
            "return map";

    private static IcuTransliterationService service;

    @BeforeClass
    public static void setUp() {
        service = IcuTransliterationService.fromLuaContent(TEST_LUA);
    }

    @Test
    public void supportsConfiguredLanguage() {
        assertTrue(service.isTransliterationSupported("sr"));
        assertTrue(service.isTransliterationSupported("sr-Cyrl"));
    }

    @Test
    public void transliteratesSerbianSimpleSequence() {
        // љубав -> ljubav
        assertEquals("ljubav", service.transliterate("\u0459\u0443\u0431\u0430\u0432", "sr"));
    }

    @Test
    public void transliteratesUppercaseCharactersUsingIcuCaseLogic() {
        // Љубав -> Ljubav
        assertEquals("Ljubav", service.transliterate("\u0409\u0443\u0431\u0430\u0432", "sr"));
    }

    @Test
    public void transliteratesRussianContextualRuleForYaAtWordStart() {
        // язык -> iazîk  (я at word start uses bh[' ']='ia')
        assertEquals("iaz\u00eek", service.transliterate("\u044f\u0437\u044b\u043a", "ru"));
    }

    @Test
    public void transliteratesRussianContextualRuleForYaAfterVowel() {
        // маяк -> maiak  (я after а uses bh['а']='ia')
        assertEquals("maiak", service.transliterate("\u043c\u0430\u044f\u043a", "ru"));
    }

    @Test
    public void transliterateNullReturnsNull() {
        assertNull(service.transliterate(null, "sr"));
    }

    @Test
    public void unsupportedLanguageThrows() {
        try {
            service.transliterate("test", "xx");
            fail("Expected IllegalArgumentException for unsupported language");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Transliteration from language xx not supported"));
        }
    }
}
