package org.wikipedia.ro.translit;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Loads language transliteration maps from Wikipedia-style Lua modules.
 *
 * <p>Use this class to parse module text directly or to fetch and parse a module from a URL.
 */
public final class LuaTransliterationMapLoader {

    private static final String USER_AGENT =
            "icu4j-wikipedia-translit/1.0 (https://github.com/andreistroe/icu4j-wikipedia-translit)";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private LuaTransliterationMapLoader() {
    }

    /**
     * Loads and parses a Lua module from a URL string.
     *
     * @param url raw-content module URL
     * @return language map keyed by language code
     * @throws IOException if fetching or parsing fails
     */
    public static Map<String, Map<String, TransliterationValue>> loadFromUrl(String url) throws IOException {
        return loadFromUrl(URI.create(url).toURL());
    }

    /**
     * Loads and parses a Lua module from a URL.
     *
     * @param url raw-content module URL
     * @return language map keyed by language code
     * @throws IOException if fetching or parsing fails
     */
    public static Map<String, Map<String, TransliterationValue>> loadFromUrl(URL url) throws IOException {
        String content = fetchContent(url);
        return parseFromLuaContent(content);
    }

    /**
     * Parses Lua module text into transliteration maps.
     *
     * @param luaContent module source text
     * @return language map keyed by language code
     */
    public static Map<String, Map<String, TransliterationValue>> parseFromLuaContent(String luaContent) {
        return new LuaDataParser(luaContent).parse();
    }

    private static String fetchContent(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        if (conn instanceof HttpURLConnection) {
            int status = ((HttpURLConnection) conn).getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " while fetching " + url);
            }
        }
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }


}
