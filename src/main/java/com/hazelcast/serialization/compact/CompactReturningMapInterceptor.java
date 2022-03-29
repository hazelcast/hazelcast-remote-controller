package com.hazelcast.serialization.compact;

import com.hazelcast.map.MapInterceptor;

public class CompactReturningMapInterceptor implements MapInterceptor {
    @Override
    public Object interceptGet(Object o) {
        return OuterCompact.INSTANCE;
    }

    @Override
    public void afterGet(Object o) {

    }

    @Override
    public Object interceptPut(Object o, Object o1) {
        return null;
    }

    @Override
    public void afterPut(Object o) {

    }

    @Override
    public Object interceptRemove(Object o) {
        return null;
    }

    @Override
    public void afterRemove(Object o) {

    }
}
