package com.pocketcodex.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class JsonTest {
    @Test
    public void roundTripsNestedValues() {
        String s = "{\"a\":1,\"b\":[true,false,null,\"x\"],\"c\":{\"d\":-2.5,\"e\":\"中文\\n\\\"q\\\"\"},\"big\":12345678901234}";
        Map<String, Object> m = Json.parseObject(s);
        assertEquals(1L, m.get("a"));
        assertEquals(Arrays.asList(true, false, null, "x"), m.get("b"));
        assertEquals(-2.5, Json.dbl(Json.obj(m, "c"), "d", 0), 0);
        assertEquals("中文\n\"q\"", Json.str(Json.obj(m, "c"), "e"));
        assertEquals(12345678901234L, m.get("big"));
        assertEquals(s, Json.write(m));
    }

    @Test
    public void handlesEscapesAndUnicode() {
        Object v = Json.parse("\"\\u4e2d\\t\\/\\b\\f\\r\"");
        assertEquals("中\t/\b\f\r", v);
        assertEquals("\"\\u0001 \\u2028\"", Json.write("\u0001 \u2028"));
    }

    @Test
    public void accessorsDefaultWhenMissing() {
        Map<String, Object> m = Json.parseObject("{\"x\":{\"n\":3}}");
        assertTrue(Json.obj(m, "nope").isEmpty());
        assertTrue(Json.arr(m, "nope").isEmpty());
        assertNull(Json.str(m, "x"));
        assertEquals(3, Json.num(Json.obj(m, "x"), "n", 0));
        assertEquals(7, Json.num(m, "nope", 7));
        List<Object> l = Json.arr(Json.parseObject("{\"l\":[1,2]}"), "l");
        assertEquals(2, l.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsGarbage() {
        Json.parse("{\"a\":1,}");
    }

    @Test
    public void strictNumbersAndStrings() {
        for (String bad : new String[]{"01", "1.", ".5", "-", "1e", "\"a\nb\""}) {
            try {
                Json.parse(bad.replace("\\n", "\n"));
                org.junit.Assert.fail("应该拒绝：" + bad);
            } catch (IllegalArgumentException expected) {
                // 好
            }
        }
        assertEquals(1.5e3, (Double) Json.parse("1.5e3"), 0);
        assertEquals(-0L, Json.parse("-0"));
    }
}
