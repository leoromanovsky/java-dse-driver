/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.core;

import com.datastax.driver.core.exceptions.AuthenticationException;

import java.net.InetSocketAddress;

/**
 * Provides {@link Authenticator} instances for use when connecting
 * to Cassandra nodes.
 * <p/>
 * See {@link PlainTextAuthProvider} for an implementation which uses SASL
 * PLAIN mechanism to authenticate using username/password strings
 */
public interface AuthProvider {

    /**
     * A provider that provides no authentication capability.
     * <p/>
     * This is only useful as a placeholder when no authentication is to be used.
     */
    AuthProvider NONE = new NoAuthProvider();

    /**
     * The {@code Authenticator} to use when connecting to {@code host}
     *
     * @param host          the Cassandra host to connect to.
     * @param authenticator the configured authenticator on the host.
     * @return The authentication implementation to use.
     */
    Authenticator newAuthenticator(InetSocketAddress host, String authenticator) throws AuthenticationException;

    class NoAuthProvider implements AuthProvider {

        private static final String DSE_AUTHENTICATOR = "com.datastax.bdp.cassandra.auth.DseAuthenticator";

        static final String NO_AUTHENTICATOR_MESSAGE = "Host %s requires authentication, but no authenticator found in Cluster configuration";

        @Override
        public Authenticator newAuthenticator(InetSocketAddress host, String authenticator) {
            if (authenticator.equals(DSE_AUTHENTICATOR))
                return new TransitionalModePlainTextAuthenticator();
            throw new AuthenticationException(host,
                String.format(NO_AUTHENTICATOR_MESSAGE, host));
        }
    }

    /**
     * Dummy Authenticator that accounts for DSE authentication configured with transitional mode.
     *
     * In this situation, the client is allowed to connect without authentication, but DSE
     * would still send an AUTHENTICATE response. This Authenticator handles this situation
     * by sending back a dummy credential.
     */
    class TransitionalModePlainTextAuthenticator extends PlainTextAuthProvider.PlainTextAuthenticator {

        public TransitionalModePlainTextAuthenticator() {
            super("", "");
        }

    }

}
