/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkalex.convert.pson;

import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import org.redkale.convert.*;
import org.redkale.util.AnyValue;

/**
 *
 * @author zhangjx
 */
public class ProtobufFactory extends ConvertFactory<ProtobufReader, ProtobufWriter> {

    private static final ProtobufFactory instance = new ProtobufFactory(null, Boolean.getBoolean("convert.protobuf.tiny"));

    static final Decodeable objectDecoder = instance.loadDecoder(Object.class);

    static final Encodeable objectEncoder = instance.loadEncoder(Object.class);

    static {
        instance.register(Serializable.class, objectDecoder);
        instance.register(Serializable.class, objectEncoder);

        instance.register(AnyValue.class, instance.loadDecoder(AnyValue.DefaultAnyValue.class));
        instance.register(AnyValue.class, instance.loadEncoder(AnyValue.DefaultAnyValue.class));
    }

    private ProtobufFactory(ProtobufFactory parent, boolean tiny) {
        super(parent, tiny);
    }

    public static ProtobufFactory root() {
        return instance;
    }

    public static ProtobufFactory create() {
        return new ProtobufFactory(null, Boolean.getBoolean("convert.protobuf.tiny"));
    }

    @Override
    public final ProtobufConvert getConvert() {
        if (convert == null) convert = new ProtobufConvert(this, tiny);
        return (ProtobufConvert) convert;
    }

    @Override
    public ProtobufFactory createChild() {
        return new ProtobufFactory(this, this.tiny);
    }

    @Override
    public ProtobufFactory createChild(boolean tiny) {
        return new ProtobufFactory(this, tiny);
    }

    @Override
    public ConvertType getConvertType() {
        return ConvertType.DIY;
    }

    @Override
    public boolean isReversible() {
        return true;
    }

    @Override
    public boolean isFieldSort() {
        return false;
    }

    public static int wireType(Type javaType) {
        if (javaType == double.class || javaType == Double.class) return 1;
        if (javaType == float.class || javaType == Float.class) return 5;
        if (javaType == boolean.class || javaType == Boolean.class) return 0;
        if (javaType instanceof Class) {
            Class javaClazz = (Class) javaType;
            if (javaClazz.isEnum()) return 0;
            if (javaClazz.isPrimitive() || Number.class.isAssignableFrom(javaClazz)) return 0;
        }
        return 2;
    }

    public static String wireTypeString(Type javaType) {
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
        if (javaType == byte[].class || javaType == Byte[].class) return "repeated sint32";
        if (javaType == boolean[].class || javaType == Boolean[].class) return "repeated bool";
        if (javaType == AtomicLong[].class) return "repeated sint64";
        if (javaType == AtomicInteger[].class) return "repeated sint32";
        if (javaType == AtomicBoolean[].class) return "repeated bool";

        if (javaType == java.util.Properties.class) return "map<string,string>";
        if (javaType instanceof Class) {
            Class javaClazz = (Class) javaType;
            if (javaClazz.isArray()) return "repeated " + wireTypeString(javaClazz.getComponentType());
            if (javaClazz.isEnum()) return "enum";
            if (CharSequence.class.isAssignableFrom(javaClazz)) return "string";
            return javaClazz.getSimpleName();
        } else if (javaType instanceof ParameterizedType) { //Collection、Stream、Map 必须是泛型
            final ParameterizedType pt = (ParameterizedType) javaType;
            final Class rawType = (Class) pt.getRawType();
            if (Map.class.isAssignableFrom(rawType)) {
                Type keyType = pt.getActualTypeArguments()[0];
                Type valueType = pt.getActualTypeArguments()[1];
                return "map<" + wireTypeString(keyType) + "," + wireTypeString(valueType) + ">";
            } else if (Collection.class.isAssignableFrom(rawType)
                || Stream.class.isAssignableFrom(rawType)) {
                return "repeated " + wireTypeString(pt.getActualTypeArguments()[0]);
            }
        }
        throw new UnsupportedOperationException("ProtobufConvert not supported type(" + javaType + ")");
    }
}
