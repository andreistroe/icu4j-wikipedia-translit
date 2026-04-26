package org.wikipedia.ro.translit;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ibm.icu.text.Transliterator;
import com.ibm.icu.util.ULocale;

/**
 * Service API for transliterating text by language code.
 *
 * <p>Create an instance from Lua content, from a Lua URL, or from preloaded maps, then call
 * {@link #transliterate(String, String)} for each input string.
 */
public final class IcuTransliterationService {

    private final Map<String, Transliterator> transliterators;

    public IcuTransliterationService(Map<String, Map<String, TransliterationValue>> transliterationMaps) {
        Map<String, Transliterator> map = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, TransliterationValue>> entry : transliterationMaps.entrySet()) {
            String langCode = entry.getKey();
            ULocale locale = ULocale.forLanguageTag(langCode);
            String rules = IcuRuleBuilder.buildRules(entry.getValue(), locale);
            Transliterator t = Transliterator.createFromRules(
                    "Wikipedia-" + langCode, rules, Transliterator.FORWARD);
            map.put(langCode, t);
        }
        this.transliterators = Collections.unmodifiableMap(map);
    }

    /**
     * Creates a service from Lua module text.
     *
     * @param luaContent full text of a Wikipedia-style transliteration module
     * @return service ready to transliterate texts for all loaded language codes
     */
    public static IcuTransliterationService fromLuaContent(String luaContent) {
        return new IcuTransliterationService(
                LuaTransliterationMapLoader.parseFromLuaContent(luaContent));
    }

    /**
     * Creates a service by loading a Lua module from a URL.
     *
     * @param url raw-content URL of a Wikipedia transliteration module
     * @return service ready to transliterate texts for all loaded language codes
     * @throws IOException if fetching or parsing the module fails
     */
    public static IcuTransliterationService fromLuaUrl(String url) throws IOException {
        return new IcuTransliterationService(LuaTransliterationMapLoader.loadFromUrl(url));
    }

    public boolean isTransliterationSupported(String langCode) {
        return transliterators.containsKey(langCode);
    }

    /**
     * Transliterates text with the map registered for the given language code.
     *
     * @param text source text; null returns null
     * @param langCode language code key present in the loaded maps
     * @return transliterated text
     * @throws IllegalArgumentException if no transliterator is available for langCode
     */
    public String transliterate(String text, String langCode) {
        if (text == null) {
            return null;
        }
        Transliterator t = transliterators.get(langCode);
        if (t == null) {
            throw new IllegalArgumentException(
                    "Transliteration from language " + langCode + " not supported");
        }
        // Pad with spaces so that word-boundary context rules (originally keyed on '' in Lua)
        // correctly fire at the very start and end of the input string.
        String padded = " " + text + " ";
        String result = t.transliterate(padded);
        // The padding spaces are not Cyrillic, so they pass through unchanged.
        return result.substring(1, result.length() - 1);
    }
}

