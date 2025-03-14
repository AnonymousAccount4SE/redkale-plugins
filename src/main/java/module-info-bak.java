/**
 * <p>
 * see: https://redkale.org
 *
 * @author zhangjx
 *
 */
/**
module redkalex.plugins {

    requires java.base;
    requires java.logging;
    requires java.net.http;
    requires java.sql;

    requires redkale;

    requires kafka.clients;
    requires lettuce.core;
    requires redisson;
    requires enjoy;
    requires freemarker;

    requires org.reactivestreams;
    requires org.mongodb.bson;
    requires org.mongodb.driver.core;
    requires org.mongodb.driver.reactivestreams;

    //vertx 3.9.x
    //requires vertx.core;
    //requires vertx.sql.client;
    //requires vertx.pg.client;    
    //requires vertx.mysql.client;
    
    //vertx 4.1.x
    requires io.vertx.core;
    requires io.vertx.client.sql;
    requires io.vertx.client.sql.pg;
    requires io.vertx.client.sql.mysql;

    exports org.redkalex.apns;
    exports org.redkalex.cache.redis;
    exports org.redkalex.cluster.consul;
    exports org.redkalex.convert.protobuf;
    exports org.redkalex.htel;
    exports org.redkalex.mq.kafka;
    exports org.redkalex.net.mqtt;
    exports org.redkalex.oidc;
    exports org.redkalex.pay;
    exports org.redkalex.source.mongo;
    exports org.redkalex.source.mysql;
    exports org.redkalex.source.pgsql;
    exports org.redkalex.source.search;
    exports org.redkalex.source.vertx;
    exports org.redkalex.weixin;

}
*/
