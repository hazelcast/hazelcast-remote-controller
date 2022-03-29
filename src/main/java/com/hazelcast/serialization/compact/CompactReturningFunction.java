package com.hazelcast.serialization.compact;

import com.hazelcast.core.IFunction;

public class CompactReturningFunction implements IFunction<Object, OuterCompact> {
    @Override
    public OuterCompact apply(Object o) {
        return OuterCompact.INSTANCE;
    }
}
