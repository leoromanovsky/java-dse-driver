/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.graph;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class GraphSON2GremlinDriverModule extends GraphSON2JacksonModule {

    GraphSON2GremlinDriverModule() {
        super("graph-graphson2gremlin");
        addSerializer(Integer.class, new IntegerGraphSONSerializer());
        addSerializer(Double.class, new DoubleGraphSONSerializer());

        addDeserializer(Vertex.class, new VertexGraphSON2Deserializer());
        addDeserializer(VertexProperty.class, new VertexPropertyGraphSON2Deserializer());
        addDeserializer(Property.class, new PropertyGraphSON2Deserializer());
        addDeserializer(Edge.class, new EdgeGraphSON2Deserializer());
        addDeserializer(Path.class, new PathGraphSON2Deserializer());
    }

    @Override
    public Map<Class<?>, String> getTypeDefinitions() {
        // Override the TinkerPop classes' types.
        final ImmutableMap.Builder<Class<?>, String> builder = ImmutableMap.builder();
        builder.put(Integer.class, "Int32");
        builder.put(Long.class, "Int64");
        builder.put(Double.class, "Double");
        builder.put(Float.class, "Float");

        builder.put(Vertex.class, "Vertex");
        builder.put(VertexProperty.class, "VertexProperty");
        builder.put(Property.class, "Property");
        builder.put(Edge.class, "Edge");
        builder.put(Path.class, "Path");
        builder.put(List.class, "Tree");

        return builder.build();
    }

    @Override
    public String getTypeNamespace() {
        // Override the classes from the TinkerPop domain.
        return "g";
    }


    final static class IntegerGraphSONSerializer extends StdScalarSerializer<Integer> {
        IntegerGraphSONSerializer() {
            super(Integer.class);
        }

        @Override
        public void serialize(final Integer integer, final JsonGenerator jsonGenerator,
                              final SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeNumber(((Integer) integer).intValue());
        }
    }

    final static class DoubleGraphSONSerializer extends StdScalarSerializer<Double> {
        DoubleGraphSONSerializer() {
            super(Double.class);
        }

        @Override
        public void serialize(final Double doubleValue, final JsonGenerator jsonGenerator,
                              final SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeNumber(doubleValue);
        }
    }

    final static class VertexGraphSON2Deserializer extends StdDeserializer<Vertex> {

        VertexGraphSON2Deserializer() {
            super(Vertex.class);
        }

        @Override
        public Vertex deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            final DefaultVertex v = new DefaultVertex();
            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                if (jsonParser.getCurrentName().equals(GraphSONTokens.ID)) {
                    jsonParser.nextToken();
                    v.id = new ObjectGraphNode(deserializationContext.readValue(jsonParser, Object.class));
                } else if (jsonParser.getCurrentName().equals(GraphSONTokens.LABEL)) {
                    jsonParser.nextToken();
                    v.label = jsonParser.getText();
                } else if (jsonParser.getCurrentName().equals(GraphSONTokens.PROPERTIES)) {
                    jsonParser.nextToken();
                    ImmutableMultimap.Builder<String, GraphNode> builder = ImmutableMultimap.builder();
                    while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                        jsonParser.nextToken();
                        while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
                            DefaultVertexProperty vp = (DefaultVertexProperty) deserializationContext.readValue(jsonParser, VertexProperty.class);
                            vp.parent = v;
                            builder.put(vp.getName(), new ObjectGraphNode(vp));
                        }
                    }
                    v.properties = builder.build();
                }
            }
            return v;
        }

        @Override
        public boolean isCachable() {
            return true;
        }
    }

    final static class VertexPropertyGraphSON2Deserializer extends StdDeserializer<VertexProperty> {

        VertexPropertyGraphSON2Deserializer() {
            super(VertexProperty.class);
        }

        @Override
        public VertexProperty deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            final DefaultVertexProperty vp = new DefaultVertexProperty();

            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                if (jsonParser.getCurrentName().equals(GraphSONTokens.ID)) {
                    jsonParser.nextToken();
                    vp.id = new ObjectGraphNode(deserializationContext.readValue(jsonParser, Object.class));
                } else if (jsonParser.getCurrentName().equals(GraphSONTokens.LABEL)) {
                    jsonParser.nextToken();
                    vp.label = jsonParser.getText();
                } else if (jsonParser.getCurrentName().equals(GraphSONTokens.VALUE)) {
                    jsonParser.nextToken();
                    vp.value = new ObjectGraphNode(deserializationContext.readValue(jsonParser, Object.class));
                } else if (jsonParser.getCurrentName().equals(GraphSONTokens.PROPERTIES)) {
                    jsonParser.nextToken();
                    ImmutableMultimap.Builder<String, GraphNode> builder = ImmutableMultimap.builder();
                    while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                        final String key = jsonParser.getCurrentName();
                        jsonParser.nextToken();
                        final Object val = deserializationContext.readValue(jsonParser, Object.class);
                        DefaultProperty prop = new DefaultProperty();
                        prop.name = key;
                        prop.value = new ObjectGraphNode(val);
                        prop.parent = vp;
                        builder.put(prop.name, new ObjectGraphNode(prop));
                    }
                    vp.properties = builder.build();
                }
            }
            return vp;
        }

        @Override
        public boolean isCachable() {
            return true;
        }
    }

    final static class EdgeGraphSON2Deserializer extends StdDeserializer<Edge> {

        EdgeGraphSON2Deserializer() {
            super(Edge.class);
        }

        @Override
        public Edge deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            final DefaultEdge e = new DefaultEdge();
            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                if (jsonParser.getCurrentName().equals(GraphSONTokens.ID)) {
                    jsonParser.nextToken();
                    e.id = new ObjectGraphNode(deserializationContext.readValue(jsonParser, Object.class));
                } else if (jsonParser.getCurrentName().equals(GraphSONTokens.LABEL)) {
                    jsonParser.nextToken();
                    e.label = jsonParser.getText();
                } else if (jsonParser.getCurrentName().equals(GraphSONTokens.OUT)) {
                    jsonParser.nextToken();
                    e.outV = new ObjectGraphNode(deserializationContext.readValue(jsonParser, Object.class));
                } else if (jsonParser.getCurrentName().equals(GraphSONTokens.OUT_LABEL)) {
                    jsonParser.nextToken();
                    e.outVLabel = jsonParser.getText();
                } else if (jsonParser.getCurrentName().equals(GraphSONTokens.IN)) {
                    jsonParser.nextToken();
                    e.inV = new ObjectGraphNode(deserializationContext.readValue(jsonParser, Object.class));
                } else if (jsonParser.getCurrentName().equals(GraphSONTokens.IN_LABEL)) {
                    jsonParser.nextToken();
                    e.inVLabel = jsonParser.getText();
                } else if (jsonParser.getCurrentName().equals(GraphSONTokens.PROPERTIES)) {
                    jsonParser.nextToken();
                    ImmutableMultimap.Builder<String, GraphNode> builder = ImmutableMultimap.builder();
                    while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                        jsonParser.nextToken();
                        DefaultProperty prop = (DefaultProperty) deserializationContext.readValue(jsonParser, Property.class);
                        prop.parent = e;
                        builder.put(prop.name, new ObjectGraphNode(prop));
                    }
                    e.properties = builder.build();
                }
            }
            return e;
        }

        @Override
        public boolean isCachable() {
            return true;
        }
    }

    final static class PropertyGraphSON2Deserializer extends StdDeserializer<Property> {
        PropertyGraphSON2Deserializer() {
            super(Property.class);
        }

        @Override
        public Property deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            final DefaultProperty prop = new DefaultProperty();
            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                if (jsonParser.getCurrentName().equals(GraphSONTokens.KEY)) {
                    jsonParser.nextToken();
                    prop.name = jsonParser.getText();
                } else if (jsonParser.getCurrentName().equals(GraphSONTokens.VALUE)) {
                    jsonParser.nextToken();
                    prop.value = new ObjectGraphNode(deserializationContext.readValue(jsonParser, Object.class));
                }
            }
            return prop;
        }

        @Override
        public boolean isCachable() {
            return true;
        }
    }

    final static class PathGraphSON2Deserializer extends StdDeserializer<Path> {
        private static final JavaType setType = TypeFactory.defaultInstance().constructCollectionType(HashSet.class, String.class);

        PathGraphSON2Deserializer() {
            super(Path.class);
        }

        @Override
        public Path deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            final JsonNode n = jsonParser.readValueAsTree();
            final DefaultPath p = new DefaultPath();

            final ArrayNode objects = (ArrayNode) n.get(GraphSONTokens.OBJECTS);
            final ArrayNode labels = (ArrayNode) n.get(GraphSONTokens.LABELS);

            final List<GraphNode> objectsList = new ArrayList<GraphNode>();
            final List<Set<String>> labelsList = new ArrayList<Set<String>>();

            for (int i = 0; i < objects.size(); i++) {
                final JsonParser po = objects.get(i).traverse();
                po.nextToken();
                final JsonParser pl = labels.get(i).traverse();
                pl.nextToken();

                objectsList.add(new ObjectGraphNode(deserializationContext.readValue(po, Object.class)));
                labelsList.add((Set<String>)deserializationContext.readValue(pl, setType));
            }
            p.objects = objectsList;
            p.labels = labelsList;

            return p;
        }

        @Override
        public boolean isCachable() {
            return true;
        }
    }


}
