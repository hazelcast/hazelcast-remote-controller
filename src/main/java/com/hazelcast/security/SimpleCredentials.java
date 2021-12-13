package com.hazelcast.security;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

import java.io.IOException;

public class SimpleCredentials implements Credentials, IdentifiedDataSerializable {
    static final int CLASS_ID = 1;

    private String username;
    private String password;

    public SimpleCredentials() {
    }

    @Override
    public String getName() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public int getFactoryId() {
        return SimpleCredentialsFactory.FACTORY_ID;
    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    @Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {
        objectDataOutput.writeUTF(username);
        objectDataOutput.writeUTF(password);
    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {
        username = objectDataInput.readUTF();
        password = objectDataInput.readUTF();
    }
}
