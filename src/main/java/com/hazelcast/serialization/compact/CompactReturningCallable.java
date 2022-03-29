package com.hazelcast.serialization.compact;

import java.util.concurrent.Callable;

public class CompactReturningCallable implements Callable<OuterCompact> {
    @Override
    public OuterCompact call() throws Exception {
        return OuterCompact.INSTANCE;
    }
}
