/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.dse.graph.internal.utils;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.dse.graph.GraphNode;
import com.datastax.driver.dse.graph.GraphStatement;
import com.datastax.driver.dse.graph.ObjectGraphNode;
import com.datastax.driver.dse.graph.SimpleGraphStatement;
import com.datastax.dse.graph.internal.serde.DriverObjectsModule;
import com.datastax.dse.graph.internal.serde.DseGraphModule;
import com.google.common.base.Function;
import com.google.common.base.Throwables;
import org.apache.tinkerpop.gremlin.process.traversal.Bytecode;
import org.apache.tinkerpop.gremlin.structure.io.GraphReader;
import org.apache.tinkerpop.gremlin.structure.io.GraphWriter;
import org.apache.tinkerpop.gremlin.structure.io.graphson.*;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerIoRegistryV2d0;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@SuppressWarnings("ConstantConditions")
public class GraphSONUtils {

    public static final String BYTECODE_GRAPHSON_GRAPH_LANGUAGE = "bytecode-json";

    @SuppressWarnings("deprecation")
    private static final GraphSONMapper GRAPHSON_MAPPER_2_0 = GraphSONMapper.build()
            .version(GraphSONVersion.V2_0)
            .typeInfo(TypeInfo.PARTIAL_TYPES)
            .addRegistry(TinkerIoRegistryV2d0.getInstance())
            .addCustomModule(GraphSONXModuleV2d0.build().create(false))
            .addCustomModule(new DseGraphModule())
            .addCustomModule(new DriverObjectsModule())
            .create();

    private static final GraphWriter GRAPHSON_WRITER_2_0 = GraphSONWriter.build()
            .mapper(GRAPHSON_MAPPER_2_0)
            .create();

    private static final GraphReader GRAPHSON_READER_2_0 = GraphSONReader.build()
            .mapper(GRAPHSON_MAPPER_2_0)
            .create();

    public static final Function<Row, GraphNode> ROW_TO_GRAPHSON2_TINKERPOP_OBJECTGRAPHNODE = (input -> {
        if (input == null) {
            return null;
        }
        Object deserializedObject = null;
        if (input.getColumnDefinitions().contains("gremlin")) {
            try {
                deserializedObject = readStringAs(input.getString("gremlin"), Object.class);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
        return new ObjectGraphNode(deserializedObject);

    });

    public static <V> V readStringAs(String json, Class<V> destClass) throws IOException {
        try (final ByteArrayInputStream bais = new ByteArrayInputStream(json.getBytes("UTF-8"))) {
            return GRAPHSON_READER_2_0.readObject(bais, destClass);
        }
    }

    private static String writeValueAsString(Object valueToWrite) throws IOException {
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            GRAPHSON_WRITER_2_0.writeObject(out, valueToWrite);
            return out.toString("UTF-8");
        }
    }

    public static GraphStatement getStatementFromBytecode(Bytecode bytecode) {
        try {
            String serializedBytecode = writeValueAsString(bytecode);
            SimpleGraphStatement simpleGraphStatement = new SimpleGraphStatement(serializedBytecode);
            simpleGraphStatement.setGraphLanguage(BYTECODE_GRAPHSON_GRAPH_LANGUAGE);
            return simpleGraphStatement;
        } catch (IOException e) {
            throw new DriverException("Could not serialize Traversal's bytecode.", e);
        }
    }
}
