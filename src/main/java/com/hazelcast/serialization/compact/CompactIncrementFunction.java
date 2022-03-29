package com.hazelcast.serialization.compact;

import com.hazelcast.core.IFunction;

public class CompactIncrementFunction implements IFunction<Long, Long> {
    @Override
    public Long apply(Long aLong) {
        return aLong + 1;
    }
}
