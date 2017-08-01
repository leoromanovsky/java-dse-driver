/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.graph;

import com.datastax.driver.core.CCMBridge;
import com.datastax.driver.core.CCMConfig;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.VersionNumber;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.exceptions.InvalidQueryException;
import com.datastax.driver.core.exceptions.UnavailableException;
import com.datastax.driver.core.utils.DseVersion;
import org.testng.annotations.Test;

import java.util.List;

import static com.datastax.driver.core.ConsistencyLevel.ALL;
import static com.datastax.driver.core.ConsistencyLevel.ONE;
import static com.datastax.driver.dse.graph.GraphAssertions.assertThat;

@CCMConfig(numberOfNodes = 3, dirtiesContext = true)
@DseVersion("5.0.0")
public class GraphConsistencyTest extends CCMGraphTestsSupport {

    private static final boolean isDSE50;

    static {
        VersionNumber dseVersion = CCMBridge.getGlobalDSEVersion();
        isDSE50 = dseVersion != null && dseVersion.getMajor() == 5 && dseVersion.getMinor() == 0;
    }

    @Override
    public CCMBridge.Builder configureCCM() {
        CCMBridge.Builder builder = super.configureCCM().withNodes(3);
        for (int i = 1; i <= 3; i++) {
            builder = builder.withWorkload(i, "graph")
                    .withDSEConfiguration("graph.gremlin_server.port", "0");
        }
        return builder;
    }

    @Override
    public void onTestContextInitialized() {
        // Create graph with RF 3.
        createAndSetGraphConfig(3);
        // Set temporarily high read timeout to deal with schema changes made across nodes.
        cluster().getConfiguration().getSocketOptions().setReadTimeoutMillis(32000);
        executeGraph(GraphFixtures.modern);
        // Execute a traversal, which triggers some schema operations, needed before taking a node down.
        session().executeGraph("g.V().limit(1)");
        cluster().getConfiguration().getSocketOptions().setReadTimeoutMillis(12000);
        // Stop node 2.
        ccm().stop(2);
    }

    /**
     * Validates that when a node is down and a graph traversal that only requires reads is made with a
     * CL of {@link ConsistencyLevel#ONE} using {@link GraphStatement#setGraphReadConsistencyLevel(ConsistencyLevel)}
     * that the query is successful.
     *
     * @test_category dse:graph
     * @jira_ticket JAVA-1104
     */
    @Test(groups = "long")
    public void should_be_able_to_make_read_query_with_graph_read_cl_one_and_node_down() {
        GraphResultSet result = session().executeGraph(new SimpleGraphStatement("g.V().limit(1)")
                .setGraphReadConsistencyLevel(ONE)
                .setGraphWriteConsistencyLevel(ALL)
                .setConsistencyLevel(ALL));

        assertThat(result.all().size()).isEqualTo(1);
    }

    /**
     * Validates that when a node is down and a graph traversal that only requires reads is made with a
     * * CL of {@link ConsistencyLevel#ALL} using {@link GraphStatement#setGraphReadConsistencyLevel(ConsistencyLevel)}
     * that the query is unsuccessful because not all replicas are available.
     *
     * @test_category dse:graph
     * @jira_ticket JAVA-1104
     */
    @Test(groups = "long")
    public void should_be_able_to_make_read_query_with_cl_one_and_node_down() {
        GraphResultSet result = session().executeGraph(new SimpleGraphStatement("g.V().limit(1)")
                .setConsistencyLevel(ONE));

        assertThat(result.all().size()).isEqualTo(1);
    }

    /**
     * Validates that when a node is down and a graph traversal that only requires reads is made with a
     * CL of {@link ConsistencyLevel#ONE} using {@link GraphStatement#setConsistencyLevel(ConsistencyLevel)}
     * that the query is successful.
     *
     * @test_category dse:graph
     * @jira_ticket JAVA-1104
     */
    @Test(groups = "long")
    public void should_not_be_able_to_make_read_query_with_graph_read_cl_all_and_node_down() {
        try {
            session().executeGraph(new SimpleGraphStatement("g.V().limit(1)")
                    .setGraphReadConsistencyLevel(ALL)
                    .setGraphWriteConsistencyLevel(ONE)
                    .setConsistencyLevel(ONE));
        } catch (DriverException e) {
            assertConsistencyException(e);
        }
    }

    /**
     * Validates that when a node is down and a graph traversal that only requires reads is made with a
     * CL of {@link ConsistencyLevel#ALL} using {@link GraphStatement#setConsistencyLevel(ConsistencyLevel)}
     * that the query is unsuccessful because not all replicas are available.
     *
     * @test_category dse:graph
     * @jira_ticket JAVA-1104
     */
    @Test(groups = "long")
    public void should_not_be_able_to_make_read_query_with_cl_all_and_node_down() {
        try {
            session().executeGraph(new SimpleGraphStatement("g.V().limit(1)")
                    .setConsistencyLevel(ALL));
        } catch (DriverException e) {
            assertConsistencyException(e);
        }
    }

    /**
     * Validates that when a node is down and a statement is executed that creates a vertex is made with a
     * CL of {@link ConsistencyLevel#ONE} using {@link GraphStatement#setGraphWriteConsistencyLevel(ConsistencyLevel)}
     * that the query is successful.
     *
     * @test_category dse:graph
     * @jira_ticket JAVA-1104
     */
    @Test(groups = "long")
    public void should_be_able_to_make_write_query_with_graph_write_cl_one_and_node_down() {
        GraphResultSet result = session().executeGraph(new SimpleGraphStatement("graph.addVertex(label, 'person', 'name', 'don', 'age', 37)")
                .setGraphWriteConsistencyLevel(ONE)
                .setGraphReadConsistencyLevel(ALL)
                .setConsistencyLevel(ALL));

        List<GraphNode> results = result.all();
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0)).asVertex().hasProperty("name", "don");
    }

    /**
     * Validates that when a node is down and a statement is executed that creates a vertex is made with a
     * CL of {@link ConsistencyLevel#ONE} using {@link GraphStatement#setConsistencyLevel(ConsistencyLevel)}
     * that the query is successful.
     *
     * @test_category dse:graph
     * @jira_ticket JAVA-1104
     */
    @Test(groups = "long")
    public void should_be_able_to_make_write_query_with_cl_one_and_node_down() {
        GraphResultSet result = session().executeGraph(new SimpleGraphStatement("graph.addVertex(label, 'person', 'name', 'don2', 'age', 37)")
                .setConsistencyLevel(ONE));

        List<GraphNode> results = result.all();
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0)).asVertex().hasProperty("name", "don2");
    }

    /**
     * Validates that when a node is down and a statement is executed that creates a vertex is made with a
     * CL of {@link ConsistencyLevel#ALL} using {@link GraphStatement#setGraphWriteConsistencyLevel(ConsistencyLevel)}
     * that the query is unsuccessful because not all replicas are available.
     *
     * @test_category dse:graph
     * @jira_ticket JAVA-1104
     */
    @Test(groups = "long")
    public void should_not_be_able_to_make_write_query_with_graph_write_cl_all_and_node_down() {
        try {
            session().executeGraph(new SimpleGraphStatement("graph.addVertex(label, 'person', 'name', 'joe', 'age', 42)")
                    .setGraphWriteConsistencyLevel(ALL)
                    .setGraphReadConsistencyLevel(ONE)
                    .setConsistencyLevel(ONE));
        } catch (DriverException e) {
            assertConsistencyException(e);
        }
    }

    /**
     * Validates that when a node is down and a statement is executed that creates a vertex is made with a
     * CL of {@link ConsistencyLevel#ALL} using {@link GraphStatement#setConsistencyLevel(ConsistencyLevel)}
     * that the query is unsuccessful because not all replicas are available.
     *
     * @test_category dse:graph
     * @jira_ticket JAVA-1104
     */
    @Test(groups = "long")
    public void should_not_be_able_to_make_write_query_with_cl_all_and_node_down() {
        try {
            session().executeGraph(new SimpleGraphStatement("graph.addVertex(label, 'person', 'name', 'joe', 'age', 42)")
                    .setConsistencyLevel(ALL));
        } catch (DriverException e) {
            assertConsistencyException(e);
        }
    }

    /**
     * Depending on the version of DSE a different error may be returned when consistency cannot be meant.  In DSE 5.0.x
     * and InvalidQueryException is expected, otherwise we expect UnavailableException, which is actually the error
     * happening at the C* level.
     */
    private void assertConsistencyException(DriverException e) {
        if (isDSE50) {
            assertThat(e).isInstanceOf(InvalidQueryException.class);
        } else {
            assertThat(e).isInstanceOf(UnavailableException.class);
        }
    }
}
