/*
 */
package org.redkalex.properties.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.redkale.boot.*;
import org.redkale.util.*;
import org.redkalex.properties.nacos.NacosPropertiesAgent.NacosInfo;

/**
 * 依赖于nacos-client实现的Nacos配置 https://github.com/alibaba/nacos
 *
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class NacosClientPropertiesAgent extends PropertiesAgent {

    protected ConfigService configService;

    protected ExecutorService listenExecutor;

    @Override
    public void compile(final AnyValue propertiesConf) {
    }

    @Override
    public boolean acceptsConf(AnyValue config) {
        //支持 nacos.serverAddr、nacos-serverAddr、nacos_serverAddr
        //nacos.data.group值的数据格式为: dataId1:group1:tenant1,dataId2:group2:tenant2 
        //多组数据用,分隔
        return (config.getValue("nacos.serverAddr") != null
            || config.getValue("nacos-serverAddr") != null
            || config.getValue("nacos_serverAddr") != null
            || System.getProperty("nacos.serverAddr") != null
            || System.getProperty("nacos-serverAddr") != null
            || System.getProperty("nacos_serverAddr") != null)
            && (config.getValue("nacos.data.group") != null
            || config.getValue("nacos-data-group") != null
            || config.getValue("nacos_data_group") != null
            || System.getProperty("nacos.data.group") != null
            || System.getProperty("nacos-data-group") != null
            || System.getProperty("nacos_data_group") != null);
    }

    @Override
    public Map<String, Properties> init(final Application application, final AnyValue propertiesConf) {
        try {
            Properties agentConf = new Properties();
            StringWrapper dataWrapper = new StringWrapper();
            propertiesConf.forEach((k, v) -> {
                String key = k.replace('-', '.').replace('_', '.');
                if (key.equals("nacos.data.group")) {
                    dataWrapper.setValue(v);
                } else if (key.startsWith("nacos.")) {
                    agentConf.put(key.substring("nacos.".length()), v);
                }
            });
            System.getProperties().forEach((k, v) -> {
                //支持 nacos.serverAddr、nacos-serverAddr、nacos_serverAddr
                if (k.toString().startsWith("nacos")) {
                    String key = k.toString().replace('-', '.').replace('_', '.');
                    if (key.equals("nacos.data.group")) {
                        dataWrapper.setValue(v.toString());
                    } else if (key.startsWith("nacos.")) {
                        agentConf.put(key.substring("nacos.".length()), v);
                    }
                }
            });
            List<NacosInfo> infos = NacosInfo.parse(dataWrapper.getValue());
            if (infos.isEmpty()) {
                logger.log(Level.WARNING, "nacos.data.group is empty");
                return null;
            }
            final AtomicInteger counter = new AtomicInteger();
            this.listenExecutor = Executors.newFixedThreadPool(infos.size(), r -> new Thread(r, "Redkalex-Properties-Nacos-Listen-Thread-" + counter.incrementAndGet()));

            this.configService = NacosFactory.createConfigService(agentConf);
            Map<String, Properties> result = new LinkedHashMap<>();
            for (NacosInfo info : infos) {
                final String content = configService.getConfigAndSignListener(info.dataId, info.group, 3_000, new Listener() {
                    @Override
                    public Executor getExecutor() {
                        return listenExecutor;
                    }

                    @Override
                    public void receiveConfigInfo(String configInfo) {
                        updateConent(application, info, configInfo, null);
                    }
                });
                updateConent(application, info, content, new Properties());
                result.put(info.dataId, info.properties);
            }
            return result;
        } catch (NacosException e) {
            throw new RedkaleException(e);
        }
    }

    @Override
    public void destroy(AnyValue propertiesConf) {
        if (this.configService != null) {
            try {
                this.configService.shutDown();
            } catch (NacosException e) {
                logger.log(Level.WARNING, "Shutdown nacos client error", e);
            }
        }
        if (this.listenExecutor != null) {
            this.listenExecutor.shutdownNow();
        }
    }

    private void updateConent(final Application application, NacosInfo info, String content, Properties result) {
        Properties props = new Properties();
        try {
            info.content = content;
            info.contentMD5 = Utility.md5Hex(content);
            props.load(new StringReader(content));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Load nacos content (dataId=" + info.dataId + ") error", e);
            return;
        }
        if (result == null) { //配置项动态变更时需要一次性提交所有配置项
            updateEnvironmentProperties(application, info.dataId, ResourceEvent.create(info.properties, props));
            info.properties = props;
        } else {
            info.properties = props;
            result.putAll(props);
        }
        logger.log(Level.FINER, "Nacos config(dataId=" + info.dataId + ") size: " + props.size());
    }

}
