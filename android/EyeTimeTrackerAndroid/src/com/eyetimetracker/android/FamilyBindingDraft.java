package com.eyetimetracker.android;

public final class FamilyBindingDraft {
    public final String bindingCode;

    private FamilyBindingDraft(String bindingCode) {
        this.bindingCode = bindingCode;
    }

    public static FamilyBindingDraft create(String bindingCode) {
        String normalized = FamilyBindingInvite.normalizeBindingCode(bindingCode);
        if (!normalized.matches("\\d{6}")) {
            throw new IllegalArgumentException("Binding code must be 6 digits.");
        }
        return new FamilyBindingDraft(normalized);
    }
}
