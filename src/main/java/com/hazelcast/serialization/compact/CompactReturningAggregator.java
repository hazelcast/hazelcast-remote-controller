package com.hazelcast.serialization.compact;

import com.hazelcast.aggregation.Aggregator;

public class CompactReturningAggregator implements Aggregator<Object, OuterCompact> {
    @Override
    public void accumulate(Object o) {

    }

    @Override
    public void combine(Aggregator aggregator) {

    }

    @Override
    public OuterCompact aggregate() {
        return OuterCompact.INSTANCE;
    }
}
