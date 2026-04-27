package org.wikipedia.ro.translit;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses Lua data structures from Wikipedia-style Lua modules.
 *
 * <p>This parser handles Lua tables, strings with escapes, identifiers, and comments.
 * It is designed to extract transliteration data but can be reused for other Lua-based parsing needs.
 */
public final class LuaDataParser {

    private final String src;
    private int pos;

    /**
     * Creates a parser for the given Lua source text.
     *
     * @param src the Lua source to parse
     */
    public LuaDataParser(String src) {
        this.src = src;
        this.pos = 0;
    }

    /**
     * Parses the Lua source into transliteration maps.
     *
     * <p>Expects a Lua module with local table assignments and aliases.
     * Returns a map of language codes to their transliteration mappings.
     *
     * @return language map keyed by language code
     */
    public Map<String, Map<String, TransliterationValue>> parse() {
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

    /**
     * Parses a Lua table from the current position.
     *
     * @return the parsed table as a TransliterationValue
     */
    public TransliterationValue parseTable() {
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

    /**
     * Parses a Lua string literal from the current position.
     *
     * <p>Handles standard escapes including decimal byte escapes that are decoded as UTF-8.
     *
     * @return the parsed string
     */
    public String parseString() {
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

    /**
     * Exception thrown when Lua parsing fails.
     */
    public static final class LuaParseException extends RuntimeException {
        /**
         * Creates a parse exception with a message and position.
         *
         * @param message error description
         * @param pos position in the source where the error occurred
         */
        public LuaParseException(String message, int pos) {
            super(message + " (position " + pos + ")");
        }
    }
}