/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.core;

import com.datastax.driver.core.policies.LoadBalancingPolicy;
import com.google.common.collect.Iterators;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Iterator;

import static com.datastax.driver.core.CreateCCM.TestMode.PER_METHOD;
import static org.testng.Assert.assertTrue;

// Test that PoolingOpions.refreshConnectedHosts works as expected (JAVA-309)
@CreateCCM(PER_METHOD)
public class LoadBalancingPolicyRefreshTest extends CCMTestsSupport {

    UpdatablePolicy policy;

    private class UpdatablePolicy implements LoadBalancingPolicy {

        private Cluster cluster;
        private Host theHost;

        public void changeTheHost(Host theNewHost) {
            this.theHost = theNewHost;
            cluster.getConfiguration().getPoolingOptions().refreshConnectedHosts();
        }

        public void init(Cluster cluster, Collection<Host> hosts) {
            this.cluster = cluster;
            try {
                for (Host h : hosts)
                    if (h.getAddress().equals(InetAddress.getByName(TestUtils.IP_PREFIX + '1')))
                        this.theHost = h;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public HostDistance distance(Host host) {
            return host == theHost ? HostDistance.LOCAL : HostDistance.IGNORED;
        }

        public Iterator<Host> newQueryPlan(String loggedKeyspace, Statement statement) {
            return Iterators.<Host>singletonIterator(theHost);
        }

        public void onAdd(Host h) {
        }

        public void onRemove(Host h) {
        }

        public void onUp(Host h) {
        }

        public void onDown(Host h) {
        }

        public void close() {
        }
    }

    @Test(groups = "short")
    @CCMConfig(numberOfNodes = 2, clusterProvider = "updatablePolicy")
    public void refreshTest() throws Throwable {
        // Ugly
        Host[] hosts = new Host[2];
        for (Host h : cluster().getMetadata().getAllHosts()) {
            if (h.getAddress().equals(ccm().addressOfNode(1).getAddress()))
                hosts[0] = h;
            else
                hosts[1] = h;
        }

        assertTrue(session().getState().getConnectedHosts().contains(hosts[0]), "Connected hosts: " + session().getState().getConnectedHosts());
        assertTrue(!session().getState().getConnectedHosts().contains(hosts[1]), "Connected hosts: " + session().getState().getConnectedHosts());

        policy.changeTheHost(hosts[1]);

        assertTrue(!session().getState().getConnectedHosts().contains(hosts[0]), "Connected hosts: " + session().getState().getConnectedHosts());
        assertTrue(session().getState().getConnectedHosts().contains(hosts[1]), "Connected hosts: " + session().getState().getConnectedHosts());
    }

    @SuppressWarnings("unused")
    private Cluster.Builder updatablePolicy() {
        policy = new UpdatablePolicy();
        return Cluster.builder().withLoadBalancingPolicy(policy);
    }
}
