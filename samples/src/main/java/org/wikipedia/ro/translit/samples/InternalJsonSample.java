package org.wikipedia.ro.translit.samples;

import java.util.Map;

import org.wikipedia.ro.translit.IcuTransliterationService;
import org.wikipedia.ro.translit.TransliterationMapLoader;
import org.wikipedia.ro.translit.TransliterationValue;

public final class InternalJsonSample {

    private InternalJsonSample() {
    }

    public static void main(String[] args) {
        Map<String, Map<String, TransliterationValue>> maps =
                TransliterationMapLoader.loadFromResource("/sample-langdata.json");
        IcuTransliterationService service = new IcuTransliterationService(maps);
        String source = "Љубав и Београд";

        System.out.println("Source: " + source);
        System.out.println("sr -> " + service.transliterate(source, "sr"));
        System.out.println("sr-Cyrl supported -> " + service.isTransliterationSupported("sr-Cyrl"));
    }
}