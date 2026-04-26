package org.wikipedia.ro.translit;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;
import com.ibm.icu.util.ULocale;

public class IcuRuleBuilderTest {

    @Test
    public void buildRulesIgnoresNullAndNonObjectValues() {
        Map<String, TransliterationValue> langMap = new LinkedHashMap<>();
        langMap.put("a", null);
        langMap.put("b", TransliterationValue.unsupported());

        String rules = IcuRuleBuilder.buildRules(langMap, ULocale.ENGLISH);
        assertEquals("", rules);
    }

    @Test
    public void ruleEscapeEscapesApostropheAndUnderscore() {
        assertEquals("''" + "'_" + "'", IcuRuleBuilder.ruleEscape("'_"));
    }

    @Test
    public void setEscapeEscapesSetMetacharactersViaReflection() throws Exception {
        Method method = IcuRuleBuilder.class.getDeclaredMethod("setEscape", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(null, "[]^-'");
        assertEquals("\\[\\]\\^\\-\\'", result);
    }
}

