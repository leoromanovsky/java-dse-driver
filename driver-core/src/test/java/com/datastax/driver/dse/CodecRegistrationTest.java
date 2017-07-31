/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse;

import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.MemoryAppender;
import com.datastax.driver.core.exceptions.CodecNotFoundException;
import com.datastax.driver.dse.geometry.codecs.LineStringCodec;
import com.datastax.driver.dse.geometry.codecs.PointCodec;
import com.datastax.driver.dse.geometry.codecs.PolygonCodec;
import com.datastax.driver.dse.search.DateRangeCodec;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class CodecRegistrationTest {

    @Test(groups = "unit")
    public void should_not_register_geospatial_codecs_when_explicitly_disabled() throws Exception {
        DseCluster cluster = DseCluster.builder()
            .addContactPoint("localhost")
            .withCodecRegistry(new CodecRegistry())
            .withoutGeospatialCodecs()
            .build();
        CodecRegistry codecRegistry = cluster.getConfiguration().getCodecRegistry();
        try {
            codecRegistry.codecFor(PointCodec.INSTANCE.getCqlType(), PointCodec.INSTANCE.getJavaType());
            fail("Expecting CodecNotFoundException");
        } catch (CodecNotFoundException ignored) {
        }
        try {
            codecRegistry.codecFor(LineStringCodec.INSTANCE.getCqlType(), LineStringCodec.INSTANCE.getJavaType());
            fail("Expecting CodecNotFoundException");
        } catch (CodecNotFoundException ignored) {
        }
        try {
            codecRegistry.codecFor(PolygonCodec.INSTANCE.getCqlType(), PolygonCodec.INSTANCE.getJavaType());
            fail("Expecting CodecNotFoundException");
        } catch (CodecNotFoundException ignored) {
        }
    }

    @Test(groups = "unit")
    public void should_not_register_search_codecs_when_explicitly_disabled() throws Exception {
        DseCluster cluster = DseCluster.builder()
            .addContactPoint("localhost")
            .withCodecRegistry(new CodecRegistry())
            .withoutSearchCodecs()
            .build();
        CodecRegistry codecRegistry = cluster.getConfiguration().getCodecRegistry();
        try {
            codecRegistry.codecFor(DateRangeCodec.INSTANCE.getCqlType(), DateRangeCodec.INSTANCE.getJavaType());
            fail("Expecting CodecNotFoundException");
        } catch (CodecNotFoundException ignored) {
        }
    }

    @Test(groups = "unit")
    public void should_not_log_warning_about_previously_registered_codec() {
        Logger codecRegistryLogger = Logger.getLogger(CodecRegistry.class);
        MemoryAppender logs = new MemoryAppender();
        Level originalLevel = codecRegistryLogger.getLevel();
        codecRegistryLogger.setLevel(Level.WARN);
        codecRegistryLogger.addAppender(logs);

        try {
            // Create cluster repeatedly.
            for (int i = 0; i < 10; i++) {
                DseCluster.builder()
                        .addContactPoint("localhost")
                        .build();
            }
        } finally {
            codecRegistryLogger.setLevel(originalLevel);
            codecRegistryLogger.removeAppender(logs);
        }

        // ensure warning was not emitted indicating attempt to register codec twice.
        assertThat(logs.get()).doesNotContain("because it collides with previously registered codec");
    }
}
