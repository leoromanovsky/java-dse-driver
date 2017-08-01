/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.graph;

import com.datastax.driver.core.CCMBridge;
import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.VersionNumber;
import com.datastax.driver.core.utils.DseVersion;
import com.datastax.driver.core.utils.UUIDs;
import com.datastax.driver.dse.geometry.LineString;
import com.datastax.driver.dse.geometry.Polygon;
import com.google.common.base.Charsets;
import com.google.common.net.InetAddresses;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.datastax.driver.dse.geometry.Utils.p;
import static com.datastax.driver.dse.graph.GraphAssertions.assertThat;

public abstract class GraphDataTypeIntegrationTest extends CCMGraphTestsSupport {

    Logger logger = LoggerFactory.getLogger(GraphDataTypeIntegrationTest.class);

    AtomicInteger schemaCounter = new AtomicInteger(0);

    /**
     * Whether or not to skip testing a given type.  May be done for various reasons (graph protocol used,
     * type being broken for certain versions, etc.)
     *
     * @param type Type to evaluate.
     * @return Whether or not to skip test for type.
     */
    boolean filterType(String type) {
        return false;
    }

    @DataProvider
    public static Object[][] dataTypeSamples() {
        return new Object[][]{
                // Types that DSE supports.
                {"Boolean()", true},
                {"Boolean()", false},
                {"Smallint()", Short.MAX_VALUE},
                {"Smallint()", Short.MIN_VALUE},
                {"Smallint()", (short) 0},
                {"Smallint()", (short) 42},
                {"Int()", Integer.MAX_VALUE},
                {"Int()", Integer.MIN_VALUE},
                {"Int()", 0},
                {"Int()", 42},
                {"Bigint()", Long.MAX_VALUE},
                {"Bigint()", Long.MIN_VALUE},
                {"Bigint()", 0L},
                {"Double()", Double.MAX_VALUE},
                {"Double()", Double.MIN_VALUE},
                {"Double()", 0.0d},
                {"Double()", Math.PI},
                {"Float()", Float.MAX_VALUE},
                {"Float()", Float.MIN_VALUE},
                {"Float()", 0.0f},
                {"Text()", ""},
                {"Text()", "75"},
                {"Text()", "Lorem Ipsum"},
                // Inet, UUID, Date
                {"Inet()", InetAddresses.forString("127.0.0.1")},
                {"Inet()", InetAddresses.forString("0:0:0:0:0:0:0:1")},
                {"Inet()", InetAddresses.forString("2001:db8:85a3:0:0:8a2e:370:7334")},
                {"Uuid()", UUID.randomUUID()},
                {"Uuid()", UUIDs.timeBased()},
                // Timestamps
                {"Timestamp()", new Date(123)},
                {"Timestamp()", new Date(1488313909)},
                // Blob
                {"Blob()", "Hello World!".getBytes(Charsets.UTF_8)},
                // BigDecimal/BigInteger
                {"Decimal()", new BigDecimal("8675309.9998")},
                {"Varint()", new BigInteger("8675309")},
                // Geospatial types
                {"Point().withBounds(-2, -2, 2, 2)", p(0, 1)},
                {"Point().withBounds(-40, -40, 40, 40)", p(-5, 20)},
                {"Linestring().withGeoBounds()", new LineString(p(30, 10), p(10, 30), p(40, 40))},
                {"Polygon().withGeoBounds()", Polygon.builder()
                        .addRing(p(35, 10), p(45, 45), p(15, 40), p(10, 20), p(35, 10))
                        .addRing(p(20, 30), p(35, 35), p(30, 20), p(20, 30))
                        .build()}
        };
    }

    @DataProvider
    public static Object[][] dataTypeSamples51() {
        return new Object[][]{
                {"Date()", LocalDate.fromYearMonthDay(2016, 5, 12)},
                {"Date()", "1999-07-29"},
                {"Time()", "18:30:41.554"},
                {"Time()", "18:30:41.554010034"}
        };
    }

    // Identify geotypes using bounds and capture everything preceding the bounds definition.
    private static final Pattern withBoundsPattern = Pattern.compile("^(.*\\(\\))\\.with.*Bounds.*$");

    /**
     * Validates that a given data sample can be set as a parameter on Statement and interpreted as a given type for a vertex
     * property.  Does the following:
     * <ol>
     * <li>Define a property of the given type.</li>
     * <li>Create a vertex for each data sample with that property having that data sample value.</li>
     * <li>Validates that the result contains the created vertex with that property and that the property can be retrieved in the same format it was inserted in.</li>
     * <li>For completeness, queries the vertex and ensures the property value matches that which was inserted.</li>
     * </ol>
     *
     * @test_category dse:graph
     */
    @Test(groups = "short", dataProvider = "dataTypeSamples")
    public void should_create_and_retrieve_vertex_property(String type, Object input) {
        if (filterType(type)) {
            throw new SkipException("Type " + type + " marked as filtered for this test.");
        }
        int id = schemaCounter.incrementAndGet();
        // If we're working with a geotype and our version is 5.0, make a special exception and truncate the
        // withBounds/withGeoBounds qualifiers.
        VersionNumber dseVersion = CCMBridge.getGlobalDSEVersion();
        if (dseVersion != null && dseVersion.getMajor() == 5 && dseVersion.getMinor() == 0) {
            Matcher matcher = withBoundsPattern.matcher(type);
            if (matcher.matches()) {
                type = matcher.group(1);
                logger.warn("Replacing type definition '{}' with '{}' for DSE 5.0", matcher.group(), type);
            }
        }
        String vertexLabel = "vertex" + id;
        String propertyName = "prop" + id;
        GraphStatement addVertexLabelAndProperty = new SimpleGraphStatement(
                "schema.propertyKey(property)." + type + ".create()\n" +
                        "schema.vertexLabel(vertexLabel).properties(property).create()\n" +
                        "schema.vertexLabel(vertexLabel).index(property + 'Index').secondary().by(property).add()")
                .set("vertexLabel", vertexLabel)
                .set("property", propertyName);
        session().executeGraph(addVertexLabelAndProperty);

        GraphStatement addV = new SimpleGraphStatement("g.addV(label, vertexLabel, propertyName, val)")
                .set("vertexLabel", vertexLabel)
                .set("propertyName", propertyName)
                .set("val", input);
        GraphResultSet resultSet = session().executeGraph(addV);
        validateVertexResult(resultSet, vertexLabel, propertyName, input);

        // For completeness, retrieve the vertex and ensure the property value was maintained.
        GraphStatement getV = new SimpleGraphStatement("g.V().hasLabel(vertexLabel).has(propertyName, val).next()")
                .set("vertexLabel", vertexLabel)
                .set("propertyName", propertyName)
                .set("val", input);
        resultSet = session().executeGraph(getV);
        validateVertexResult(resultSet, vertexLabel, propertyName, input);
    }

    @Test(groups = "short", dataProvider = "dataTypeSamples51")
    @DseVersion("5.1.0")
    public void should_create_and_retrieve_vertex_property_51(String type, Object input) {
        should_create_and_retrieve_vertex_property(type, input);
    }

    @SuppressWarnings("unchecked")
    private void validateVertexResult(GraphResultSet resultSet, String vertexLabel, String propertyName, Object
            expectedResult) {
        // Ensure the created vertex is returned and the property value matches what was provided.
        List<GraphNode> results = resultSet.all();
        Assertions.assertThat(results.size()).isEqualTo(1);
        Vertex v = results.get(0).asVertex();
        VertexAssert a = assertThat(v).hasLabel(vertexLabel);

        // Validate using the appropriate asXXX method depending on the type of the class.
        if (expectedResult instanceof String) {
            a.hasProperty(propertyName, (String) expectedResult);
        } else if (expectedResult instanceof Integer) {
            a.hasProperty(propertyName, (Integer) expectedResult);
        } else if (expectedResult instanceof Boolean) {
            a.hasProperty(propertyName, (Boolean) expectedResult);
        } else if (expectedResult instanceof Long) {
            a.hasProperty(propertyName, (Long) expectedResult);
        } else if (expectedResult instanceof Double) {
            a.hasProperty(propertyName, (Double) expectedResult);
        }

        // Validate using the as(Clazz) method depending on the expectedClass.
        a.hasProperty(propertyName, expectedResult, (Class<Object>) expectedResult.getClass());
    }
}
