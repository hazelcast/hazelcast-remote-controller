package com.hazelcast.security;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.Objects;

public class CompactCredentialsLoginModule extends ClusterLoginModule {
    private String name;

    @Override
    protected boolean onLogin() throws LoginException {
        CredentialsCallback cb = new CredentialsCallback();
        TokenDeserializerCallback tdcb = new TokenDeserializerCallback();
        try {
            callbackHandler.handle(new Callback[]{cb, tdcb});
        } catch (IOException | UnsupportedCallbackException e) {
            throw new LoginException("Failed to get the credentials");
        }

        Credentials credentials = cb.getCredentials();
        if (credentials instanceof TokenCredentials) {
            TokenCredentials tokenCredentials = (TokenCredentials) credentials;
            credentials = (Credentials) tdcb.getTokenDeserializer().deserialize(tokenCredentials);
        }

        if (!(credentials instanceof CompactCredentials)) {
            throw new FailedLoginException("The " + credentials + " is not of type " + CompactCredentials.class.getSimpleName());
        }

        CompactCredentials cc = (CompactCredentials) credentials;
        String username = Objects.requireNonNull(cc.getName(), "Username of the credentials cannot be null");
        String key1 = Objects.requireNonNull(cc.getKey1(), "Key1 of the credentials cannot be null");
        String key2 = Objects.requireNonNull(cc.getKey2(), "Key2 of the credentials cannot be null");

        String configuredUsername = Objects.requireNonNull((String) options.get("username"), "Configured username cannot be null");
        String configuredKey1 = Objects.requireNonNull((String) options.get("key1"), "Configured key1 cannot be null");
        String configuredKey2 = Objects.requireNonNull((String) options.get("key2"), "Configured key2 cannot be null");

        if (!username.equals(configuredUsername)
                || !key1.equals(configuredKey1)
                || !key2.equals(configuredKey2)) {
            throw new LoginException("Invalid credentials. Make sure the configured properties are the same with the credentials sent.");
        }

        name = username;
        addRole(name);
        return true;
    }

    @Override
    protected String getName() {
        return name;
    }
}
