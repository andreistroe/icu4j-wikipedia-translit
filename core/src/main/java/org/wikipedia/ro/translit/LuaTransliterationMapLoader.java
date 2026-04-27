package org.wikipedia.ro.translit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    private static final class LuaDataParser {

        private final String src;
        private int pos;

        LuaDataParser(String src) {
            this.src = src;
            this.pos = 0;
        }

        Map<String, Map<String, TransliterationValue>> parse() {
            Map<String, Map<String, TransliterationValue>> result = new LinkedHashMap<>();

            while (pos < src.length()) {
                skipSpace();
                if (pos >= src.length() || peekKeyword("return")) {
                    break;
                }

                if (peekKeyword("local")) {
                    consumeKeyword("local");
                    skipSpace();
                    parseIdent();
                    skipSpace();
                    expect('=');
                    skipSpace();
                    TransliterationValue topNode = parseTable();
                    if (topNode.isObject()) {
                        topNode.fields().forEach((key, value) -> {
                            if (value.isObject()) {
                                result.put(key, Collections.unmodifiableMap(value.fields()));
                            }
                        });
                    }
                    skipOptional(';');
                    continue;
                }

                int savedPos = pos;
                String lhsIdent = tryParseIdent();
                if (lhsIdent != null) {
                    skipSpace();
                    if (pos < src.length() && src.charAt(pos) == '[') {
                        pos++;
                        skipSpace();
                        String lhsKey = parseString();
                        skipSpace();
                        expect(']');
                        skipSpace();
                        expect('=');
                        skipSpace();
                        String rhsIdent = tryParseIdent();
                        // Ignore aliases whose RHS uses Lua keywords, e.g. return['x'].
                        if (rhsIdent != null && !isReservedWord(rhsIdent)
                                && pos < src.length() && src.charAt(pos) == '[') {
                            pos++;
                            skipSpace();
                            String rhsKey = parseString();
                            skipSpace();
                            expect(']');
                            if (!result.containsKey(lhsKey) && result.containsKey(rhsKey)) {
                                result.put(lhsKey, result.get(rhsKey));
                            }
                            skipOptional(';');
                            continue;
                        }
                    }
                }
                pos = savedPos;
                pos++;
            }

            return Collections.unmodifiableMap(result);
        }

        TransliterationValue parseTable() {
            expect('{');
            Map<String, TransliterationValue> node = new LinkedHashMap<>();

            while (true) {
                skipSpace();
                if (pos >= src.length()) {
                    throw new LuaParseException("Unexpected end of input inside table", pos);
                }
                char c = src.charAt(pos);
                if (c == '}') {
                    pos++;
                    break;
                }

                String key;
                if (c == '[') {
                    pos++;
                    skipSpace();
                    key = parseString();
                    skipSpace();
                    expect(']');
                } else if (isIdentStart(c)) {
                    key = parseIdent();
                } else {
                    throw new LuaParseException(
                            "Expected table field key at position " + pos + " ('" + c + "')", pos);
                }

                skipSpace();
                expect('=');
                skipSpace();

                TransliterationValue value;
                if (pos < src.length() && src.charAt(pos) == '{') {
                    value = parseTable();
                } else {
                    value = TransliterationValue.text(parseString());
                }
                node.put(key, value);

                skipSpace();
                if (pos < src.length() && (src.charAt(pos) == ',' || src.charAt(pos) == ';')) {
                    pos++;
                }
            }

            return TransliterationValue.object(node);
        }

        String parseString() {
            skipSpace();
            if (pos >= src.length()) {
                throw new LuaParseException("Expected string literal at end of input", pos);
            }
            char q = src.charAt(pos);
            if (q != '\'' && q != '\"') {
                throw new LuaParseException(
                        "Expected string literal at position " + pos + " ('" + q + "')", pos);
            }
            pos++;

            StringBuilder result = new StringBuilder();
            ByteArrayOutputStream pendingBytes = new ByteArrayOutputStream();

            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == q) {
                    pos++;
                    flushUtf8(pendingBytes, result);
                    return result.toString();
                }
                if (c == '\\') {
                    pos++;
                    if (pos >= src.length()) {
                        throw new LuaParseException("Unterminated escape sequence", pos);
                    }
                    char esc = src.charAt(pos);
                    if (esc >= '0' && esc <= '9') {
                        // Lua decimal escapes are bytes; consecutive escapes are collected
                        // and decoded as UTF-8 to preserve multi-byte characters.
                        int value = esc - '0';
                        pos++;
                        if (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                            value = value * 10 + (src.charAt(pos) - '0');
                            pos++;
                            if (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                                value = value * 10 + (src.charAt(pos) - '0');
                                pos++;
                            }
                        }
                        pendingBytes.write(value & 0xFF);
                        continue;
                    }
                    flushUtf8(pendingBytes, result);
                    switch (esc) {
                        case 'n': result.append('\n'); break;
                        case 't': result.append('\t'); break;
                        case 'r': result.append('\r'); break;
                        case '\\': result.append('\\'); break;
                        case '\'': result.append('\''); break;
                        case '\"': result.append('\"'); break;
                        case 'a': result.appendCodePoint(7); break;
                        case 'b': result.append('\b'); break;
                        case 'f': result.append('\f'); break;
                        case 'v': result.appendCodePoint(11); break;
                        case 'z':
                            pos++;
                            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                                pos++;
                            }
                            continue;
                        default:
                            result.append(esc);
                            break;
                    }
                    pos++;
                } else {
                    flushUtf8(pendingBytes, result);
                    result.append(c);
                    pos++;
                }
            }

            throw new LuaParseException("Unterminated string literal", pos);
        }
        private void flushUtf8(ByteArrayOutputStream baos, StringBuilder sb) {
            if (baos.size() > 0) {
                sb.append(new String(baos.toByteArray(), StandardCharsets.UTF_8));
                baos.reset();
            }
        }

        private void skipSpace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (Character.isWhitespace(c)) {
                    pos++;
                    continue;
                }
                if (c == '-' && pos + 1 < src.length() && src.charAt(pos + 1) == '-') {
                    pos += 2;
                    if (pos + 1 < src.length() && src.charAt(pos) == '[' && src.charAt(pos + 1) == '[') {
                        pos += 2;
                        while (pos + 1 < src.length()) {
                            if (src.charAt(pos) == ']' && src.charAt(pos + 1) == ']') {
                                pos += 2;
                                break;
                            }
                            pos++;
                        }
                    } else {
                        while (pos < src.length() && src.charAt(pos) != '\n') {
                            pos++;
                        }
                    }
                    continue;
                }
                break;
            }
        }

        private boolean peekKeyword(String keyword) {
            if (pos + keyword.length() > src.length()) {
                return false;
            }
            for (int i = 0; i < keyword.length(); i++) {
                if (src.charAt(pos + i) != keyword.charAt(i)) {
                    return false;
                }
            }
            if (pos + keyword.length() < src.length()) {
                char next = src.charAt(pos + keyword.length());
                if (isIdentPart(next)) {
                    return false;
                }
            }
            return true;
        }

        private void consumeKeyword(String keyword) {
            if (!peekKeyword(keyword)) {
                throw new LuaParseException("Expected keyword '" + keyword + "' at position " + pos, pos);
            }
            pos += keyword.length();
        }

        private String parseIdent() {
            int start = pos;
            if (pos < src.length() && isIdentStart(src.charAt(pos))) {
                pos++;
                while (pos < src.length() && isIdentPart(src.charAt(pos))) {
                    pos++;
                }
                return src.substring(start, pos);
            }
            throw new LuaParseException("Expected identifier at position " + pos, pos);
        }

        private String tryParseIdent() {
            int start = pos;
            if (pos < src.length() && isIdentStart(src.charAt(pos))) {
                pos++;
                while (pos < src.length() && isIdentPart(src.charAt(pos))) {
                    pos++;
                }
                return src.substring(start, pos);
            }
            return null;
        }

        private boolean isIdentStart(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
        }

        private boolean isIdentPart(char c) {
            return isIdentStart(c) || (c >= '0' && c <= '9');
        }

        private boolean isReservedWord(String ident) {
            return "return".equals(ident) || "local".equals(ident);
        }

        private void expect(char expected) {
            if (pos >= src.length() || src.charAt(pos) != expected) {
                throw new LuaParseException("Expected '" + expected + "' at position " + pos, pos);
            }
            pos++;
        }

        private void skipOptional(char c) {
            if (pos < src.length() && src.charAt(pos) == c) {
                pos++;
            }
        }

        static final class LuaParseException extends RuntimeException {
            LuaParseException(String message, int pos) {
                super(message + " (position " + pos + ")");
            }
        }
    }
}
