/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.graph;

final class GraphSONTokens {

    private GraphSONTokens() {
    }

    public static final String ID = "id";
    public static final String TYPE = "type";
    public static final String VALUE = "value";
    public static final String PROPERTIES = "properties";
    public static final String KEY = "key";
    public static final String IN = "inV";
    public static final String OUT = "outV";
    public static final String LABEL = "label";
    public static final String LABELS = "labels";
    public static final String OBJECTS = "objects";
    public static final String IN_LABEL = "inVLabel";
    public static final String OUT_LABEL = "outVLabel";

    public static final String BULK = "bulk";

    public static final String AND = "and";

}
