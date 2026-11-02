package com.pocketcodex.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小而快的 JSON：解析成 LinkedHashMap / ArrayList / String / Long / Double / Boolean / null，序列化反过来。
 * 不用 org.json（安卓自带的版本和 JVM 上的不一样，测试和真机会有差别）。
 */
public final class Json {
    private Json() {
    }

    /* ---------------- 解析 ---------------- */

    public static Object parse(String s) {
        P p = new P(s);
        p.ws();
        Object v = p.value(0);
        p.ws();
        if (p.i != s.length()) throw p.err("多余的字符");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String s) {
        Object v = parse(s);
        if (!(v instanceof Map)) throw new IllegalArgumentException("不是 JSON 对象");
        return (Map<String, Object>) v;
    }

    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");

    private static final class P {
        final String s;
        int i;

        P(String s) {
            this.s = s;
        }

        IllegalArgumentException err(String m) {
            return new IllegalArgumentException("JSON 第 " + i + " 个字符：" + m);
        }

        void ws() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++;
                else break;
            }
        }

        Object value(int depth) {
            if (depth > 512) throw err("嵌套太深");
            if (i >= s.length()) throw err("意外结束");
            char c = s.charAt(i);
            switch (c) {
                case '{':
                    return object(depth);
                case '[':
                    return array(depth);
                case '"':
                    return string();
                case 't':
                    lit("true");
                    return Boolean.TRUE;
                case 'f':
                    lit("false");
                    return Boolean.FALSE;
                case 'n':
                    lit("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return number();
                    throw err("意外的字符 " + c);
            }
        }

        void lit(String w) {
            if (!s.startsWith(w, i)) throw err("应为 " + w);
            i += w.length();
        }

        Map<String, Object> object(int depth) {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (true) {
                ws();
                if (i >= s.length() || s.charAt(i) != '"') throw err("应为键名");
                String k = string();
                ws();
                if (i >= s.length() || s.charAt(i) != ':') throw err("应为冒号");
                i++;
                ws();
                m.put(k, value(depth + 1));
                ws();
                if (i >= s.length()) throw err("对象没有结束");
                char c = s.charAt(i++);
                if (c == '}') return m;
                if (c != ',') throw err("应为逗号");
            }
        }

        List<Object> array(int depth) {
            List<Object> a = new ArrayList<>();
            i++;
            ws();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return a;
            }
            while (true) {
                ws();
                a.add(value(depth + 1));
                ws();
                if (i >= s.length()) throw err("数组没有结束");
                char c = s.charAt(i++);
                if (c == ']') return a;
                if (c != ',') throw err("应为逗号");
            }
        }

        String string() {
            i++; // "
            StringBuilder b = null;
            int start = i;
            while (true) {
                if (i >= s.length()) throw err("字符串没有结束");
                char c = s.charAt(i);
                if (c == '"') {
                    String r = b == null ? s.substring(start, i) : b.append(s, start, i).toString();
                    i++;
                    return r;
                }
                if (c == '\\') {
                    if (b == null) b = new StringBuilder();
                    b.append(s, start, i);
                    i++;
                    if (i >= s.length()) throw err("转义没有结束");
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': b.append('"'); break;
                        case '\\': b.append('\\'); break;
                        case '/': b.append('/'); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case 'n': b.append('\n'); break;
                        case 'r': b.append('\r'); break;
                        case 't': b.append('\t'); break;
                        case 'u':
                            if (i + 4 > s.length()) throw err("\\u 不完整");
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                            break;
                        default:
                            throw err("未知转义 \\" + e);
                    }
                    start = i;
                } else {
                    if (c < 0x20) throw err("字符串里有未转义的控制字符");
                    i++;
                }
            }
        }

        Object number() {
            int st = i;
            boolean frac = false;
            if (s.charAt(i) == '-') i++;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') i++;
                else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    frac = true;
                    i++;
                } else break;
            }
            String t = s.substring(st, i);
            if (!NUMBER.matcher(t).matches()) throw err("数字格式不对 " + t);
            if (!frac) {
                try {
                    return Long.parseLong(t);
                } catch (NumberFormatException ignored) {
                    // 超出 long：按小数
                }
            }
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                throw err("数字格式不对 " + t);
            }
        }
    }

    /* ---------------- 序列化 ---------------- */

    public static String write(Object v) {
        StringBuilder b = new StringBuilder(128);
        write(b, v);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    public static void write(StringBuilder b, Object v) {
        if (v == null) {
            b.append("null");
        } else if (v instanceof String) {
            quote(b, (String) v);
        } else if (v instanceof Boolean) {
            b.append(((Boolean) v) ? "true" : "false");
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) b.append("null");
            else if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(d);
        } else if (v instanceof Number) {
            b.append(((Number) v).longValue());
        } else if (v instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                if (!first) b.append(',');
                first = false;
                quote(b, e.getKey());
                b.append(':');
                write(b, e.getValue());
            }
            b.append('}');
        } else if (v instanceof List) {
            b.append('[');
            boolean first = true;
            for (Object o : (List<Object>) v) {
                if (!first) b.append(',');
                first = false;
                write(b, o);
            }
            b.append(']');
        } else {
            quote(b, v.toString());
        }
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        b.append("\\u").append(HEX[(c >> 12) & 0xF]).append(HEX[(c >> 8) & 0xF]).append(HEX[(c >> 4) & 0xF]).append(HEX[c & 0xF]);
                    } else {
                        b.append(c);
                    }
            }
        }
        b.append('"');
    }

    /* ---------------- 取值的小工具（缺了就给默认值，不抛异常） ---------------- */

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object m, String key) {
        Object v = m instanceof Map ? ((Map<String, Object>) m).get(key) : null;
        return v instanceof Map ? (Map<String, Object>) v : Collections.<String, Object>emptyMap();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object m, String key) {
        Object v = m instanceof Map ? ((Map<String, Object>) m).get(key) : null;
        return v instanceof List ? (List<Object>) v : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public static String str(Object m, String key) {
        Object v = m instanceof Map ? ((Map<String, Object>) m).get(key) : null;
        return v instanceof String ? (String) v : null;
    }

    public static String str(Object m, String key, String def) {
        String v = str(m, key);
        return v == null ? def : v;
    }

    @SuppressWarnings("unchecked")
    public static long num(Object m, String key, long def) {
        Object v = m instanceof Map ? ((Map<String, Object>) m).get(key) : null;
        return v instanceof Number ? ((Number) v).longValue() : def;
    }

    @SuppressWarnings("unchecked")
    public static double dbl(Object m, String key, double def) {
        Object v = m instanceof Map ? ((Map<String, Object>) m).get(key) : null;
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }

    @SuppressWarnings("unchecked")
    public static boolean bool(Object m, String key, boolean def) {
        Object v = m instanceof Map ? ((Map<String, Object>) m).get(key) : null;
        return v instanceof Boolean ? (Boolean) v : def;
    }

    /** 小对象的构造：Json.map("a", 1, "b", "x")。 */
    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
