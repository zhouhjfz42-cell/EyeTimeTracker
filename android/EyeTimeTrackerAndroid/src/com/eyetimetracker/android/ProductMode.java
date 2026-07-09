package com.eyetimetracker.android;

public enum ProductMode {
    PERSONAL("personal"),
    FAMILY("family");

    private final String storageValue;

    ProductMode(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }

    public static ProductMode fromStorageValue(String value) {
        if (value != null) {
            for (ProductMode mode : values()) {
                if (mode.storageValue.equals(value.trim())) {
                    return mode;
                }
            }
        }
        return PERSONAL;
    }
}
