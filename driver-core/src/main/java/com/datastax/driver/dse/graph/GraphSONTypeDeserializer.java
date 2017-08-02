/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.dse.graph;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.impl.TypeDeserializerBase;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Contains main logic for the whole JSON to Java deserialization. Handles types embedded with the version 2.0 of GraphSON.
 */
class GraphSONTypeDeserializer extends TypeDeserializerBase {
    private final TypeIdResolver idRes;
    private final String propertyName;
    private final String valuePropertyName;
    private final JavaType baseType;
    private final TypeInfo typeInfo;

    private static final JavaType mapJavaType = TypeFactory.defaultInstance().constructType(LinkedHashMap.class);
    private static final JavaType arrayJavaType = TypeFactory.defaultInstance().constructType(ArrayList.class);


    GraphSONTypeDeserializer(final JavaType baseType, final TypeIdResolver idRes, final String typePropertyName,
                             final TypeInfo typeInfo, final String valuePropertyName){
        super(baseType, idRes, typePropertyName, false, null);
        this.baseType = baseType;
        this.idRes = idRes;
        this.propertyName = typePropertyName;
        this.typeInfo = typeInfo;
        this.valuePropertyName = valuePropertyName;
    }

    @Override
    public TypeDeserializer forProperty(BeanProperty beanProperty) {
        return this;
    }

    @Override
    public JsonTypeInfo.As getTypeInclusion() {
        return JsonTypeInfo.As.WRAPPER_ARRAY;
    }


    @Override
    public TypeIdResolver getTypeIdResolver() {
        return idRes;
    }

    @Override
    public Class<?> getDefaultImpl() {
        return null;
    }

    @Override
    public Object deserializeTypedFromObject(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        return deserialize(jsonParser, deserializationContext);
    }

    @Override
    public Object deserializeTypedFromArray(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        return deserialize(jsonParser, deserializationContext);
    }

    @Override
    public Object deserializeTypedFromScalar(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        return deserialize(jsonParser, deserializationContext);
    }

    @Override
    public Object deserializeTypedFromAny(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        return deserialize(jsonParser, deserializationContext);
    }

    /**
     * Main logic for the deserialization.
     */
    private Object deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final TokenBuffer buf = new TokenBuffer(jsonParser.getCodec(), false);
        final TokenBuffer localCopy = new TokenBuffer(jsonParser.getCodec(), false);

        // Detect type
        try {
            // The Type pattern is START_OBJECT -> TEXT_FIELD(propertyName) && TEXT_FIELD(valueProp).
            if (jsonParser.getCurrentToken() == JsonToken.START_OBJECT) {
                buf.writeStartObject();
                String typeName = null;
                boolean valueDetected = false;
                boolean valueDetectedFirst = false;

                for (int i = 0; i < 2; i++) {
                    String nextFieldName = jsonParser.nextFieldName();
                    if (nextFieldName == null) {
                        // empty map or less than 2 fields, go out.
                        break;
                    }
                    if (!nextFieldName.equals(this.propertyName) && !nextFieldName.equals(this.valuePropertyName)) {
                        // no type, go out.
                        break;
                    }

                    if (nextFieldName.equals(this.propertyName)) {
                        // detected "@type" field.
                        typeName = jsonParser.nextTextValue();
                        // keeping the spare buffer up to date in case it's a false detection (only the "@type" property)
                        buf.writeStringField(this.propertyName, typeName);
                        continue;
                    }
                    if (nextFieldName.equals(this.valuePropertyName)) {
                        // detected "@value" field.
                        jsonParser.nextValue();

                        if (typeName == null) {
                            // keeping the spare buffer up to date in case it's a false detection (only the "@value" property)
                            // the problem is that the fields "@value" and "@type" could be in any order
                            buf.writeFieldName(this.valuePropertyName);
                            valueDetectedFirst = true;
                            localCopy.copyCurrentStructure(jsonParser);
                        }
                        valueDetected = true;
                        continue;
                    }
                }

                if (typeName != null && valueDetected) {
                    // Type has been detected pattern detected.
                    final JavaType typeFromId = idRes.typeFromId(deserializationContext, typeName);

                    if (!baseType.isJavaLangObject() && !baseType.equals(typeFromId)) {
                        throw new InstantiationException(
                                String.format("Cannot deserialize the value with the detected type contained in the JSON ('%s') " +
                                        "to the type specified in parameter to the object mapper (%s). " +
                                        "Those types are incompatible.", typeName, baseType.getRawClass().toString())
                        );
                    }

                    final JsonDeserializer jsonDeserializer = deserializationContext.findContextualValueDeserializer(typeFromId, null);

                    JsonParser tokenParser;

                    if (valueDetectedFirst) {
                        tokenParser = localCopy.asParser();
                        tokenParser.nextToken();
                    } else {
                        tokenParser = jsonParser;
                    }

                    final Object value = jsonDeserializer.deserialize(tokenParser, deserializationContext);

                    final JsonToken t = jsonParser.nextToken();
                    if (t == JsonToken.END_OBJECT) {
                        // we're good to go
                        return value;
                    } else {
                        // detected the type pattern entirely but the Map contained other properties
                        // For now we error out because we assume that pattern is *only* reserved to
                        // typed values.
                        throw deserializationContext.mappingException("Detected the type pattern in the JSON payload " +
                                "but the map containing the types and values contains other fields. This is not " +
                                "allowed by the deserializer.");
                    }
                }
            }
        } catch (Exception e) {
            throw deserializationContext.mappingException("Could not deserialize the JSON value as required. Nested exception: " + e.toString());
        }

        // Type pattern wasn't detected, however,
        // while searching for the type pattern, we may have moved the cursor of the original JsonParser in param.
        // To compensate, we have filled consistently a TokenBuffer that should contain the equivalent of
        // what we skipped while searching for the pattern.
        // This has a huge positive impact on performances, since JsonParser does not have a 'rewind()',
        // the only other solution would have been to copy the whole original JsonParser. Which we avoid here and use
        // an efficient structure made of TokenBuffer + JsonParserSequence/Concat.
        // Concatenate buf + localCopy + end of original content(jsonParser).
        final JsonParser[] concatenatedArray = {buf.asParser(), localCopy.asParser(), jsonParser};
        final JsonParser parserToUse = new JsonParserConcat(concatenatedArray);
        parserToUse.nextToken();

        // If a type has been specified in parameter, use it to find a deserializer and deserialize:
        if (!baseType.isJavaLangObject()) {
            final JsonDeserializer jsonDeserializer = deserializationContext.findContextualValueDeserializer(baseType, null);
            return jsonDeserializer.deserialize(parserToUse, deserializationContext);
        }
        // Otherwise, detect the current structure:
        else {
            if (parserToUse.isExpectedStartArrayToken()) {
                return deserializationContext.findContextualValueDeserializer(arrayJavaType, null).deserialize(parserToUse, deserializationContext);
            } else if (parserToUse.isExpectedStartObjectToken()) {
                return deserializationContext.findContextualValueDeserializer(mapJavaType, null).deserialize(parserToUse, deserializationContext);
            } else {
                // There's "java.lang.Object" in param, there's no type detected in the payload, the payload isn't a JSON Map or JSON List
                // then consider it a simple type, even though we shouldn't be here if it was a simple type.
                // TODO : maybe throw an error instead?
                // throw deserializationContext.mappingException("Roger, we have a problem deserializing");
                final JsonDeserializer jsonDeserializer = deserializationContext.findContextualValueDeserializer(baseType, null);
                return jsonDeserializer.deserialize(parserToUse, deserializationContext);
            }
        }
    }

    private boolean canReadTypeId() {
        return this.typeInfo == TypeInfo.PARTIAL_TYPES;
    }
}
