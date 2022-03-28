package com.hazelcast.serialization.compact;

import java.util.Objects;

public class OuterCompact {

    public static final OuterCompact INSTANCE = new OuterCompact(42, InnerCompact.INSTANCE);

    private int intField;
    private InnerCompact innerField;

    public OuterCompact() {

    }

    public OuterCompact(int intField, InnerCompact innerField) {
        this.intField = intField;
        this.innerField = innerField;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OuterCompact outer = (OuterCompact) o;
        return intField == outer.intField && Objects.equals(innerField, outer.innerField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intField, innerField);
    }
}
