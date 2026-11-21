package org.json;
import java.util.*;
public class JSONArray {
    final ArrayList<Object> l = new ArrayList<>();
    public int length() { return l.size(); }
    public String getString(int i) { return (String) l.get(i); }
    public JSONObject getJSONObject(int i) { return (JSONObject) l.get(i); }
}
