package com.hazelcast.security;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public class SimpleLoginModule implements LoginModule {
    private static final String USERNAME_OPTION = "username";
    private static final String PASSWORD_OPTION = "password";

    private CallbackHandler callbackHandler;
    private String username;
    private String password;

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
        this.callbackHandler = callbackHandler;
        this.username = (String) options.get(USERNAME_OPTION);
        this.password = (String) options.get(PASSWORD_OPTION);
    }

    @Override
    public boolean login() throws LoginException {
        CredentialsCallback credentialsCb = new CredentialsCallback();
        TokenDeserializerCallback tokenDeserializerCb = new TokenDeserializerCallback();
        try {
            callbackHandler.handle(new Callback[] { credentialsCb, tokenDeserializerCb });
        } catch (IOException | UnsupportedCallbackException e) {
            throw new LoginException("Unable to retrieve necessary data");
        }

        Credentials credentials = credentialsCb.getCredentials();
        if (credentials == null) {
            throw new LoginException("Credentials could not be retrieved!");
        }

        if (credentials instanceof TokenCredentials) {
            TokenCredentials tokenCredentials = (TokenCredentials) credentials;
            credentials = (Credentials) tokenDeserializerCb.getTokenDeserializer().deserialize(tokenCredentials);
        }

        if (credentials instanceof SimpleCredentials) {
            SimpleCredentials simpleCredentials = (SimpleCredentials) credentials;
            doAuthenticate(simpleCredentials);
        } else {
            throw new LoginException("Credentials is not an instance of SimpleCredentials!");
        }

        return true;
    }

    private void doAuthenticate(SimpleCredentials credentials) throws LoginException {
        String username = credentials.getName();
        String password = credentials.getPassword();

        if (!Objects.equals(username, this.username) || !Objects.equals(password, this.password)) {
            throw new LoginException("Received invalid credentials!");
        }
    }

    @Override
    public boolean commit() throws LoginException {
        return true;
    }

    @Override
    public boolean abort() throws LoginException {
        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        return true;
    }
}
