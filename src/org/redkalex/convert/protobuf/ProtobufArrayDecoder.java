/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkalex.convert.protobuf;

import java.lang.reflect.Type;
import org.redkale.convert.*;

/**
 *
 * @author zhangjx
 * @param <T> T
 */
public class ProtobufArrayDecoder<T> extends ArrayDecoder<T> {

    private final boolean string;

    private final boolean enumtostring;

    public ProtobufArrayDecoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.enumtostring = ((ProtobufFactory) factory).enumtostring;
        this.string = String.class == this.getComponentType();
    }

    @Override
    protected Reader getItemReader(Reader in, DeMember member, boolean first) {
        return ProtobufFactory.getItemReader(string, in, member, enumtostring, first);
    }

}
