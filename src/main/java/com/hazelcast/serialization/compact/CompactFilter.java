package com.hazelcast.serialization.compact;

import com.hazelcast.core.IFunction;

public class CompactFilter implements IFunction<Object, Boolean> {
    @Override
    public Boolean apply(Object o) {
        return Boolean.TRUE;
    }
}
