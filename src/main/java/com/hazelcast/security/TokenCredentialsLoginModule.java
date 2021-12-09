package com.hazelcast.security;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

public class TokenCredentialsLoginModule implements LoginModule {
    private static final String TOKEN_OPTION = "token";
    private static final String ENCODING_OPTION = "encoding";
    private static final String ASCII_ENCODING = "ascii";
    private static final String BASE64_ENCODING = "base64";

    private CallbackHandler callbackHandler;
    private byte[] token;

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
        this.callbackHandler = callbackHandler;
        String tokenString = (String) options.get(TOKEN_OPTION);
        String encoding = (String) options.get(ENCODING_OPTION);

        if (ASCII_ENCODING.equalsIgnoreCase(encoding) || encoding == null) {
            this.token = tokenString.getBytes(StandardCharsets.US_ASCII);
        } else if (BASE64_ENCODING.equalsIgnoreCase(encoding)) {
            this.token = Base64.getDecoder().decode(tokenString);
        } else {
            throw new IllegalStateException("Received unknown encoding");
        }
    }

    @Override
    public boolean login() throws LoginException {
        CredentialsCallback credentialsCb = new CredentialsCallback();
        try {
            callbackHandler.handle(new Callback[] { credentialsCb });
        } catch (IOException | UnsupportedCallbackException e) {
            throw new LoginException("Unable to retrieve necessary data");
        }

        Credentials credentials = credentialsCb.getCredentials();
        if (credentials == null) {
            throw new LoginException("Credentials could not be retrieved!");
        }

        if (credentials instanceof TokenCredentials) {
            TokenCredentials tokenCredentials = (TokenCredentials) credentials;
            doAuthenticate(tokenCredentials);
        } else {
            throw new LoginException("Credentials is not an instance of TokenCredentials!");
        }

        return true;
    }

    private void doAuthenticate(TokenCredentials credentials) throws LoginException {
        byte[] token = credentials.getToken();

        if (!Arrays.equals(token, this.token)) {
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
