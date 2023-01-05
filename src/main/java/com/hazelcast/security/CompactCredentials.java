package com.hazelcast.security;

public class CompactCredentials implements Credentials {

    private String username;
    private String key1;
    private String key2;

    public CompactCredentials() {
    }

    public CompactCredentials(String username, String key1, String key2) {
        this.username = username;
        this.key1 = key1;
        this.key2 = key2;
    }

    @Override
    public String getName() {
        return username;
    }

    public String getKey1() {
        return key1;
    }

    public String getKey2() {
        return key2;
    }
}
