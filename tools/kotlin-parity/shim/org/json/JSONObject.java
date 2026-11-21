package org.json;
import java.util.*;
/** Minimal stand-in for Android's org.json, enough for ShomerEngine: used only to run the engine on a plain JVM. */
public class JSONObject {
    final LinkedHashMap<String,Object> m = new LinkedHashMap<>();
    public JSONObject(String src) { Object o = new P(src).value(); m.putAll(((JSONObject) o).m); }
    JSONObject() {}
    public Iterator<String> keys() { return m.keySet().iterator(); }
    public JSONArray getJSONArray(String k) { return (JSONArray) get(k); }
    public JSONObject getJSONObject(String k) { return (JSONObject) get(k); }
    public String getString(String k) { return (String) get(k); }
    public boolean getBoolean(String k) { return (Boolean) get(k); }
    public int getInt(String k) { return ((Number) get(k)).intValue(); }
    public JSONObject optJSONObject(String k) { Object o = m.get(k); return o instanceof JSONObject ? (JSONObject) o : null; }
    public JSONArray optJSONArray(String k) { Object o = m.get(k); return o instanceof JSONArray ? (JSONArray) o : null; }
    public String optString(String k, String d) { Object o = m.get(k); return o instanceof String ? (String) o : d; }
    Object get(String k) { if (!m.containsKey(k)) throw new RuntimeException("missing " + k); return m.get(k); }

    static class P {
        final String s; int i = 0;
        P(String s) { this.s = s; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        Object value() {
            ws(); char c = s.charAt(i);
            if (c == '{') { i++; JSONObject o = new JSONObject(); ws(); if (s.charAt(i) == '}') { i++; return o; }
                while (true) { ws(); String k = str(); ws(); i++; o.m.put(k, value()); ws(); if (s.charAt(i++) == '}') return o; } }
            if (c == '[') { i++; JSONArray a = new JSONArray(); ws(); if (s.charAt(i) == ']') { i++; return a; }
                while (true) { a.l.add(value()); ws(); if (s.charAt(i++) == ']') return a; } }
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int j = i; while (j < s.length() && "+-0123456789.eE".indexOf(s.charAt(j)) >= 0) j++;
            String n = s.substring(i, j); i = j; return n.contains(".") ? (Object) Double.valueOf(n) : (Object) Integer.valueOf(n);
        }
        String str() {
            i++; StringBuilder b = new StringBuilder();
            while (s.charAt(i) != '"') { char c = s.charAt(i++);
                if (c == '\\') { char e = s.charAt(i++);
                    switch (e) { case 'n': b.append('\n'); break; case 't': b.append('\t'); break; case 'r': b.append('\r'); break; case 'b': b.append('\b'); break; case 'f': b.append('\f'); break;
                        case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break; default: b.append(e); } }
                else b.append(c); }
            i++; return b.toString();
        }
    }
}
