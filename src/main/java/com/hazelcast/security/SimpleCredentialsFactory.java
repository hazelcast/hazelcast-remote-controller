package com.hazelcast.security;

import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

public class SimpleCredentialsFactory implements DataSerializableFactory {
    static final int FACTORY_ID = 1;

    @Override
    public IdentifiedDataSerializable create(int classId) {
        if (classId == SimpleCredentials.CLASS_ID) {
            return new SimpleCredentials();
        }
        return null;
    }
}
