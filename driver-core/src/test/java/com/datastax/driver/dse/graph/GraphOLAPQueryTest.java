/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.graph;

import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.utils.DseVersion;
import com.google.common.collect.Lists;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DseVersion("5.0.0")
public class GraphOLAPQueryTest extends CCMGraphTestsOLAPSupport {

    private Collection<Host> executeOLAPQuery(int times, String graphSource) {
        GraphStatement statement = new SimpleGraphStatement("g.V().count()");
        if (graphSource != null) {
            statement = statement.setGraphSource(graphSource);
        }
        Collection<Host> triedHosts = Lists.newArrayListWithCapacity(times);
        for (int i = 0; i < times; i++) {
            GraphResultSet result = session().executeGraph(statement);

            List<GraphNode> results = result.all();
            assertThat(results.size()).isEqualTo(1);
            GraphNode r = results.get(0);

            assertThat(r.asInt()).isEqualTo(6);

            ExecutionInfo executionInfo = result.getExecutionInfo();
            assertThat(executionInfo.getTriedHosts().size()).isGreaterThanOrEqualTo(1);
            triedHosts.add(executionInfo.getTriedHosts().get(0));
        }
        return triedHosts;
    }

    /**
     * Validates that when using the default load balancing policy that if you make a query with 'a' traversal source
     * that the {@link com.datastax.driver.dse.DseLoadBalancingPolicy} behavior kicks in and targets
     * the spark master as the primary query source.
     *
     * @test_category dse:graph
     * @jira_ticket JAVA-1098
     */
    @Test(groups = "long")
    public void should_target_analytics_node_with_analytics_source() {
        Host analyticsHost = findSparkMaster();
        assertThat(executeOLAPQuery(10, "a")).containsOnly(analyticsHost);
    }

    /**
     * Validates that when using the default load balancing policy that if you make a query with the default traversal
     * source that {@link com.datastax.driver.dse.DseLoadBalancingPolicy}
     * does not target the spark master as the primary query source.
     *
     * @test_category dse:graph
     * @jira_ticket JAVA-1098
     */
    @Test(groups = "long")
    public void should_not_target_analytics_node_with_default_source() {
        assertThat(executeOLAPQuery(10, "default")).containsAll(cluster().getMetadata().getAllHosts());
    }

    /**
     * Validates that when using the default load balancing policy that if you make a query without providing a
     * traversal source that {@link com.datastax.driver.dse.DseLoadBalancingPolicy}
     * does not target the spark master as the primary query source.
     *
     * @test_category dse:graph
     * @jira_ticket JAVA-1098
     */
    @Test(groups = "long")
    public void should_not_target_analytics_node_by_default() {
        assertThat(executeOLAPQuery(10, null)).containsAll(cluster().getMetadata().getAllHosts());
    }
}
