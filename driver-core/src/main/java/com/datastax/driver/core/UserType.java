/*
 * Copyright (C) 2012-2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.driver.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;

import java.util.*;

/**
 * A User Defined Type (UDT).
 * <p/>
 * A UDT is a essentially a named collection of fields (with a name and a type).
 */
public class UserType extends DataType implements Iterable<UserType.Field> {

    static final String TYPE_NAME = "type_name";
    private static final String COLS_NAMES = "field_names";
    static final String COLS_TYPES = "field_types";

    private final String keyspace;
    private final String typeName;
    private final boolean frozen;
    private final ProtocolVersion protocolVersion;

    // can be null, if this object is being constructed from a response message
    // see Responses.Result.Rows.Metadata.decode()
    private volatile CodecRegistry codecRegistry;

    // Note that we don't expose the order of fields, from an API perspective this is a map
    // of String->Field, but internally we care about the order because the serialization format
    // of UDT expects a particular order.
    final Field[] byIdx;
    // For a given name, we can only have one field with that name, so we don't need a int[] in
    // practice. However, storing one element arrays save allocations in UDTValue.getAllIndexesOf
    // implementation.
    final Map<String, int[]> byName;

    private UserType(Name name, String keyspace, String typeName, boolean frozen, ProtocolVersion protocolVersion, CodecRegistry codecRegistry, Field[] byIdx, Map<String, int[]> byName) {
        super(name);
        this.keyspace = keyspace;
        this.typeName = typeName;
        this.frozen = frozen;
        this.protocolVersion = protocolVersion;
        this.codecRegistry = codecRegistry;
        this.byIdx = byIdx;
        this.byName = byName;
    }

    UserType(String keyspace, String typeName, boolean frozen, Collection<Field> fields, ProtocolVersion protocolVersion, CodecRegistry codecRegistry) {
        this(DataType.Name.UDT, keyspace, typeName, frozen, protocolVersion, codecRegistry,
                fields.toArray(new Field[fields.size()]),
                mapByName(fields));
    }

    private static ImmutableMap<String, int[]> mapByName(Collection<Field> fields) {
        ImmutableMap.Builder<String, int[]> builder = new ImmutableMap.Builder<String, int[]>();
        int i = 0;
        for (Field field : fields) {
            builder.put(field.getName(), new int[]{i});
            i += 1;
        }
        return builder.build();
    }

    static UserType build(KeyspaceMetadata ksm, Row row, VersionNumber version, Cluster cluster, Map<String, UserType> userTypes) {
        ProtocolVersion protocolVersion = cluster.getConfiguration().getProtocolOptions().getProtocolVersion();
        CodecRegistry codecRegistry = cluster.getConfiguration().getCodecRegistry();

        String keyspace = row.getString(KeyspaceMetadata.KS_NAME);
        String name = row.getString(TYPE_NAME);

        List<String> fieldNames = row.getList(COLS_NAMES, String.class);
        List<String> fieldTypes = row.getList(COLS_TYPES, String.class);

        List<Field> fields = new ArrayList<Field>(fieldNames.size());
        for (int i = 0; i < fieldNames.size(); i++) {
            DataType fieldType;
            if (version.getMajor() >= 3.0) {
                fieldType = DataTypeCqlNameParser.parse(fieldTypes.get(i), cluster, ksm.getName(), userTypes, ksm.userTypes, false, false);
            } else {
                fieldType = DataTypeClassNameParser.parseOne(fieldTypes.get(i), protocolVersion, codecRegistry);
            }
            fields.add(new Field(fieldNames.get(i), fieldType));
        }
        return new UserType(keyspace, name, false, fields, protocolVersion, codecRegistry);
    }

    /**
     * Returns a new empty value for this user type definition.
     *
     * @return an empty value for this user type definition.
     */
    public UDTValue newValue() {
        return new UDTValue(this);
    }

    /**
     * The name of the keyspace this UDT is part of.
     *
     * @return the name of the keyspace this UDT is part of.
     */
    public String getKeyspace() {
        return keyspace;
    }

    /**
     * The name of this user type.
     *
     * @return the name of this user type.
     */
    public String getTypeName() {
        return typeName;
    }

    /**
     * Returns the number of fields in this UDT.
     *
     * @return the number of fields in this UDT.
     */
    public int size() {
        return byIdx.length;
    }

    /**
     * Returns whether this UDT contains a given field.
     *
     * @param name the name to check. Note that {@code name} obey the usual
     *             CQL identifier rules: it should be quoted if it denotes a case sensitive
     *             identifier (you can use {@link Metadata#quote} for the quoting).
     * @return {@code true} if this UDT contains a field named {@code name},
     * {@code false} otherwise.
     */
    public boolean contains(String name) {
        return byName.containsKey(Metadata.handleId(name));
    }

    /**
     * Returns an iterator over the fields of this UDT.
     *
     * @return an iterator over the fields of this UDT.
     */
    @Override
    public Iterator<Field> iterator() {
        return Iterators.forArray(byIdx);
    }

    /**
     * Returns the names of the fields of this UDT.
     *
     * @return the names of the fields of this UDT as a collection.
     */
    public Collection<String> getFieldNames() {
        return byName.keySet();
    }

    /**
     * Returns the type of a given field.
     *
     * @param name the name of the field. Note that {@code name} obey the usual
     *             CQL identifier rules: it should be quoted if it denotes a case sensitive
     *             identifier (you can use {@link Metadata#quote} for the quoting).
     * @return the type of field {@code name} if this UDT has a field of this
     * name, {@code null} otherwise.
     * @throws IllegalArgumentException if {@code name} is not a field of this
     *                                  UDT definition.
     */
    public DataType getFieldType(String name) {
        int[] idx = byName.get(Metadata.handleId(name));
        if (idx == null)
            throw new IllegalArgumentException(name + " is not a field defined in this definition");

        return byIdx[idx[0]].getType();
    }

    @Override
    public boolean isFrozen() {
        return frozen;
    }

    public UserType copy(boolean newFrozen) {
        if (newFrozen == frozen) {
            return this;
        } else {
            return new UserType(name, keyspace, typeName, newFrozen, protocolVersion, codecRegistry, byIdx, byName);
        }
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + keyspace.hashCode();
        result = 31 * result + typeName.hashCode();
        result = 31 * result + Arrays.hashCode(byIdx);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UserType))
            return false;

        UserType other = (UserType) o;

        // Note: we don't test byName because it's redundant with byIdx in practice,
        // but also because the map holds 'int[]' which don't have proper equal.
        return name.equals(other.name)
                && keyspace.equals(other.keyspace)
                && typeName.equals(other.typeName)
                && Arrays.equals(byIdx, other.byIdx);
    }

    /**
     * Returns a CQL query representing this user type in human readable form.
     * <p/>
     * This method is equivalent to {@link #asCQLQuery} but the ouptut is
     * formatted to be human readable (for some definition of human readable).
     *
     * @return the CQL query representing this user type.
     */
    public String exportAsString() {
        return asCQLQuery(true);
    }

    /**
     * Returns a CQL query representing this user type.
     * <p/>
     * This method returns a single 'CREATE TYPE' query corresponding
     * to this UDT definition.
     * <p/>
     * Note that the returned string is a single line; the returned query
     * is not formatted in any way.
     *
     * @return the 'CREATE TYPE' query corresponding to this user type.
     * @see #exportAsString
     */
    public String asCQLQuery() {
        return asCQLQuery(false);
    }

    /**
     * Return the protocol version that has been used to deserialize
     * this UDT, or that will be used to serialize it.
     * In most cases this should be the version
     * currently in use by the cluster instance
     * that this UDT belongs to, as reported by
     * {@link ProtocolOptions#getProtocolVersion()}.
     *
     * @return the protocol version that has been used to deserialize
     * this UDT, or that will be used to serialize it.
     */
    ProtocolVersion getProtocolVersion() {
        return protocolVersion;
    }

    CodecRegistry getCodecRegistry() {
        return codecRegistry;
    }

    void setCodecRegistry(CodecRegistry codecRegistry) {
        this.codecRegistry = codecRegistry;
    }

    private String asCQLQuery(boolean formatted) {
        StringBuilder sb = new StringBuilder();

        sb.append("CREATE TYPE ").append(Metadata.quoteIfNecessary(keyspace)).append('.').append(Metadata.quoteIfNecessary(typeName)).append(" (");
        TableMetadata.newLine(sb, formatted);
        for (int i = 0; i < byIdx.length; i++) {
            sb.append(TableMetadata.spaces(4, formatted)).append(byIdx[i]);
            if (i < byIdx.length - 1)
                sb.append(',');
            TableMetadata.newLine(sb, formatted);
        }

        return sb.append(");").toString();
    }

    @Override
    public String toString() {
        String str = Metadata.quoteIfNecessary(getKeyspace()) + "." + Metadata.quoteIfNecessary(getTypeName());
        return isFrozen() ?
                "frozen<" + str + ">" :
                str;
    }

    @Override
    public String asFunctionParameterString() {
        return Metadata.quoteIfNecessary(getTypeName());
    }

    /**
     * A UDT field.
     */
    public static class Field {
        private final String name;
        private final DataType type;

        Field(String name, DataType type) {
            this.name = name;
            this.type = type;
        }

        /**
         * Returns the name of the field.
         *
         * @return the name of the field.
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the type of the field.
         *
         * @return the type of the field.
         */
        public DataType getType() {
            return type;
        }

        @Override
        public final int hashCode() {
            return Arrays.hashCode(new Object[]{name, type});
        }

        @Override
        public final boolean equals(Object o) {
            if (!(o instanceof Field))
                return false;

            Field other = (Field) o;
            return name.equals(other.name)
                    && type.equals(other.type);
        }

        @Override
        public String toString() {
            return Metadata.quoteIfNecessary(name) + ' ' + type;
        }
    }

    /**
     * A "shallow" definition of a UDT that only contains the keyspace and type name, without any information
     * about the type's structure.
     * <p/>
     * This is used for internal dependency analysis only, and never returned to the client.
     *
     * @since 3.0.0
     */
    static class Shallow extends DataType {

        final String keyspaceName;
        final String typeName;
        final boolean frozen;

        Shallow(String keyspaceName, String typeName, boolean frozen) {
            super(Name.UDT);
            this.keyspaceName = keyspaceName;
            this.typeName = typeName;
            this.frozen = frozen;
        }

        @Override
        public boolean isFrozen() {
            return frozen;
        }
    }
}
