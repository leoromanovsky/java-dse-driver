/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.dse.graph.internal.utils;

import com.datastax.driver.core.VersionNumber;
import org.testng.SkipException;

public class DseVersionCheckUtils {
    public static void checkDse51Version(VersionNumber actual, VersionNumber minimumRequired) {
        if (actual.getMinor() == 1 && actual.getPatch() < minimumRequired.getPatch()) {
            throw new SkipException(String.format("Minimum DSE Version required was %s but version given was %s.", minimumRequired.toString(), actual.toString()));
        }
    }
}
