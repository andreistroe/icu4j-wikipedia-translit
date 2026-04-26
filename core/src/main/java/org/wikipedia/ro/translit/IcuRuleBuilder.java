package org.wikipedia.ro.translit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.util.ULocale;

/**
 * Builds ICU rule strings from one language transliteration map.
 *
 * <p>Use this class when creating a {@link com.ibm.icu.text.Transliterator} from map data
 * loaded by this library.
 */
public final class IcuRuleBuilder {

    // _ = "any character" wildcard, : = used in UnicodeSet property expressions [:Prop:]
    private static final String RULE_METACHARACTERS = "{}><()=!@~&|^[];$#'\\_: \t\n\r";
    private static final String SET_METACHARACTERS = "[]\\^-'";

    private IcuRuleBuilder() {
    }

    /**
     * Converts a single language map into an ICU4J rule string.
     *
     * @param langMap transliteration entries for one language code
     * @param locale locale used for language-aware case expansion
     * @return ICU rule text consumable by Transliterator.createFromRules
     */
    public static String buildRules(Map<String, TransliterationValue> langMap, ULocale locale) {
        Map<String, String> simpleOut = buildSimpleOutputMap(langMap, locale);
        StringBuilder rules = new StringBuilder();
        Set<String> seenLhs = new HashSet<>();
        for (Map.Entry<String, TransliterationValue> entry : langMap.entrySet()) {
            appendRulesForChar(entry.getKey(), entry.getValue(), null, null,
                    locale, rules, seenLhs, simpleOut);
        }
        return rules.toString();
    }

    private static Map<String, String> buildSimpleOutputMap(
            Map<String, TransliterationValue> langMap, ULocale locale) {
        Map<String, String> out = new HashMap<>();
        out.put(" ", " ");
        for (Map.Entry<String, TransliterationValue> entry : langMap.entrySet()) {
            String src = UCharacter.toLowerCase(locale, entry.getKey());
            TransliterationValue rule = entry.getValue();
            String output = null;
            if (rule != null && rule.isText()) {
                output = rule.asText();
            } else if (rule != null && rule.isObject()) {
                TransliterationValue def = rule.get("def");
                if (def != null && def.isText()) {
                    output = def.asText();
                }
            }
            if (output != null && !out.containsKey(src)) {
                out.put(src, output);
            }
        }
        return out;
    }

    private static void appendRulesForChar(
            String srcChar, TransliterationValue rule,
            String prevCtx, String nextCtx,
            ULocale locale, StringBuilder rules, Set<String> seenLhs,
            Map<String, String> simpleOut) {

        if (rule == null) {
            return;
        }
        if (rule.isText()) {
            emitRule(srcChar, rule.asText(), prevCtx, nextCtx, locale, rules, seenLhs, simpleOut);
            return;
        }
        if (!rule.isObject()) {
            return;
        }

        TransliterationValue bh = rule.get("bh");
        if (bh != null && bh.isObject()) {
            for (Iterator<Map.Entry<String, TransliterationValue>> it = bh.fields().entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, TransliterationValue> e = it.next();
                String pCtx = e.getKey().isEmpty() ? " " : e.getKey();
                appendRulesForChar(srcChar, e.getValue(), pCtx, nextCtx,
                        locale, rules, seenLhs, simpleOut);
            }
        }

        TransliterationValue ah = rule.get("ah");
        if (ah != null && ah.isObject()) {
            for (Iterator<Map.Entry<String, TransliterationValue>> it = ah.fields().entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, TransliterationValue> e = it.next();
                String nCtx = e.getKey().isEmpty() ? " " : e.getKey();
                appendRulesForChar(srcChar, e.getValue(), prevCtx, nCtx,
                        locale, rules, seenLhs, simpleOut);
            }
        }

        TransliterationValue def = rule.get("def");
        if (def != null) {
            appendRulesForChar(srcChar, def, prevCtx, nextCtx, locale, rules, seenLhs, simpleOut);
        }
    }

    private static void emitRule(
            String srcChar, String target,
            String prevCtx, String nextCtx,
            ULocale locale, StringBuilder rules, Set<String> seenLhs,
            Map<String, String> simpleOut) {

        emitSingleRule(srcChar, target, prevCtx, nextCtx, locale, rules, seenLhs, simpleOut);

        // Explicit uppercase rule: ICU4J's automatic case handling is not reliable
        // for Cyrillic in rule-based transliterators, so generate uppercase explicitly.
        // Skip when the uppercase expansion is multi-codepoint (e.g. Armenian ligatures
        // like 'և' → 'ԵՎ') because those longer rules would need to precede all shorter
        // rules starting with the same first char, which would require re-ordering the
        // entire rule set. The practical impact is minimal.
        String upperSrc = UCharacter.toUpperCase(locale, srcChar);
        if (!upperSrc.equals(srcChar) && upperSrc.codePointCount(0, upperSrc.length()) == 1) {
            emitSingleRule(upperSrc, ucfirst(target), prevCtx, nextCtx, locale, rules, seenLhs, simpleOut);
        }
    }

    private static void emitSingleRule(
            String srcChar, String target,
            String prevCtx, String nextCtx,
            ULocale locale, StringBuilder rules, Set<String> seenLhs,
            Map<String, String> simpleOut) {

        StringBuilder lhs = new StringBuilder();
        if (prevCtx != null && !prevCtx.isEmpty()) {
            lhs.append(lookbehindPattern(prevCtx, locale, simpleOut)).append(" { ");
        }
        lhs.append(ruleEscape(srcChar));
        if (nextCtx != null && !nextCtx.isEmpty()) {
            lhs.append(" } ").append(lookaheadPattern(nextCtx, locale));
        }

        String lhsStr = lhs.toString();
        if (!seenLhs.add(lhsStr)) {
            return;
        }

        StringBuilder rule = new StringBuilder(lhsStr);
        rule.append(" > ");
        if (!target.isEmpty()) {
            rule.append(ruleEscape(target));
        }
        rule.append(" ;\n");
        rules.append(rule);
    }

    private static String ucfirst(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        int firstCp = s.codePointAt(0);
        int upperCp = UCharacter.toUpperCase(firstCp);
        String rest = s.substring(Character.charCount(firstCp));
        return new String(Character.toChars(upperCp)) + rest;
    }

    private static String lookbehindPattern(String ctx, ULocale locale, Map<String, String> simpleOut) {
        // ICU evaluates rules left-to-right, so lookbehind sees text that may already be
        // transliterated. We therefore map source context through the simple output map first.
        if (ctx.equals(" ")) {
            return "' '";
        }
        String lower = UCharacter.toLowerCase(locale, ctx);
        String translated = simpleOut.get(lower);
        if (translated == null || translated.isEmpty()) {
            return caseExpandedPattern(ctx, locale);
        }
        String lastCh = translated.substring(translated.length() - 1);
        return caseExpandedPattern(lastCh, ULocale.ENGLISH);
    }

    private static String lookaheadPattern(String ctx, ULocale locale) {
        // Lookahead still sees original source characters, so no source->target mapping is needed.
        if (ctx.equals(" ")) {
            return "' '";
        }
        return caseExpandedPattern(ctx, locale);
    }

    private static String caseExpandedPattern(String ch, ULocale locale) {
        String upper = UCharacter.toUpperCase(locale, ch);
        if (ch.equals(upper)) {
            return ruleEscape(ch);
        }
        return "[" + setEscape(ch) + setEscape(upper) + "]";
    }

    static String ruleEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp < 128 && RULE_METACHARACTERS.indexOf((char) cp) >= 0) {
                if (cp == '\'') {
                    sb.append("''");
                } else {
                    sb.append('\'').appendCodePoint(cp).append('\'');
                }
            } else {
                sb.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    private static String setEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp < 128 && SET_METACHARACTERS.indexOf((char) cp) >= 0) {
                sb.append('\\').appendCodePoint(cp);
            } else {
                sb.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }
}
