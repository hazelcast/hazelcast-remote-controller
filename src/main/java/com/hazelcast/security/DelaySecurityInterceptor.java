package com.hazelcast.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.AccessControlException;
import java.util.Arrays;
import java.util.List;

public class DelaySecurityInterceptor implements SecurityInterceptor {
    private static Logger LOG = LogManager.getLogger(DelaySecurityInterceptor.class);

    private List<String> methods = Arrays.asList("values", "executeOnKey", "executeOnKeys", "executeOnEntries");
    @Override
    public void before(Credentials credentials, String objectType, String objectName, String methodName, Parameters parameters)
            throws AccessControlException {
        if(methods.contains(methodName)) {
            LOG.info("Delaying the message task via security interceptor");
            try {
                Thread.sleep(300 * 1000);
            } catch (InterruptedException e) {
                //ignore
            }
        }

    }

    @Override
    public void after(Credentials credentials, String s, String s1, String s2, Parameters parameters) {
        //noop
    }
}
