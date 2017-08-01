/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.auth;

import com.datastax.driver.core.*;
import com.datastax.driver.core.exceptions.AuthenticationException;
import com.datastax.driver.core.utils.DseVersion;
import com.datastax.driver.dse.CCMDseTestsSupport;
import com.datastax.driver.dse.DseCluster;
import com.datastax.driver.dse.DseSession;
import org.testng.annotations.Test;

import static com.datastax.driver.core.CreateCCM.TestMode.PER_METHOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.fail;

/**
 * Tests for transitional mode (JAVA-1578)
 */
@CreateCCM(PER_METHOD)
@DseVersion("5.0")
@CCMConfig(
    createCluster = false,
    config = {
        "authorizer:com.datastax.bdp.cassandra.auth.DseAuthorizer",
        "authenticator:com.datastax.bdp.cassandra.auth.DseAuthenticator"
    },
    dseConfig = {
        "authentication_options.enabled:true",
        "authentication_options.default_scheme:internal"
    },
    jvmArgs = "-Dcassandra.superuser_setup_delay_ms=0")
public class DseAuthenticatorTransitionalModeTest extends CCMDseTestsSupport {

    /**
     * Validates that unauthorized login is allowed when DSE authentication is enabled but
     * transitional mode is set to normal.
     *
     * @jira_ticket JAVA-1578
     * @test_category dse:authentication
     */
    @CCMConfig(dseConfig = "authentication_options.transitional_mode:normal")
    @Test(groups = "short")
    public void should_allow_unauthorized_login_when_transitional_mode_on() throws Exception {
        tryConnect(clusterBuilder().build());
    }

    /**
     * Validates that unauthorized login is not allowed when DSE authentication is enabled and
     * transitional mode is disabled.
     *
     * @jira_ticket JAVA-1578
     * @test_category dse:authentication
     */
    @CCMConfig(dseConfig = "authentication_options.transitional_mode:disabled")
    @Test(groups = "short")
    public void should_not_allow_unauthorized_login_when_transitional_mode_off() throws Exception {
        try {
            tryConnect(clusterBuilder().build());
            fail("Expecting AuthenticationException");
        } catch (AuthenticationException e) {
            assertThat(e).hasMessageEndingWith("requires authentication, but no authenticator found in Cluster configuration");
        }
    }

    /**
     * Validates that authorized login is allowed when DSE authentication is enabled and
     * transitional mode is set to normal.
     *
     * @jira_ticket JAVA-1578
     * @test_category dse:authentication
     */
    @CCMConfig(dseConfig = "authentication_options.transitional_mode:normal")
    @Test(groups = "short")
    public void should_allow_authorized_login_when_transitional_mode_on() throws Exception {
        AuthProvider authProvider = new DsePlainTextAuthProvider("cassandra", "cassandra");
        DseCluster cluster = clusterBuilder().withAuthProvider(authProvider).build();
        tryConnect(cluster);
    }

    private DseCluster.Builder clusterBuilder() {
        return DseCluster.builder().addContactPointsWithPorts(this.getContactPointsWithPorts());
    }

    private void tryConnect(DseCluster cluster) {
        try {
            Statement statement = new SimpleStatement("select * from system.local");
            DseSession session = cluster.connect();
            Row row = session.execute(statement).one();
            assertThat(row).isNotNull();
        } finally {
            cluster.close();
        }
    }

}
