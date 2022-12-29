/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkalex.source.mysql;

//import org.redkalex.source.mysql_old.MysqlDataSource;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import static org.redkale.boot.Application.RESNAME_APP_CLIENT_ASYNCGROUP;
import org.redkale.boot.LoggingFileHandler;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.AsyncIOGroup;
import org.redkale.source.*;
import org.redkale.util.*;
import org.redkalex.source.vertx.VertxSqlDataSource;

/**
 *
 * @author zhangjx
 */
public class MySQLTest {

    public static void main(String[] args) throws Throwable {

        LoggingFileHandler.initDebugLogConfig();
        final AsyncIOGroup asyncGroup = new AsyncIOGroup(8192, 16);
        asyncGroup.start();
        ResourceFactory factory = ResourceFactory.create();
        factory.register(RESNAME_APP_CLIENT_ASYNCGROUP, asyncGroup);

        Properties prop = new Properties();
        prop.setProperty("redkale.datasource[].url", "jdbc:mysql://127.0.0.1:3389/aa_test?useSSL=false&rewriteBatchedStatements=true&serverTimezone=UTC&characterEncoding=utf8"); //192.168.175.1  127.0.0.1 192.168.1.103
        prop.setProperty("redkale.datasource[].maxconns", "2");
        prop.setProperty("redkale.datasource[].table-autoddl", "true");
        prop.setProperty("redkale.datasource[].user", "root");
        prop.setProperty("redkale.datasource[].password", "");

        if (VertxSqlDataSource.class.isAssignableFrom(DataSqlSource.class)) return;

        //MysqlDataSource source = new MysqlDataSource();
        DataJdbcSource source = new DataJdbcSource();
        factory.inject(source);
        source.init(AnyValue.loadFromProperties(prop).getAnyValue("redkale").getAnyValue("datasource").getAnyValue(""));
        System.out.println("---------");
        Function<DataResultSet, String> func = set -> set.next() ? ("" + set.getObject(1)) : null;
        //System.out.println("查询结果: " + source.directQuery("SHOW TABLES", func));
        //System.out.println("执行结果: " + source.directExecute("SET NAMES UTF8MB4"));
        if (true) {
            DayRecord record1 = new DayRecord();
            record1.setCreateTime(System.currentTimeMillis());
            record1.setContent("这是内容1 " + Utility.formatTime(record1.getCreateTime()));
            record1.setRecordid("rid-" + record1.getCreateTime());

            DayRecord record2 = new DayRecord();
            record2.setCreateTime(record1.getCreateTime() - 24 * 60 * 60 * 1000L - 1);
            record2.setContent("这是内容2 " + Utility.formatTime(record2.getCreateTime()));
            record2.setRecordid("rid2-" + record2.getCreateTime());

            DayRecord record3 = new DayRecord();
            record3.setCreateTime(record1.getCreateTime() - 48 * 60 * 60 * 1000L - 2);
            record3.setContent("这是内容3 " + Utility.formatTime(record3.getCreateTime()));
            record3.setRecordid("rid3-" + record3.getCreateTime());

            DayRecord record4 = new DayRecord();
            record4.setCreateTime(record1.getCreateTime() - 72 * 60 * 60 * 1000L - 3);
            record4.setContent("这是内容4 " + Utility.formatTime(record4.getCreateTime()));
            record4.setRecordid("rid4-" + record4.getCreateTime());

            source.insert(record1, record2);
            System.out.println("-------新增成功---------");

            record1.setContent("这是内容1 xx " + Utility.formatTime(record1.getCreateTime()));
            record2.setContent("这是内容2 xx " + Utility.formatTime(record2.getCreateTime()));
            record3.setContent("这是内容3 xx " + Utility.formatTime(record3.getCreateTime()));
            record4.setContent("这是内容4 xx " + Utility.formatTime(record4.getCreateTime()));
            for (int i = 0; i < 10; i++) {
                //    record2.setContent(record2.getContent() + " ******** ");
            }

            //int rs = source.updateAsync(record1, record2, record3, record4).join();
            int rs = source.update(record1, record2, record3, record4);
            System.out.println("-------修改成功数: " + rs + "---------");

            FilterNode node = FilterNode.create("createTime", new Range.LongRange(record4.getCreateTime(), record1.getCreateTime()));
            System.out.println("查询结果: " + source.querySheet(DayRecord.class, new Flipper(), node));
            return;
        }

        System.out.println("清空表: " + source.clearTable(World.class));
        //System.out.println("----------新增记录----------");

        //EntityInfo info = ((MysqlDataSource)source).loadEntityInfo(SmsRecord.class);
        //System.out.println(Arrays.toString(info.getQueryAttributes()));
        System.out.println("查询List结果: " + source.queryList(World.class));
        System.out.println("查询List结束========");

        World w1 = new World();
        w1.id = 1;
        w1.randomNumber = 10;
        World w2 = new World();
        w2.id = 2;
        w2.randomNumber = 20;
        System.out.println("新增结果: " + source.insert(w1, w2));

        System.out.println("\r\n\r\n开始更新\r\n\r\n");
        w1.randomNumber = 11;
        w2.randomNumber = 22;
        System.out.println("修改结果: " + source.update(w1, w2));

        System.out.println("\r\n\r\n开始删除\r\n\r\n");
        System.out.println("删除结果: " + source.delete(w1));

        System.out.println("\r\n\r\n开始finds\r\n\r\n");
        List<Integer> list = List.of(1, -1, 2);
        System.out.println(Arrays.toString(source.finds(World.class, list.stream())));

        System.out.println("\r\n\r\n再次finds\r\n\r\n");
        List<Integer> list2 = List.of(3);
        System.out.println(Arrays.toString(source.finds(World.class, list2.stream())));

        int[] cs = source.directExecute("update world set randomNumber =11 where id =2", "update world set randomNumber =11 where id =-1");
        System.out.println("批量处理结果: " + Arrays.toString(cs));

        //if (true) return;
        final SmsRecord record = new SmsRecord((short) 2, "12345678901", "这是内容");
        record.setSmsid("sms1-" + record.getCreateTime());
        SmsRecord record2 = new SmsRecord((short) 2, "12345678901", "这是内容");
        record2.setSmsid("sms2-" + record.getCreateTime());
        System.out.println("新增结果: " + source.insert(record, record2));
        SmsRecord record3 = new SmsRecord((short) 2, "12345678901", "这是内容");
        record3.setSmsid("sms3-" + record.getCreateTime());
        System.out.println("新增结果: " + source.insert(record3));
        SmsRecord record4 = new SmsRecord((short) 2, "12345678901", "这是内容");
        record4.setSmsid("sms4-" + record.getCreateTime());
        System.out.println("新增结果: " + source.insert(record4));
        if (source.find(SmsRecord.class, record.getSmsid()) == null) source.insert(record);

        //if (true) return;
        System.out.println(source.find(SmsRecord.class, "sms1-1632282662741"));
        System.out.println("--------------继续查询单个记录------------------");
        SmsRecord sms = source.find(SmsRecord.class, FilterNode.create("smsid", record.getSmsid()));
        System.out.println(sms);
        sms.setCreateTime(System.currentTimeMillis());
        sms.setStatus((short) 3);
        System.out.println("----------------修改记录----------------");
        System.out.println("修改结果: " + source.update(sms));
        System.out.println("修改结果: " + source.update(sms));
        sms.setSmsid("33");
        System.out.println("修改结果: " + source.update(sms));
        System.out.println(source.find(SmsRecord.class, "sms1-1632282662741"));

        System.out.println("\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n");
        System.out.println(" -------------------------------- 压测开始 --------------------------------");
        final String json = JsonConvert.root().convertTo(record);
        int count = 200;
        CountDownLatch cdl = new CountDownLatch(count);
        long s = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            final int b = i;
            new Thread() {
                public void run() {
                    try {
                        if (b % 3 == 0) {
                            String smsid = record.getSmsid().replace("sms1", "sms" + ((b + 1) % 5 + 100));
                            source.delete(SmsRecord.class, smsid);
                        } else if (b % 2 == 0) {
                            source.findAsync(SmsRecord.class, record.getSmsid()).thenCompose(v -> source.updateAsync(v)).join();
                        } else {
                            String smsid = record.getSmsid().replace("sms1", "sms" + (b + 1) % 5);
                            String content = "这是内容," + ((b + 1) % 5);
                            SmsRecord s = JsonConvert.root().convertFrom(SmsRecord.class, json);
                            s.setSmsid(smsid);
                            s.setContent(content);
                            source.update(s);
                        }
                    } finally {
                        cdl.countDown();
                    }
                }
            }.start();
        }
        cdl.await();
        long e = System.currentTimeMillis() - s;
        System.out.println("并发 " + count + ", 一共耗时: " + e + "ms");
        System.out.println("---------------- 准备关闭DataSource ----------------");
        source.close();
        System.out.println("---------------- 全部执行完毕 ----------------");
    }
}
