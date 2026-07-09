package org.json;

import java.util.ArrayList;
import java.util.List;

public class JSONArray {
    private final List<Object> values = new ArrayList<>();

    public JSONArray() {
    }

    public JSONArray put(Object value) {
        values.add(value);
        return this;
    }

    public JSONArray put(int index, Object value) {
        while (values.size() <= index) {
            values.add(null);
        }
        values.set(index, value);
        return this;
    }

    public int length() {
        return values.size();
    }

    public long optLong(int index, long fallback) {
        if (index < 0 || index >= values.size()) {
            return fallback;
        }
        Object value = values.get(index);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    public JSONObject optJSONObject(int index) {
        if (index < 0 || index >= values.size()) {
            return null;
        }
        Object value = values.get(index);
        return value instanceof JSONObject ? (JSONObject) value : null;
    }

    public JSONObject getJSONObject(int index) throws JSONException {
        JSONObject value = optJSONObject(index);
        if (value == null) {
            throw new JSONException("JSONObject not found at index " + index);
        }
        return value;
    }
}
