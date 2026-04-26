package org.wikipedia.ro.translit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Value model for transliteration entries.
 *
 * <p>Entries are either plain text values, nested objects, or unsupported nodes.
 */
public sealed interface TransliterationValue permits TransliterationValue.Text, TransliterationValue.Object,
        TransliterationValue.Unsupported {

    boolean isText();

    boolean isObject();

    String asText();

    TransliterationValue get(String name);

    Map<String, TransliterationValue> fields();

    static TransliterationValue text(String text) {
        return new Text(text);
    }

    static TransliterationValue object(Map<String, TransliterationValue> fields) {
        return new Object(fields == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fields)));
    }

    static TransliterationValue unsupported() {
        return Unsupported.INSTANCE;
    }

    record Text(String value) implements TransliterationValue {
        @Override
        public boolean isText() {
            return true;
        }

        @Override
        public boolean isObject() {
            return false;
        }

        @Override
        public String asText() {
            return value;
        }

        @Override
        public TransliterationValue get(String name) {
            return null;
        }

        @Override
        public Map<String, TransliterationValue> fields() {
            return Collections.emptyMap();
        }
    }

    record Object(Map<String, TransliterationValue> fields) implements TransliterationValue {
        @Override
        public boolean isText() {
            return false;
        }

        @Override
        public boolean isObject() {
            return true;
        }

        @Override
        public String asText() {
            return null;
        }

        @Override
        public TransliterationValue get(String name) {
            return fields.get(name);
        }

        @Override
        public Map<String, TransliterationValue> fields() {
            return fields;
        }
    }

    record Unsupported() implements TransliterationValue {
        private static final Unsupported INSTANCE = new Unsupported();

        @Override
        public boolean isText() {
            return false;
        }

        @Override
        public boolean isObject() {
            return false;
        }

        @Override
        public String asText() {
            return null;
        }

        @Override
        public TransliterationValue get(String name) {
            return null;
        }

        @Override
        public Map<String, TransliterationValue> fields() {
            return Collections.emptyMap();
        }
    }
}
