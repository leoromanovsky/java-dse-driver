/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.mapping;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;

/**
 * A bound statement that was generated by the object mapper.
 */
class MapperBoundStatement extends BoundStatement {
    public MapperBoundStatement(PreparedStatement statement) {
        super(statement);
    }
}
