package com.hazelcast.security;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import java.io.IOException;

public class CompactCredentialsLoginModule extends ClusterLoginModule {
    private String name;

    @Override
    protected boolean onLogin() throws LoginException {
        CredentialsCallback cb = new CredentialsCallback();
        TokenDeserializerCallback tdcb = new TokenDeserializerCallback();
        try {
            callbackHandler.handle(new Callback[]{cb, tdcb});
        } catch (IOException | UnsupportedCallbackException e) {
            throw new LoginException("Problem getting credentials");
        }
        Credentials credentials = cb.getCredentials();
        if (credentials instanceof TokenCredentials) {
            TokenCredentials tokenCreds = (TokenCredentials) credentials;
            credentials = (Credentials) tdcb.getTokenDeserializer().deserialize(tokenCreds);
        }
        if (!(credentials instanceof CompactCredentials)) {
            throw new FailedLoginException();
        }
        CompactCredentials cc = (CompactCredentials) credentials;
        if (cc.getName().equals(options.get("username"))
                && cc.getKey1().equals(options.get("key1"))
                && cc.getKey2().equals(options.get("key2"))) {
            name = cc.getName();
            addRole(name);
            return true;
        }
        throw new LoginException("Invalid credentials");
    }

    @Override
    protected String getName() {
        return name;
    }
}

