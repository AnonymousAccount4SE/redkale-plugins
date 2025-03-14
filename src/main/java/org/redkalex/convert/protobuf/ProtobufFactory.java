/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkalex.convert.protobuf;

import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import org.redkale.convert.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class ProtobufFactory extends ConvertFactory<ProtobufReader, ProtobufWriter> {

    private static final ProtobufFactory instance = new ProtobufFactory(null, getSystemPropertyBoolean("redkale.convert.protobuf.tiny", "redkale.convert.tiny", true), Boolean.parseBoolean(System.getProperty("redkale.convert.protobuf.enumtostring", "true")));

    static final Decodeable objectDecoder = instance.loadDecoder(Object.class);

    static final Encodeable objectEncoder = instance.loadEncoder(Object.class);

    protected final boolean enumtostring;

    protected boolean reversible = false;

    static {
        instance.register(Serializable.class, objectDecoder);
        instance.register(Serializable.class, objectEncoder);

        instance.register(AnyValue.class, instance.loadDecoder(AnyValue.DefaultAnyValue.class));
        instance.register(AnyValue.class, instance.loadEncoder(AnyValue.DefaultAnyValue.class));
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    private ProtobufFactory(ProtobufFactory parent, boolean tiny, boolean enumtostring) {
        super(parent, tiny);
        this.enumtostring = enumtostring;
        if (parent == null) { //root
            this.register(String[].class, this.createArrayDecoder(String[].class));
            this.register(String[].class, this.createArrayEncoder(String[].class));
        }
    }

    public static ProtobufFactory root() {
        return instance;
    }

    public static ProtobufFactory create() {
        return new ProtobufFactory(null, getSystemPropertyBoolean("redkale.convert.protobuf.tiny", "redkale.convert.tiny", true), Boolean.parseBoolean(System.getProperty("redkale.convert.protobuf.enumtostring", "true")));
    }

    @Override
    protected SimpledCoder createEnumSimpledCoder(Class enumClass) {
        return new ProtobufEnumSimpledCoder(enumClass, this.enumtostring);
    }

    @Override
    protected ObjectDecoder createObjectDecoder(Type type) {
        return new ProtobufObjectDecoder(type);
    }

    @Override
    protected ObjectEncoder createObjectEncoder(Type type) {
        return new ProtobufObjectEncoder(type);
    }

    @Override
    protected <E> Decodeable<ProtobufReader, E> createMapDecoder(Type type) {
        return new ProtobufMapDecoder(this, type);
    }

    @Override
    protected <E> Encodeable<ProtobufWriter, E> createMapEncoder(Type type) {
        return new ProtobufMapEncoder(this, type);
    }

    @Override
    protected <E> Decodeable<ProtobufReader, E> createArrayDecoder(Type type) {
        return new ProtobufArrayDecoder(this, type);
    }

    @Override
    protected <E> Encodeable<ProtobufWriter, E> createArrayEncoder(Type type) {
        return new ProtobufArrayEncoder(this, type);
    }

    @Override
    protected <E> Decodeable<ProtobufReader, E> createCollectionDecoder(Type type) {
        return new ProtobufCollectionDecoder(this, type);
    }

    @Override
    protected <E> Encodeable<ProtobufWriter, E> createCollectionEncoder(Type type) {
        return new ProtobufCollectionEncoder(this, type);
    }

    @Override
    protected <E> Decodeable<ProtobufReader, E> createStreamDecoder(Type type) {
        return new ProtobufStreamDecoder(this, type);
    }

    @Override
    protected <E> Encodeable<ProtobufWriter, E> createStreamEncoder(Type type) {
        return new ProtobufStreamEncoder(this, type);
    }

    @Override
    public final ProtobufConvert getConvert() {
        if (convert == null) convert = new ProtobufConvert(this, tiny);
        return (ProtobufConvert) convert;
    }

    @Override
    public ProtobufFactory createChild() {
        return new ProtobufFactory(this, this.tiny, this.enumtostring);
    }

    @Override
    public ProtobufFactory createChild(boolean tiny) {
        return new ProtobufFactory(this, tiny, this.enumtostring);
    }

    @Override
    public ConvertType getConvertType() {
        return ConvertType.PROTOBUF;
    }

    public ProtobufFactory reversible(boolean reversible) {
        this.reversible = reversible;
        return this;
    }

    @Override
    public boolean isReversible() {
        return reversible;
    }

    @Override
    public boolean isFieldSort() {
        return true;
    }

    protected static Reader getItemReader(boolean string, boolean simple, Reader in, DeMember member, boolean enumtostring, boolean first) {
        if (string) {
            if (member == null || first) return in;
            ProtobufReader reader = (ProtobufReader) in;
            int tag = reader.readTag();
            if (tag != member.getTag()) {
                reader.backTag(tag);
                return null;
            }
            return in;
        } else {
            ProtobufReader reader = (ProtobufReader) in;
            if (!first && member != null) {
                int tag = reader.readTag();
                if (tag != member.getTag()) {
                    reader.backTag(tag);
                    return null;
                }
            }
            byte[] bs = reader.readByteArray();
            return new ProtobufReader(bs);
        }
    }

    public static int getTag(String fieldName, Type fieldType, int fieldPos, boolean enumtostring) {
        int wiretype = ProtobufFactory.wireType(fieldType, enumtostring);
        return (fieldPos << 3 | wiretype);
    }

    public static int getTag(DeMember member, boolean enumtostring) {
        int wiretype = ProtobufFactory.wireType(member.getAttribute().type(), enumtostring);
        return (member.getPosition() << 3 | wiretype);
    }

    public static int wireType(Type javaType, boolean enumtostring) {
        if (javaType == double.class || javaType == Double.class) return 1;
        if (javaType == float.class || javaType == Float.class) return 5;
        if (javaType == boolean.class || javaType == Boolean.class) return 0;
        if (javaType instanceof Class) {
            Class javaClazz = (Class) javaType;
            if (javaClazz.isEnum()) return enumtostring ? 2 : 0;
            if (javaClazz.isPrimitive() || Number.class.isAssignableFrom(javaClazz)) return 0;
        }
        return 2;
    }

    public static String wireTypeString(Type javaType, boolean enumtostring) {
        if (javaType == double.class || javaType == Double.class) return "double";
        if (javaType == long.class || javaType == Long.class) return "sint64";
        if (javaType == float.class || javaType == Float.class) return "float";
        if (javaType == int.class || javaType == Integer.class) return "sint32";
        if (javaType == short.class || javaType == Short.class) return "sint32";
        if (javaType == char.class || javaType == Character.class) return "sint32";
        if (javaType == byte.class || javaType == Byte.class) return "sint32";
        if (javaType == boolean.class || javaType == Boolean.class) return "bool";
        if (javaType == AtomicLong.class) return "sint64";
        if (javaType == AtomicInteger.class) return "sint32";
        if (javaType == AtomicBoolean.class) return "bool";

        if (javaType == double[].class || javaType == Double[].class) return "repeated double";
        if (javaType == long[].class || javaType == Long[].class) return "repeated sint64";
        if (javaType == float[].class || javaType == Float[].class) return "repeated float";
        if (javaType == int[].class || javaType == Integer[].class) return "repeated sint32";
        if (javaType == short[].class || javaType == Short[].class) return "repeated sint32";
        if (javaType == char[].class || javaType == Character[].class) return "repeated sint32";
        if (javaType == byte[].class || javaType == Byte[].class) return "bytes";
        if (javaType == boolean[].class || javaType == Boolean[].class) return "repeated bool";
        if (javaType == AtomicLong[].class) return "repeated sint64";
        if (javaType == AtomicInteger[].class) return "repeated sint32";
        if (javaType == AtomicBoolean[].class) return "repeated bool";

        if (javaType == java.util.Properties.class) return "map<string,string>";
        if (javaType instanceof Class) {
            Class javaClazz = (Class) javaType;
            if (javaClazz.isArray()) return "repeated " + wireTypeString(javaClazz.getComponentType(), enumtostring);
            if (javaClazz.isEnum()) return enumtostring ? "string" : javaClazz.getSimpleName();
            if (CharSequence.class.isAssignableFrom(javaClazz)) return "string";
            return javaClazz == Object.class ? "Any" : javaClazz.getSimpleName();
        } else if (javaType instanceof ParameterizedType) { //Collection、Stream、Map 必须是泛型
            final ParameterizedType pt = (ParameterizedType) javaType;
            final Class rawType = (Class) pt.getRawType();
            if (Map.class.isAssignableFrom(rawType)) {
                Type keyType = pt.getActualTypeArguments()[0];
                Type valueType = pt.getActualTypeArguments()[1];
                return "map<" + wireTypeString(keyType, enumtostring) + "," + wireTypeString(valueType, enumtostring) + ">";
            } else if (Collection.class.isAssignableFrom(rawType)
                || Stream.class.isAssignableFrom(rawType) || rawType.isArray()) {
                return "repeated " + wireTypeString(pt.getActualTypeArguments()[0], enumtostring);
            } else if (pt.getActualTypeArguments().length == 1
                && (pt.getActualTypeArguments()[0] instanceof Class)) {
                return rawType.getSimpleName() + "_" + ((Class) pt.getActualTypeArguments()[0]).getSimpleName();
            }
        } else if (javaType instanceof GenericArrayType) {
            return "repeated " + wireTypeString(((GenericArrayType) javaType).getGenericComponentType(), enumtostring);
        }
        throw new UnsupportedOperationException("ProtobufConvert not supported type(" + javaType + ")");
    }
}
