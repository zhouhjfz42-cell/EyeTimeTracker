package org.json;

import java.util.HashMap;
import java.util.Map;

public class JSONObject {
    private final Map<String, Object> values = new HashMap<>();

    public JSONObject() {
    }

    public JSONObject(String raw) throws JSONException {
        throw new JSONException("String parsing is not supported by this test JSON object.");
    }

    public JSONObject put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public String getString(String key) throws JSONException {
        if (!values.containsKey(key)) {
            throw new JSONException("String not found for key " + key);
        }
        return optString(key, "");
    }

    public String optString(String key) {
        return optString(key, "");
    }

    public String optString(String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : value.toString();
    }

    public long optLong(String key, long fallback) {
        Object value = values.get(key);
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

    public boolean optBoolean(String key, boolean fallback) {
        Object value = values.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return fallback;
    }

    public int optInt(String key, int fallback) {
        Object value = values.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    public JSONArray optJSONArray(String key) {
        Object value = values.get(key);
        return value instanceof JSONArray ? (JSONArray) value : null;
    }

    public JSONArray getJSONArray(String key) throws JSONException {
        JSONArray value = optJSONArray(key);
        if (value == null) {
            throw new JSONException("JSONArray not found for key " + key);
        }
        return value;
    }

    public JSONObject optJSONObject(String key) {
        Object value = values.get(key);
        return value instanceof JSONObject ? (JSONObject) value : null;
    }
}
