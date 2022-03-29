package com.hazelcast.serialization.compact;

import com.hazelcast.projection.Projection;

public class CompactReturningProjection implements Projection<Object, Object> {
    @Override
    public Object transform(Object o) {
        return OuterCompact.INSTANCE;
    }
}
