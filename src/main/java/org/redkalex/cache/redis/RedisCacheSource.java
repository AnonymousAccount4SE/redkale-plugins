/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkalex.cache.redis;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.annotation.Resource;
import org.redkale.convert.Convert;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.*;
import org.redkale.service.*;
import org.redkale.source.*;
import org.redkale.util.*;
import static org.redkale.boot.Application.RESNAME_APP_ASYNCGROUP;

/**
 * 详情见: https://redkale.org
 *
 *
 * @author zhangjx
 */
@Local
@AutoLoad(false)
@ResourceType(CacheSource.class)
public final class RedisCacheSource extends AbstractService implements CacheSource, Service, AutoCloseable, Resourcable {

    static final boolean debug = false; //System.getProperty("os.name").contains("Window") || System.getProperty("os.name").contains("Mac");

    protected static final byte TYPE_BULK = '$';  //块字符串

    protected static final byte TYPE_ARRAY = '*'; //数组

    protected static final byte TYPE_STRING = '+';  //简单字符串(不包含CRLF)类型

    protected static final byte TYPE_ERROR = '-'; //错误(不包含CRLF)类型

    protected static final byte TYPE_NUMBER = ':'; //整型

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    @Resource(name = RESNAME_APP_ASYNCGROUP)
    protected AsyncGroup asyncGroup;

    @Resource
    protected JsonConvert defaultConvert;

    @Resource(name = "$_convert")
    protected JsonConvert convert;

    protected RedisCacheClient client;

    protected InetSocketAddress address;

    protected int db;

    @Override
    public void init(AnyValue conf) {
        if (this.convert == null) this.convert = this.defaultConvert;
        if (conf == null) conf = new AnyValue.DefaultAnyValue();
        String password = null;
        for (AnyValue node : conf.getAnyValues("node")) {
            String addrstr = node.getValue("addr");
            if (addrstr.startsWith("redis://")) {
                addrstr = addrstr.substring("redis://".length());
                int pos = addrstr.indexOf(':');
                address = new InetSocketAddress(addrstr.substring(0, pos), Integer.parseInt(addrstr.substring(pos + 1)));
            } else { //兼容addr和port分开
                address = new InetSocketAddress(addrstr, node.getIntValue("port"));
            }
            password = node.getValue("password", "").trim();
            String db0 = node.getValue("db", "").trim();
            if (!db0.isEmpty()) db = Integer.valueOf(db0);
            break;
        }
        this.client = new RedisCacheClient(asyncGroup, resourceName() + "." + db, address, Utility.cpus(), 16,
            password == null || password.isEmpty() ? null : new RedisCacheReqAuth(password), db > 0 ? new RedisCacheReqDB(db) : null);

        if (logger.isLoggable(Level.FINE)) logger.log(Level.FINE, RedisCacheSource.class.getSimpleName() + ": addr=" + address + ", db=" + db);
    }

    @Override //ServiceLoader时判断配置是否符合当前实现类
    public boolean acceptsConf(AnyValue config) {
        if (config == null) return false;
        AnyValue[] nodes = config.getAnyValues("node");
        if (nodes == null || nodes.length == 0) return false;
        for (AnyValue node : nodes) {
            if (node.getValue("addr") != null && node.getValue("addr").startsWith("redis://")) return true;
        }
        return false;
    }

    @Override
    public final String getType() {
        return "redis";
    }

    @Override
    public void close() throws Exception {  //在 Application 关闭时调用
        destroy(null);
    }

    @Override
    public String resourceName() {
        Resource res = this.getClass().getAnnotation(Resource.class);
        return res == null ? "" : res.name();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{addr = " + this.address + ", db=" + this.db + "}";
    }

    @Override
    public void destroy(AnyValue conf) {
        if (client != null) client.close();
    }

    //--------------------- exists ------------------------------
    @Override
    public CompletableFuture<Boolean> existsAsync(String key) {
        return sendAsync("EXISTS", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getIntValue(0) > 0);
    }

    @Override
    public boolean exists(String key) {
        return existsAsync(key).join();
    }

    //--------------------- get ------------------------------
    @Override
    public <T> CompletableFuture<T> getAsync(String key, Type type) {
        return sendAsync("GET", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getObjectValue(type));
    }

    @Override
    public CompletableFuture<String> getStringAsync(String key) {
        return sendAsync("GET", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getStringValue());
    }

    @Override
    public CompletableFuture<Long> getLongAsync(String key, long defValue) {
        return sendAsync("GET", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getLongValue(defValue));
    }

    @Override
    public <T> T get(String key, final Type type) {
        return (T) getAsync(key, type).join();
    }

    @Override
    public String getString(String key) {
        return getStringAsync(key).join();
    }

    @Override
    public long getLong(String key, long defValue) {
        return getLongAsync(key, defValue).join();
    }

    //--------------------- getAndRefresh ------------------------------
    @Override
    public <T> CompletableFuture<T> getAndRefreshAsync(String key, int expireSeconds, final Type type) {
        return refreshAsync(key, expireSeconds).thenCompose(v -> getAsync(key, type));
    }

    @Override
    public <T> T getAndRefresh(String key, final int expireSeconds, final Type type) {
        return (T) getAndRefreshAsync(key, expireSeconds, type).join();
    }

    @Override
    public CompletableFuture<String> getStringAndRefreshAsync(String key, int expireSeconds) {
        return refreshAsync(key, expireSeconds).thenCompose(v -> getStringAsync(key));
    }

    @Override
    public String getStringAndRefresh(String key, final int expireSeconds) {
        return getStringAndRefreshAsync(key, expireSeconds).join();
    }

    @Override
    public CompletableFuture<Long> getLongAndRefreshAsync(String key, int expireSeconds, long defValue) {
        return (CompletableFuture) refreshAsync(key, expireSeconds).thenCompose(v -> getLongAsync(key, defValue));
    }

    @Override
    public long getLongAndRefresh(String key, final int expireSeconds, long defValue) {
        return getLongAndRefreshAsync(key, expireSeconds, defValue).join();
    }

    //--------------------- refresh ------------------------------
    @Override
    public CompletableFuture<Void> refreshAsync(String key, int expireSeconds) {
        return setExpireSecondsAsync(key, expireSeconds);
    }

    @Override
    public void refresh(String key, final int expireSeconds) {
        setExpireSeconds(key, expireSeconds);
    }

    //--------------------- set ------------------------------
    @Override
    public <T> CompletableFuture<Void> setAsync(String key, Convert convert, T value) {
        return sendAsync("SET", key, key.getBytes(StandardCharsets.UTF_8), formatValue(convert, value.getClass(), value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(String key, final Type type, T value) {
        return sendAsync("SET", key, key.getBytes(StandardCharsets.UTF_8), formatValue((Convert) null, type, value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(String key, Convert convert, final Type type, T value) {
        return sendAsync("SET", key, key.getBytes(StandardCharsets.UTF_8), formatValue(convert, type, value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public <T> void set(final String key, final Convert convert, T value) {
        setAsync(key, convert, value).join();
    }

    @Override
    public <T> void set(final String key, final Type type, T value) {
        setAsync(key, type, value).join();
    }

    @Override
    public <T> void set(String key, final Convert convert, final Type type, T value) {
        setAsync(key, convert, type, value).join();
    }

    @Override
    public CompletableFuture<Void> setStringAsync(String key, String value) {
        return sendAsync("SET", key, key.getBytes(StandardCharsets.UTF_8), formatValue(value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public void setString(String key, String value) {
        setStringAsync(key, value).join();
    }

    @Override
    public CompletableFuture<Void> setLongAsync(String key, long value) {
        return sendAsync("SET", key, key.getBytes(StandardCharsets.UTF_8), formatValue(value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public void setLong(String key, long value) {
        setLongAsync(key, value).join();
    }

    //--------------------- set ------------------------------    
    @Override
    public <T> CompletableFuture<Void> setAsync(int expireSeconds, String key, Convert convert, T value) {
        return (CompletableFuture) setAsync(key, convert, value).thenCompose(v -> setExpireSecondsAsync(key, expireSeconds));
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(int expireSeconds, String key, final Type type, T value) {
        return (CompletableFuture) setAsync(key, type, value).thenCompose(v -> setExpireSecondsAsync(key, expireSeconds));
    }

    @Override
    public <T> CompletableFuture<Void> setAsync(int expireSeconds, String key, Convert convert, final Type type, T value) {
        return (CompletableFuture) setAsync(key, convert, type, value).thenCompose(v -> setExpireSecondsAsync(key, expireSeconds));
    }

    @Override
    public <T> void set(int expireSeconds, String key, Convert convert, T value) {
        setAsync(expireSeconds, key, convert, value).join();
    }

    @Override
    public <T> void set(int expireSeconds, String key, final Type type, T value) {
        setAsync(expireSeconds, key, type, value).join();
    }

    @Override
    public <T> void set(int expireSeconds, String key, Convert convert, final Type type, T value) {
        setAsync(expireSeconds, key, convert, type, value).join();
    }

    @Override
    public CompletableFuture<Void> setStringAsync(int expireSeconds, String key, String value) {
        return (CompletableFuture) setStringAsync(key, value).thenCompose(v -> setExpireSecondsAsync(key, expireSeconds));
    }

    @Override
    public void setString(int expireSeconds, String key, String value) {
        setStringAsync(expireSeconds, key, value).join();
    }

    @Override
    public CompletableFuture<Void> setLongAsync(int expireSeconds, String key, long value) {
        return (CompletableFuture) setLongAsync(key, value).thenCompose(v -> setExpireSecondsAsync(key, expireSeconds));
    }

    @Override
    public void setLong(int expireSeconds, String key, long value) {
        setLongAsync(expireSeconds, key, value).join();
    }

    //--------------------- setExpireSeconds ------------------------------    
    @Override
    public CompletableFuture<Void> setExpireSecondsAsync(String key, int expireSeconds) {
        return sendAsync("EXPIRE", key, key.getBytes(StandardCharsets.UTF_8), String.valueOf(expireSeconds).getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public void setExpireSeconds(String key, int expireSeconds) {
        setExpireSecondsAsync(key, expireSeconds).join();
    }

    //--------------------- remove ------------------------------    
    @Override
    public CompletableFuture<Integer> removeAsync(String key) {
        return sendAsync("DEL", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getIntValue(0));
    }

    @Override
    public int remove(String key) {
        return removeAsync(key).join();
    }

    //--------------------- incr ------------------------------    
    @Override
    public long incr(final String key) {
        return incrAsync(key).join();
    }

    @Override
    public CompletableFuture<Long> incrAsync(final String key) {
        return sendAsync("INCR", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getLongValue(0L));
    }

    @Override
    public long incr(final String key, long num) {
        return incrAsync(key, num).join();
    }

    @Override
    public CompletableFuture<Long> incrAsync(final String key, long num) {
        return sendAsync("INCRBY", key, key.getBytes(StandardCharsets.UTF_8), String.valueOf(num).getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getLongValue(0L));
    }

    //--------------------- decr ------------------------------    
    @Override
    public long decr(final String key) {
        return decrAsync(key).join();
    }

    @Override
    public CompletableFuture<Long> decrAsync(final String key) {
        return sendAsync("DECR", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getLongValue(0L));
    }

    @Override
    public long decr(final String key, long num) {
        return decrAsync(key, num).join();
    }

    @Override
    public CompletableFuture<Long> decrAsync(final String key, long num) {
        return sendAsync("DECRBY", key, key.getBytes(StandardCharsets.UTF_8), String.valueOf(num).getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getLongValue(0L));
    }

    @Override
    public int hremove(final String key, String... fields) {
        return hremoveAsync(key, fields).join();
    }

    @Override
    public int hsize(final String key) {
        return hsizeAsync(key).join();
    }

    @Override
    public List<String> hkeys(final String key) {
        return hkeysAsync(key).join();
    }

    @Override
    public long hincr(final String key, String field) {
        return hincrAsync(key, field).join();
    }

    @Override
    public long hincr(final String key, String field, long num) {
        return hincrAsync(key, field, num).join();
    }

    @Override
    public long hdecr(final String key, String field) {
        return hdecrAsync(key, field).join();
    }

    @Override
    public long hdecr(final String key, String field, long num) {
        return hdecrAsync(key, field, num).join();
    }

    @Override
    public boolean hexists(final String key, String field) {
        return hexistsAsync(key, field).join();
    }

    @Override
    public <T> void hset(final String key, final String field, final Convert convert, final T value) {
        hsetAsync(key, field, convert, value).join();
    }

    @Override
    public <T> void hset(final String key, final String field, final Type type, final T value) {
        hsetAsync(key, field, type, value).join();
    }

    @Override
    public <T> void hset(final String key, final String field, final Convert convert, final Type type, final T value) {
        hsetAsync(key, field, convert, type, value).join();
    }

    @Override
    public void hsetString(final String key, final String field, final String value) {
        hsetStringAsync(key, field, value).join();
    }

    @Override
    public void hsetLong(final String key, final String field, final long value) {
        hsetLongAsync(key, field, value).join();
    }

    @Override
    public void hmset(final String key, final Serializable... values) {
        hmsetAsync(key, values).join();
    }

    @Override
    public List<Serializable> hmget(final String key, final Type type, final String... fields) {
        return hmgetAsync(key, type, fields).join();
    }

    @Override
    public <T> Map<String, T> hmap(final String key, final Type type, int offset, int limit, String pattern) {
        return (Map) hmapAsync(key, type, offset, limit, pattern).join();
    }

    @Override
    public <T> Map<String, T> hmap(final String key, final Type type, int offset, int limit) {
        return (Map) hmapAsync(key, type, offset, limit).join();
    }

    @Override
    public <T> T hget(final String key, final String field, final Type type) {
        return (T) hgetAsync(key, field, type).join();
    }

    @Override
    public String hgetString(final String key, final String field) {
        return hgetStringAsync(key, field).join();
    }

    @Override
    public long hgetLong(final String key, final String field, long defValue) {
        return hgetLongAsync(key, field, defValue).join();
    }

    @Override
    public CompletableFuture<Integer> hremoveAsync(final String key, String... fields) {
        byte[][] bs = new byte[fields.length + 1][];
        bs[0] = key.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < fields.length; i++) {
            bs[i + 1] = fields[i].getBytes(StandardCharsets.UTF_8);
        }
        return sendAsync("HDEL", key, bs).thenApply(v -> v.getIntValue(0));
    }

    @Override
    public CompletableFuture<Integer> hsizeAsync(final String key) {
        return sendAsync("HLEN", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getIntValue(0));
    }

    @Override
    public CompletableFuture<List<String>> hkeysAsync(final String key) {
        return sendAsync("HKEYS", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> (List) v.getCollectionValue(false, String.class));
    }

    @Override
    public CompletableFuture<Long> hincrAsync(final String key, String field) {
        return hincrAsync(key, field, 1);
    }

    @Override
    public CompletableFuture<Long> hincrAsync(final String key, String field, long num) {
        return sendAsync("HINCRBY", key, key.getBytes(StandardCharsets.UTF_8), field.getBytes(StandardCharsets.UTF_8), String.valueOf(num).getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getLongValue(0L));
    }

    @Override
    public CompletableFuture<Long> hdecrAsync(final String key, String field) {
        return hincrAsync(key, field, -1);
    }

    @Override
    public CompletableFuture<Long> hdecrAsync(final String key, String field, long num) {
        return hincrAsync(key, field, -num);
    }

    @Override
    public CompletableFuture<Boolean> hexistsAsync(final String key, String field) {
        return sendAsync("HEXISTS", key, key.getBytes(StandardCharsets.UTF_8), field.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getIntValue(0) > 0);
    }

    @Override
    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Convert convert, final T value) {
        if (value == null) return CompletableFuture.completedFuture(null);
        return sendAsync("HSET", key, key.getBytes(StandardCharsets.UTF_8), field.getBytes(StandardCharsets.UTF_8), formatValue(convert, null, value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Type type, final T value) {
        if (value == null) return CompletableFuture.completedFuture(null);
        return sendAsync("HSET", key, key.getBytes(StandardCharsets.UTF_8), field.getBytes(StandardCharsets.UTF_8), formatValue(null, type, value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public <T> CompletableFuture<Void> hsetAsync(final String key, final String field, final Convert convert, final Type type, final T value) {
        if (value == null) return CompletableFuture.completedFuture(null);
        return sendAsync("HSET", key, key.getBytes(StandardCharsets.UTF_8), field.getBytes(StandardCharsets.UTF_8), formatValue(convert, type, value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public CompletableFuture<Void> hsetStringAsync(final String key, final String field, final String value) {
        if (value == null) return CompletableFuture.completedFuture(null);
        return sendAsync("HSET", key, key.getBytes(StandardCharsets.UTF_8), field.getBytes(StandardCharsets.UTF_8), formatValue(value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public CompletableFuture<Void> hsetLongAsync(final String key, final String field, final long value) {
        return sendAsync("HSET", key, key.getBytes(StandardCharsets.UTF_8), field.getBytes(StandardCharsets.UTF_8), formatValue(value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public CompletableFuture<Void> hmsetAsync(final String key, final Serializable... values) {
        byte[][] bs = new byte[values.length + 1][];
        bs[0] = key.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < values.length; i += 2) {
            bs[i + 1] = String.valueOf(values[i]).getBytes(StandardCharsets.UTF_8);
            bs[i + 2] = formatValue(values[i + 1]);
        }
        return sendAsync("HMSET", key, bs).thenApply(v -> v.getVoidValue());
    }

    @Override
    public CompletableFuture<List<Serializable>> hmgetAsync(final String key, final Type type, final String... fields) {
        byte[][] bs = new byte[fields.length + 1][];
        bs[0] = key.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < fields.length; i++) {
            bs[i + 1] = fields[i].getBytes(StandardCharsets.UTF_8);
        }
        return sendAsync("HMGET", key, bs).thenApply(v -> (List) v.getCollectionValue(false, type));
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> hmapAsync(final String key, final Type type, int offset, int limit) {
        return hmapAsync(key, type, offset, limit, null);
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> hmapAsync(final String key, final Type type, int offset, int limit, String pattern) {
        byte[][] bs = new byte[pattern == null || pattern.isEmpty() ? 4 : 6][limit];
        int index = -1;
        bs[++index] = key.getBytes(StandardCharsets.UTF_8);
        bs[++index] = String.valueOf(offset).getBytes(StandardCharsets.UTF_8);
        if (pattern != null && !pattern.isEmpty()) {
            bs[++index] = "MATCH".getBytes(StandardCharsets.UTF_8);
            bs[++index] = pattern.getBytes(StandardCharsets.UTF_8);
        }
        bs[++index] = "COUNT".getBytes(StandardCharsets.UTF_8);
        bs[++index] = String.valueOf(limit).getBytes(StandardCharsets.UTF_8);
        return sendAsync("HSCAN", key, bs).thenApply(v -> v.getMapValue(type));
    }

    @Override
    public <T> CompletableFuture<T> hgetAsync(final String key, final String field, final Type type) {
        return sendAsync("HGET", key, key.getBytes(StandardCharsets.UTF_8), field.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getObjectValue(type));
    }

    @Override
    public CompletableFuture<String> hgetStringAsync(final String key, final String field) {
        return sendAsync("HGET", key, key.getBytes(StandardCharsets.UTF_8), field.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getStringValue());
    }

    @Override
    public CompletableFuture<Long> hgetLongAsync(final String key, final String field, long defValue) {
        return sendAsync("HGET", key, key.getBytes(StandardCharsets.UTF_8), field.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getLongValue(defValue));
    }

    //--------------------- collection ------------------------------  
    @Override
    public CompletableFuture<Integer> getCollectionSizeAsync(String key) {
        return sendAsync("TYPE", key, key.getBytes(StandardCharsets.UTF_8)).thenCompose(t -> {
            String type = t.getStringValue();
            if (type == null) return CompletableFuture.completedFuture(0);
            return sendAsync(type.contains("list") ? "LLEN" : "SCARD", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getIntValue(0));
        });
    }

    @Override
    public int getCollectionSize(String key) {
        return getCollectionSizeAsync(key).join();
    }

    @Override
    public <T> CompletableFuture<Collection<T>> getCollectionAsync(String key, final Type componentType) {
        return sendAsync("TYPE", key, key.getBytes(StandardCharsets.UTF_8)).thenCompose(t -> {
            String type = t.getStringValue();
            if (type == null) return CompletableFuture.completedFuture(null);
            boolean set = !type.contains("list");
            return sendAsync(set ? "SMEMBERS" : "LRANGE", key, keyArgs(set, key)).thenApply(v -> v.getCollectionValue(set, componentType));
        });
    }

    @Override
    public CompletableFuture<Map<String, Long>> getLongMapAsync(String... keys) {
        byte[][] bs = new byte[keys.length][];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = keys[i].getBytes(StandardCharsets.UTF_8);
        }
        return sendAsync("MGET", keys[0], bs).thenApply(v -> {
            List list = (List) v.getCollectionValue(false, long.class);
            Map map = new LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) {
                Object obj = list.get(i);
                if (obj != null) map.put(keys[i], list.get(i));
            }
            return map;
        });
    }

    @Override
    public CompletableFuture<Long[]> getLongArrayAsync(String... keys) {
        byte[][] bs = new byte[keys.length][];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = keys[i].getBytes(StandardCharsets.UTF_8);
        }
        return sendAsync("MGET", keys[0], bs).thenApply(v -> {
            List list = (List) v.getCollectionValue(false, long.class);
            Long[] rs = new Long[keys.length];
            for (int i = 0; i < keys.length; i++) {
                Number obj = (Number) list.get(i);
                rs[i] = obj == null ? null : obj.longValue();
            }
            return rs;
        });
    }

    @Override
    public CompletableFuture<String[]> getStringArrayAsync(String... keys) {
        byte[][] bs = new byte[keys.length][];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = keys[i].getBytes(StandardCharsets.UTF_8);
        }
        return sendAsync("MGET", keys[0], bs).thenApply(v -> {
            List list = (List) v.getCollectionValue(false, String.class);
            String[] rs = new String[keys.length];
            for (int i = 0; i < keys.length; i++) {
                Object obj = list.get(i);
                rs[i] = obj == null ? null : obj.toString();
            }
            return rs;
        });
    }

    @Override
    public CompletableFuture<Map<String, String>> getStringMapAsync(String... keys) {
        byte[][] bs = new byte[keys.length][];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = keys[i].getBytes(StandardCharsets.UTF_8);
        }
        return sendAsync("MGET", keys[0], bs).thenApply(v -> {
            List list = (List) v.getCollectionValue(false, String.class);
            Map map = new LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) {
                Object obj = list.get(i);
                if (obj != null) map.put(keys[i], list.get(i));
            }
            return map;
        });
    }

    @Override
    public <T> CompletableFuture<Map<String, T>> getMapAsync(final Type componentType, String... keys) {
        byte[][] bs = new byte[keys.length][];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = keys[i].getBytes(StandardCharsets.UTF_8);
        }
        return sendAsync("MGET", keys[0], bs).thenApply(v -> {
            List list = (List) v.getCollectionValue(false, componentType);
            Map map = new LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) {
                Object obj = list.get(i);
                if (obj != null) map.put(keys[i], list.get(i));
            }
            return map;
        });
    }

    @Override
    public <T> CompletableFuture<Map<String, Collection<T>>> getCollectionMapAsync(final boolean set, final Type componentType, final String... keys) {
        final CompletableFuture<Map<String, Collection<T>>> rsFuture = new CompletableFuture<>();
        final Map<String, Collection<T>> map = new LinkedHashMap<>();
        final CompletableFuture[] futures = new CompletableFuture[keys.length];
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            futures[i] = sendAsync(set ? "SMEMBERS" : "LRANGE", key, keyArgs(set, key)).thenAccept(v -> {
                Collection c = v.getCollectionValue(set, componentType);
                if (c != null) {
                    synchronized (map) {
                        map.put(key, (Collection) c);
                    }
                }
            });
        }
        CompletableFuture.allOf(futures).whenComplete((w, e) -> {
            if (e != null) {
                rsFuture.completeExceptionally(e);
            } else {
                rsFuture.complete(map);
            }
        });
        return rsFuture;
    }

    @Override
    public <T> Collection<T> getCollection(String key, final Type componentType) {
        return (Collection) getCollectionAsync(key, componentType).join();
    }

    @Override
    public Map<String, Long> getLongMap(final String... keys) {
        return getLongMapAsync(keys).join();
    }

    @Override
    public Long[] getLongArray(final String... keys) {
        return getLongArrayAsync(keys).join();
    }

    @Override
    public Map<String, String> getStringMap(final String... keys) {
        return getStringMapAsync(keys).join();
    }

    @Override
    public String[] getStringArray(final String... keys) {
        return getStringArrayAsync(keys).join();
    }

    @Override
    public <T> Map<String, T> getMap(final Type componentType, final String... keys) {
        return (Map) getMapAsync(componentType, keys).join();
    }

    @Override
    public <T> Map<String, Collection<T>> getCollectionMap(final boolean set, final Type componentType, String... keys) {
        return (Map) getCollectionMapAsync(set, componentType, keys).join();
    }

    @Override
    public CompletableFuture<Collection<String>> getStringCollectionAsync(String key) {
        return sendAsync("TYPE", key, key.getBytes(StandardCharsets.UTF_8)).thenCompose(t -> {
            String type = t.getStringValue();
            if (type == null) return CompletableFuture.completedFuture(null);
            boolean set = !type.contains("list");
            return sendAsync(set ? "SMEMBERS" : "LRANGE", key, keyArgs(set, key)).thenApply(v -> v.getCollectionValue(set, String.class));
        });
    }

    @Override
    public CompletableFuture<Map<String, Collection<String>>> getStringCollectionMapAsync(final boolean set, String... keys) {
        final CompletableFuture<Map<String, Collection<String>>> rsFuture = new CompletableFuture<>();
        final Map<String, Collection<String>> map = new LinkedHashMap<>();
        final CompletableFuture[] futures = new CompletableFuture[keys.length];
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            futures[i] = sendAsync(set ? "SMEMBERS" : "LRANGE", key, keyArgs(set, key)).thenAccept(v -> {
                Collection<String> c = v.getCollectionValue(set, String.class);
                if (c != null) {
                    synchronized (map) {
                        map.put(key, (Collection) c);
                    }
                }
            });
        }
        CompletableFuture.allOf(futures).whenComplete((w, e) -> {
            if (e != null) {
                rsFuture.completeExceptionally(e);
            } else {
                rsFuture.complete(map);
            }
        });
        return rsFuture;
    }

    @Override
    public Collection<String> getStringCollection(String key) {
        return getStringCollectionAsync(key).join();
    }

    @Override
    public Map<String, Collection<String>> getStringCollectionMap(final boolean set, String... keys) {
        return getStringCollectionMapAsync(set, keys).join();
    }

    @Override
    public CompletableFuture<Collection<Long>> getLongCollectionAsync(String key) {
        return sendAsync("TYPE", key, key.getBytes(StandardCharsets.UTF_8)).thenCompose(t -> {
            String type = t.getStringValue();
            if (type == null) return CompletableFuture.completedFuture(null);
            boolean set = !type.contains("list");
            return sendAsync(set ? "SMEMBERS" : "LRANGE", key, keyArgs(set, key)).thenApply(v -> v.getCollectionValue(set, long.class));
        });
    }

    @Override
    public CompletableFuture<Map<String, Collection<Long>>> getLongCollectionMapAsync(final boolean set, String... keys) {
        final CompletableFuture<Map<String, Collection<Long>>> rsFuture = new CompletableFuture<>();
        final Map<String, Collection<Long>> map = new LinkedHashMap<>();
        final CompletableFuture[] futures = new CompletableFuture[keys.length];
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            futures[i] = sendAsync(set ? "SMEMBERS" : "LRANGE", key, keyArgs(set, key)).thenAccept(v -> {
                Collection<String> c = v.getCollectionValue(set, long.class);
                if (c != null) {
                    synchronized (map) {
                        map.put(key, (Collection) c);
                    }
                }
            });
        }
        CompletableFuture.allOf(futures).whenComplete((w, e) -> {
            if (e != null) {
                rsFuture.completeExceptionally(e);
            } else {
                rsFuture.complete(map);
            }
        });
        return rsFuture;
    }

    @Override
    public Collection<Long> getLongCollection(String key) {
        return getLongCollectionAsync(key).join();
    }

    @Override
    public Map<String, Collection<Long>> getLongCollectionMap(final boolean set, String... keys) {
        return getLongCollectionMapAsync(set, keys).join();
    }

    //--------------------- getCollectionAndRefresh ------------------------------  
    @Override
    public <T> CompletableFuture<Collection<T>> getCollectionAndRefreshAsync(String key, int expireSeconds, final Type componentType) {
        return (CompletableFuture) refreshAsync(key, expireSeconds).thenCompose(v -> getCollectionAsync(key, componentType));
    }

    @Override
    public <T> Collection<T> getCollectionAndRefresh(String key, final int expireSeconds, final Type componentType) {
        return (Collection) getCollectionAndRefreshAsync(key, expireSeconds, componentType).join();
    }

    @Override
    public CompletableFuture<Collection<String>> getStringCollectionAndRefreshAsync(String key, int expireSeconds) {
        return (CompletableFuture) refreshAsync(key, expireSeconds).thenCompose(v -> getStringCollectionAsync(key));
    }

    @Override
    public Collection<String> getStringCollectionAndRefresh(String key, final int expireSeconds) {
        return getStringCollectionAndRefreshAsync(key, expireSeconds).join();
    }

    @Override
    public CompletableFuture<Collection<Long>> getLongCollectionAndRefreshAsync(String key, int expireSeconds) {
        return (CompletableFuture) refreshAsync(key, expireSeconds).thenCompose(v -> getLongCollectionAsync(key));
    }

    @Override
    public Collection<Long> getLongCollectionAndRefresh(String key, final int expireSeconds) {
        return getLongCollectionAndRefreshAsync(key, expireSeconds).join();
    }

    //--------------------- existsItem ------------------------------  
    @Override
    public <T> boolean existsSetItem(String key, final Type componentType, T value) {
        return existsSetItemAsync(key, componentType, value).join();
    }

    @Override
    public <T> CompletableFuture<Boolean> existsSetItemAsync(String key, final Type componentType, T value) {
        return sendAsync("SISMEMBER", key, key.getBytes(StandardCharsets.UTF_8), formatValue((Convert) null, componentType, value)).thenApply(v -> v.getIntValue(0) > 0);
    }

    @Override
    public boolean existsStringSetItem(String key, String value) {
        return existsStringSetItemAsync(key, value).join();
    }

    @Override
    public CompletableFuture<Boolean> existsStringSetItemAsync(String key, String value) {
        return sendAsync("SISMEMBER", key, key.getBytes(StandardCharsets.UTF_8), formatValue(value)).thenApply(v -> v.getIntValue(0) > 0);
    }

    @Override
    public boolean existsLongSetItem(String key, long value) {
        return existsLongSetItemAsync(key, value).join();
    }

    @Override
    public CompletableFuture<Boolean> existsLongSetItemAsync(String key, long value) {
        return sendAsync("SISMEMBER", key, key.getBytes(StandardCharsets.UTF_8), formatValue(value)).thenApply(v -> v.getIntValue(0) > 0);
    }

    //--------------------- appendListItem ------------------------------  
    @Override
    public <T> CompletableFuture<Void> appendListItemAsync(String key, final Type componentType, T value) {
        return sendAsync("RPUSH", key, key.getBytes(StandardCharsets.UTF_8), formatValue((Convert) null, componentType, value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public <T> void appendListItem(String key, final Type componentType, T value) {
        appendListItemAsync(key, componentType, value).join();
    }

    @Override
    public CompletableFuture<Void> appendStringListItemAsync(String key, String value) {
        return sendAsync("RPUSH", key, key.getBytes(StandardCharsets.UTF_8), formatValue(value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public void appendStringListItem(String key, String value) {
        appendStringListItemAsync(key, value).join();
    }

    @Override
    public CompletableFuture<Void> appendLongListItemAsync(String key, long value) {
        return sendAsync("RPUSH", key, key.getBytes(StandardCharsets.UTF_8), formatValue(value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public void appendLongListItem(String key, long value) {
        appendLongListItemAsync(key, value).join();
    }

    //--------------------- removeListItem ------------------------------  
    @Override
    public <T> CompletableFuture<Integer> removeListItemAsync(String key, final Type componentType, T value) {
        return sendAsync("LREM", key, key.getBytes(StandardCharsets.UTF_8), new byte[]{'0'}, formatValue((Convert) null, componentType, value)).thenApply(v -> v.getIntValue(0));
    }

    @Override
    public <T> int removeListItem(String key, final Type componentType, T value) {
        return removeListItemAsync(key, componentType, value).join();
    }

    @Override
    public CompletableFuture<Integer> removeStringListItemAsync(String key, String value) {
        return sendAsync("LREM", key, key.getBytes(StandardCharsets.UTF_8), new byte[]{'0'}, formatValue(value)).thenApply(v -> v.getIntValue(0));
    }

    @Override
    public int removeStringListItem(String key, String value) {
        return removeStringListItemAsync(key, value).join();
    }

    @Override
    public CompletableFuture<Integer> removeLongListItemAsync(String key, long value) {
        return sendAsync("LREM", key, key.getBytes(StandardCharsets.UTF_8), new byte[]{'0'}, formatValue(value)).thenApply(v -> v.getIntValue(0));
    }

    @Override
    public int removeLongListItem(String key, long value) {
        return removeLongListItemAsync(key, value).join();
    }

    //--------------------- appendSetItem ------------------------------  
    @Override
    public <T> CompletableFuture<Void> appendSetItemAsync(String key, Type componentType, T value) {
        return sendAsync("SADD", key, key.getBytes(StandardCharsets.UTF_8), formatValue((Convert) null, componentType, value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public <T> CompletableFuture<T> spopSetItemAsync(String key, Type componentType) {
        return sendAsync("SPOP", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getObjectValue(componentType));
    }

    @Override
    public <T> CompletableFuture<Set<T>> spopSetItemAsync(String key, int count, Type componentType) {
        return sendAsync("SPOP", key, key.getBytes(StandardCharsets.UTF_8), String.valueOf(count).getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getObjectValue(componentType));
    }

    @Override
    public CompletableFuture<String> spopStringSetItemAsync(String key) {
        return sendAsync("SPOP", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getStringValue());
    }

    @Override
    public CompletableFuture<Set<String>> spopStringSetItemAsync(String key, int count) {
        return sendAsync("SPOP", key, key.getBytes(StandardCharsets.UTF_8), String.valueOf(count).getBytes(StandardCharsets.UTF_8)).thenApply(v -> (Set) v.getCollectionValue(true, String.class));
    }

    @Override
    public CompletableFuture<Long> spopLongSetItemAsync(String key) {
        return sendAsync("SPOP", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getLongValue(0L));
    }

    @Override
    public CompletableFuture<Set<Long>> spopLongSetItemAsync(String key, int count) {
        return sendAsync("SPOP", key, key.getBytes(StandardCharsets.UTF_8), String.valueOf(count).getBytes(StandardCharsets.UTF_8)).thenApply(v -> (Set) v.getCollectionValue(true, long.class));
    }

    @Override
    public <T> void appendSetItem(String key, final Type componentType, T value) {
        appendSetItemAsync(key, componentType, value).join();
    }

    @Override
    public <T> T spopSetItem(String key, final Type componentType) {
        return (T) spopSetItemAsync(key, componentType).join();
    }

    @Override
    public <T> Set<T> spopSetItem(String key, int count, final Type componentType) {
        return (Set) spopSetItemAsync(key, count, componentType).join();
    }

    @Override
    public String spopStringSetItem(String key) {
        return spopStringSetItemAsync(key).join();
    }

    @Override
    public Set<String> spopStringSetItem(String key, int count) {
        return spopStringSetItemAsync(key, count).join();
    }

    @Override
    public Long spopLongSetItem(String key) {
        return spopLongSetItemAsync(key).join();
    }

    @Override
    public Set<Long> spopLongSetItem(String key, int count) {
        return spopLongSetItemAsync(key, count).join();
    }

    @Override
    public CompletableFuture<Void> appendStringSetItemAsync(String key, String value) {
        return sendAsync("SADD", key, key.getBytes(StandardCharsets.UTF_8), formatValue(value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public void appendStringSetItem(String key, String value) {
        appendStringSetItemAsync(key, value).join();
    }

    @Override
    public CompletableFuture<Void> appendLongSetItemAsync(String key, long value) {
        return sendAsync("SADD", key, key.getBytes(StandardCharsets.UTF_8), formatValue(value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public void appendLongSetItem(String key, long value) {
        appendLongSetItemAsync(key, value).join();
    }

    //--------------------- removeSetItem ------------------------------  
    @Override
    public <T> CompletableFuture<Integer> removeSetItemAsync(String key, final Type componentType, T value) {
        return sendAsync("SREM", key, key.getBytes(StandardCharsets.UTF_8), formatValue((Convert) null, componentType, value)).thenApply(v -> v.getIntValue(0));
    }

    @Override
    public <T> int removeSetItem(String key, final Type componentType, T value) {
        return removeSetItemAsync(key, componentType, value).join();
    }

    @Override
    public CompletableFuture<Integer> removeStringSetItemAsync(String key, String value) {
        return sendAsync("SREM", key, key.getBytes(StandardCharsets.UTF_8), formatValue(value)).thenApply(v -> v.getIntValue(0));
    }

    @Override
    public int removeStringSetItem(String key, String value) {
        return removeStringSetItemAsync(key, value).join();
    }

    @Override
    public CompletableFuture<Integer> removeLongSetItemAsync(String key, long value) {
        return sendAsync("SREM", key, key.getBytes(StandardCharsets.UTF_8), formatValue(value)).thenApply(v -> v.getIntValue(0));
    }

    @Override
    public int removeLongSetItem(String key, long value) {
        return removeLongSetItemAsync(key, value).join();
    }

    //--------------------- queryKeys ------------------------------  
    @Override
    public List<String> queryKeys() {
        return queryKeysAsync().join();
    }

    @Override
    public List<String> queryKeysStartsWith(String startsWith) {
        return queryKeysStartsWithAsync(startsWith).join();
    }

    @Override
    public List<String> queryKeysEndsWith(String endsWith) {
        return queryKeysEndsWithAsync(endsWith).join();
    }

    @Override
    public byte[] getBytes(final String key) {
        return getBytesAsync(key).join();
    }

    @Override
    public byte[] getBytesAndRefresh(final String key, final int expireSeconds) {
        return getBytesAndRefreshAsync(key, expireSeconds).join();
    }

    @Override
    public void setBytes(final String key, final byte[] value) {
        setBytesAsync(key, value).join();
    }

    @Override
    public void setBytes(final int expireSeconds, final String key, final byte[] value) {
        setBytesAsync(expireSeconds, key, value).join();
    }

    @Override
    public <T> void setBytes(final String key, final Convert convert, final Type type, final T value) {
        setBytesAsync(key, convert, type, value).join();
    }

    @Override
    public <T> void setBytes(final int expireSeconds, final String key, final Convert convert, final Type type, final T value) {
        setBytesAsync(expireSeconds, key, convert, type, value).join();
    }

    @Override
    public CompletableFuture<byte[]> getBytesAsync(final String key) {
        return sendAsync("GET", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> v.getBytesValue());
    }

    @Override
    public CompletableFuture<byte[]> getBytesAndRefreshAsync(final String key, final int expireSeconds) {
        return refreshAsync(key, expireSeconds).thenCompose(v -> getBytesAsync(key));
    }

    @Override
    public CompletableFuture<Void> setBytesAsync(final String key, final byte[] value) {
        return sendAsync("SET", key, key.getBytes(StandardCharsets.UTF_8), value).thenApply(v -> v.getVoidValue());

    }

    @Override
    public CompletableFuture<Void> setBytesAsync(final int expireSeconds, final String key, final byte[] value) {
        return (CompletableFuture) setBytesAsync(key, value).thenCompose(v -> setExpireSecondsAsync(key, expireSeconds));
    }

    @Override
    public <T> CompletableFuture<Void> setBytesAsync(final String key, final Convert convert, final Type type, final T value) {
        return sendAsync("SET", key, key.getBytes(StandardCharsets.UTF_8), convert.convertToBytes(type, value)).thenApply(v -> v.getVoidValue());
    }

    @Override
    public <T> CompletableFuture<Void> setBytesAsync(final int expireSeconds, final String key, final Convert convert, final Type type, final T value) {
        return (CompletableFuture) setBytesAsync(key, convert.convertToBytes(type, value)).thenCompose(v -> setExpireSecondsAsync(key, expireSeconds));
    }

    @Override
    public CompletableFuture<List<String>> queryKeysAsync() {
        return sendAsync("KEYS", "*", new byte[]{(byte) '*'}).thenApply(v -> (List) v.getCollectionValue(false, String.class));
    }

    @Override
    public CompletableFuture<List<String>> queryKeysStartsWithAsync(String startsWith) {
        if (startsWith == null) return queryKeysAsync();
        String key = startsWith + "*";
        return sendAsync("KEYS", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> (List) v.getCollectionValue(false, String.class));
    }

    @Override
    public CompletableFuture<List<String>> queryKeysEndsWithAsync(String endsWith) {
        if (endsWith == null) return queryKeysAsync();
        String key = "*" + endsWith;
        return sendAsync("KEYS", key, key.getBytes(StandardCharsets.UTF_8)).thenApply(v -> (List) v.getCollectionValue(false, String.class));
    }

    //--------------------- getKeySize ------------------------------  
    @Override
    public int getKeySize() {
        return getKeySizeAsync().join();
    }

    @Override
    public CompletableFuture<Integer> getKeySizeAsync() {
        return sendAsync("DBSIZE", null).thenApply(v -> v.getIntValue(0));
    }

    //--------------------- send ------------------------------  
    private CompletableFuture<RedisCacheResult> sendAsync(final String command, final String key, final byte[]... args) {
        return client.pollConnection().thenCompose(conn -> conn.writeChannel(conn.pollRequest(WorkThread.currWorkThread()).prepare(command, key, args))).orTimeout(6, TimeUnit.SECONDS);
    }

    private byte[][] keyArgs(boolean set, String key) {
        if (set) return new byte[][]{key.getBytes(StandardCharsets.UTF_8)};
        return new byte[][]{key.getBytes(StandardCharsets.UTF_8), new byte[]{'0'}, new byte[]{'-', '1'}};
    }

    private byte[] formatValue(long value) {
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] formatValue(String value) {
        if (value == null) throw new NullPointerException();
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] formatValue(Object value) {
        return formatValue(null, null, value);
    }

    private byte[] formatValue(Convert convert0, Type type, Object value) {
        if (value == null) throw new NullPointerException();
        if (value instanceof byte[]) return (byte[]) value;
        if (convert0 == null) convert0 = convert;
        if (type == null) type = value.getClass();
        Class clz = value.getClass();
        if (clz == String.class || clz == Long.class
            || Number.class.isAssignableFrom(clz) || CharSequence.class.isAssignableFrom(clz)) {
            return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        }
        return convert0.convertToBytes(type, value);
    }
}
