/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkalex.cluster;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.*;
import org.redkale.boot.*;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class ConsulClusterAgent extends ClusterAgent {

    protected static final Map<String, String> httpHeaders = Utility.ofMap("Content-Type", "application/json");

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected String apiurl;

    protected ScheduledThreadPoolExecutor scheduler;

    @Override
    public void init(AnyValue config) {
        super.init(config);
        AnyValue[] properties = config.getAnyValues("property");
        for (AnyValue property : properties) {
            if ("apiurl".equalsIgnoreCase(property.getValue("name"))) {
                this.apiurl = property.getValue("value", "").trim();
                if (this.apiurl.endsWith("/")) this.apiurl = this.apiurl.substring(0, this.apiurl.length() - 1);
            }
        }
    }

    @Override
    public void destroy(AnyValue config) {
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Override
    protected void afterRegister(NodeServer ns, String protocol) {
        if (this.scheduler == null) {
            this.scheduler = new ScheduledThreadPoolExecutor(3, (Runnable r) -> {
                final Thread t = new Thread(r, ConsulClusterAgent.class.getSimpleName() + "-Task-Thread");
                t.setDaemon(true);
                return t;
            });
        }
    }

    @Override
    protected List<InetSocketAddress> queryAddress(NodeServer ns, String protocol, Service service) {
        String serviceid = generateServiceId(ns, protocol, service);
        String servicename = generateServiceName(ns, protocol, service);
        InetSocketAddress address = ns.getSncpAddress();

        return new ArrayList<>();
    }

    @Override
    protected void register(NodeServer ns, String protocol, Service service) {
        String serviceid = generateServiceId(ns, protocol, service);
        String servicename = generateServiceName(ns, protocol, service);
        InetSocketAddress address = ns.getSncpAddress();
        String json = "{\"ID\": \"" + serviceid + "\",\"Name\": \"" + servicename + "\",\"Address\": \"" + address.getHostString() + "\",\"Port\": " + address.getPort()
            + ",\"Check\":{\"CheckID\": \"" + generateCheckId(ns, protocol, service) + "\",\"Name\": \"" + generateCheckName(ns, protocol, service) + "\",\"TTL\":\"10s\",\"Notes\":\"Interval Check\"}}";
        try {
            String s = Utility.remoteHttpContent("PUT", this.apiurl + "/agent/service/register", httpHeaders, json).toString(StandardCharsets.UTF_8);
            System.out.println(s);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, serviceid + " register error", ex);
        }
    }

    @Override
    protected void deregister(NodeServer ns, String protocol, Service service) {
        String serviceid = generateServiceId(ns, protocol, service);
        try {
            Utility.remoteHttpContent("PUT", this.apiurl + "/agent/service/deregister/" + serviceid, httpHeaders, (String) null).toString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, serviceid + " deregister error", ex);
        }
    }

}
