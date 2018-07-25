/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkalex.test.protobuf;

import com.google.protobuf.ByteString;
import java.util.*;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.Utility;
import org.redkalex.convert.pson.ProtobufConvert;

/**
 *
 * @author zhangjx
 */
public class TestBean {

    public static class PTestEntry {

        @ConvertColumn(index = 1)
        public boolean[] bools = new boolean[]{true, false, true};

        @ConvertColumn(index = 2)
        public byte[] bytes = new byte[]{1, 2, 3, 4};

        @ConvertColumn(index = 3)
        public char[] chars = new char[]{'A', 'B', 'C'};

        @ConvertColumn(index = 4)
        public short[] shorts = new short[]{10, 20, 30};

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }

    public static enum Kind {
        ONE,
        TWO,
        THREE
    }

    @ConvertColumn(index = 1)
    public boolean[] bools ;

    @ConvertColumn(index = 2)
    public byte[] bytes;

    @ConvertColumn(index = 3)
    public char[] chars ;

    @ConvertColumn(index = 4)
    public PTestEntry[] entrys;

    @ConvertColumn(index = 5)
    public int[] ints ;

    @ConvertColumn(index = 6)
    public float[] floats;

    @ConvertColumn(index = 7)
    public long[] longs ;

    @ConvertColumn(index = 8)
    public double[] doubles; //8

    @ConvertColumn(index = 9)
    public String[] strings ; //9

    @ConvertColumn(index = 10)
    public int id = 0x7788; //10

    @ConvertColumn(index = 11)
    public String name; //11

    @ConvertColumn(index = 12)
    public String email; //12

    @ConvertColumn(index = 13)
    public Kind kind;  //13

    @ConvertColumn(index = 14)
    public Map<String, Integer> map; //14

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

    public static void main(String[] args) throws Throwable {
        System.out.println(ProtobufConvert.root().getProtoDescriptor(TestBean.class));
        System.out.println(Integer.toHexString('a')); 
        System.out.println(Integer.toHexString(14<<3|2));
        TestBean bean = new TestBean();
//        bean.bools = new boolean[]{true, false, true};
//        bean.bytes = new byte[]{1, 2, 3, 4};
//        bean.chars = new char[]{'A', 'B', 'C'};
//        bean.entrys  = new PTestEntry[]{new PTestEntry(), new PTestEntry()};
//        bean.ints = new int[]{100, 200, 300};
//        bean.floats  = new float[]{10.12f, 20.34f};
//        bean.longs = new long[]{111, 222, 333};
//        bean.doubles = new double[]{65.65, 78.78};
//        bean.strings = new String[]{"str1", "str2", "str3"};
//        bean.name = "redkale";
//        bean.email = "redkale@qq.org";
//        bean.kind  = Kind.TWO;
        bean.map = Utility.ofMap("aa", 0x55, "bb", 0x66);
        System.out.println(bean);
        byte[] bs = ProtobufConvert.root().convertTo(bean);
        Utility.println("convert-", bs);
        PTestBeanOuterClass.PTestBean.Builder builder = PTestBeanOuterClass.PTestBean.newBuilder();

        for (int i = 0; bean.bools != null && i < bean.bools.length; i++) {
            builder.addBools(bean.bools[i]);
        }
        if (bean.bytes != null) builder.addBytes(ByteString.copyFrom(bean.bytes));
        for (int i = 0; bean.chars != null && i < bean.chars.length; i++) {
            builder.addChars(bean.chars[i]);
        }
        for (int i = 0; bean.entrys != null && i < bean.entrys.length; i++) {
            PTestBeanOuterClass.PTestBean.PTestEntry.Builder entry = PTestBeanOuterClass.PTestBean.PTestEntry.newBuilder();
            for (int j = 0; bean.entrys[i].bools != null && j < bean.entrys[i].bools.length; j++) {
                entry.addBools(bean.entrys[i].bools[j]);
            }
            if (bean.entrys[i].bytes != null) entry.addBytes(ByteString.copyFrom(bean.entrys[i].bytes));
            for (int j = 0; bean.entrys[i].chars != null && j < bean.entrys[i].chars.length; j++) {
                entry.addChars(bean.entrys[i].chars[j]);
            }
            for (int j = 0; bean.entrys[i].shorts != null && j < bean.entrys[i].shorts.length; j++) {
                entry.addShorts(bean.entrys[i].shorts[j]);
            }
            builder.addEntrys(entry.build());
        }
        for (int i = 0; bean.ints != null && i < bean.ints.length; i++) {
            builder.addInts(bean.ints[i]);
        }
        for (int i = 0; bean.floats != null && i < bean.floats.length; i++) {
            builder.addFloats(bean.floats[i]);
        }
        for (int i = 0; bean.longs != null && i < bean.longs.length; i++) {
            builder.addLongs(bean.longs[i]);
        }
        for (int i = 0; bean.doubles != null && i < bean.doubles.length; i++) {
            builder.addDoubles(bean.doubles[i]);
        }
        for (int i = 0; bean.strings != null && i < bean.strings.length; i++) {
            builder.addStrings(bean.strings[i]);
        }
        builder.setId(bean.id);
        if (bean.name != null) builder.setName(bean.name);
        if (bean.email != null) builder.setEmail(bean.email);
        if (bean.kind != null) builder.setKind(PTestBeanOuterClass.PTestBean.Kind.TWO);
        if (bean.map != null) builder.putAllMap(bean.map);
        PTestBeanOuterClass.PTestBean bean2 = builder.build();
        byte[] bs2 = bean2.toByteArray();
        Utility.println("protobuf", bs2);
        Thread.sleep(10); 
        if (!Arrays.equals(bs, bs2)) throw new RuntimeException("两者序列化出来的byte[]不一致");
        //集合判断hasNext有问题，还未解决
        System.out.println(ProtobufConvert.root().convertFrom(TestBean.class, bs).toString());
    }
}

//protoc --java_out=D:\Java-Projects\RedkalePluginsProject\test\ --proto_path=D:\Java-Projects\RedkalePluginsProject\test\org\redkalex\test\protobuf\ PTestBean.proto
