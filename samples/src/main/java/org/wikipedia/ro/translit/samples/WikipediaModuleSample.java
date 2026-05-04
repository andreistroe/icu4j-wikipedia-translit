package org.wikipedia.ro.translit.samples;

import org.wikipedia.ro.translit.IcuTransliterationService;

public final class WikipediaModuleSample {

    private static final String MODULE_URL =
            "https://ro.wikipedia.org/w/index.php?title=Modul:Transliteration/langdata&action=raw";

    private WikipediaModuleSample() {
    }

    public static void main(String[] args) throws Exception {
        IcuTransliterationService service = IcuTransliterationService.fromLuaUrl(MODULE_URL);
        String source = "Любовь и Љубав";

        System.out.println("Source: " + source);
        System.out.println("ru -> " + service.transliterate("Любовь", "ru"));
        System.out.println("sr -> " + service.transliterate("Љубав", "sr"));
    }
}