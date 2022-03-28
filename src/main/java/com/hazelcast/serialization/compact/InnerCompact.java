package com.hazelcast.serialization.compact;

import java.util.Objects;

public class InnerCompact {

    public static final InnerCompact INSTANCE = new InnerCompact("42");

    private String stringField;

    public InnerCompact() {

    }

    private InnerCompact(String stringField) {
        this.stringField = stringField;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InnerCompact inner = (InnerCompact) o;
        return Objects.equals(stringField, inner.stringField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stringField);
    }
}
