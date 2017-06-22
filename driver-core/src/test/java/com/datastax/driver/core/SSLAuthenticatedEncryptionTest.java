/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.core;

import com.datastax.driver.core.exceptions.NoHostAvailableException;
import org.testng.annotations.Test;

import static com.datastax.driver.core.CCMBridge.*;

@CCMConfig(auth = true)
public class SSLAuthenticatedEncryptionTest extends SSLTestBase {

    /**
     * <p>
     * Validates that an SSL connection can be established with client auth if the target
     * cassandra cluster is using SSL, requires client auth and would validate with the client's
     * certificate.
     * </p>
     *
     * @test_category connection:ssl, authentication
     * @expected_result Connection can be established to a cassandra node using SSL that requires client auth.
     */
    @Test(groups = "short", dataProvider = "sslImplementation", dataProviderClass = SSLTestBase.class)
    public void should_connect_with_ssl_with_client_auth_and_node_requires_auth(SslImplementation sslImplementation) throws Exception {
        connectWithSSLOptions(getSSLOptions(sslImplementation, true, true));
    }

    /**
     * <p>
     * Validates that an SSL connection can not be established with if the target
     * cassandra cluster is using SSL, requires client auth, but the client does not provide
     * sufficient certificate authentication.
     * </p>
     *
     * @test_category connection:ssl, authentication
     * @expected_result Connection is not established.
     */
    @Test(groups = "short", dataProvider = "sslImplementation", dataProviderClass = SSLTestBase.class, expectedExceptions = {NoHostAvailableException.class})
    public void should_not_connect_without_client_auth_but_node_requires_auth(SslImplementation sslImplementation) throws Exception {
        connectWithSSLOptions(getSSLOptions(sslImplementation, false, true));
    }

    /**
     * <p>
     * Validates that SSL connectivity can be configured via the standard javax.net.ssl System properties.
     * </p>
     *
     * @test_category connection:ssl, authentication
     * @expected_result Connection can be established.
     */
    @Test(groups = "isolated")
    public void should_use_system_properties_with_default_ssl_options() throws Exception {
        System.setProperty("javax.net.ssl.keyStore", DEFAULT_CLIENT_KEYSTORE_FILE.getAbsolutePath());
        System.setProperty("javax.net.ssl.keyStorePassword", DEFAULT_CLIENT_KEYSTORE_PASSWORD);
        System.setProperty("javax.net.ssl.trustStore", DEFAULT_CLIENT_TRUSTSTORE_FILE.getAbsolutePath());
        System.setProperty("javax.net.ssl.trustStorePassword", DEFAULT_CLIENT_TRUSTSTORE_PASSWORD);
        try {
            connectWithSSL();
        } finally {
            try {
                System.clearProperty("javax.net.ssl.keyStore");
                System.clearProperty("javax.net.ssl.keyStorePassword");
                System.clearProperty("javax.net.ssl.trustStore");
                System.clearProperty("javax.net.ssl.trustStorePassword");
            } catch (SecurityException e) {
                // ok
            }
        }
    }
}
