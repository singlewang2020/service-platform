package com.ruler.one.runtime;

import java.util.HashMap;
import java.util.Map;

public class Checkpoint {
    private Map<String, Object> data = new HashMap<>();

    public Checkpoint() {}

    public Checkpoint put(String key, Object val) {
        data.put(key, val);
        return this;
    }

    public Object get(String key) { return data.get(key); }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
