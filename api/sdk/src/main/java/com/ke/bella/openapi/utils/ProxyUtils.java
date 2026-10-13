package com.ke.bella.openapi.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 代理工具类，用于配置和管理全局代理设置
 */
public class ProxyUtils {
    private static final Logger logger = LoggerFactory.getLogger(ProxyUtils.class);

    // 代理配置相关常量
    private static String proxyHost = null;
    private static int proxyPort = 0;
    private static Proxy.Type proxyType = Proxy.Type.DIRECT;
    private static final Set<String> proxyDomains = new HashSet<>();

    // 系统属性名称
    private static final String PROP_PROXY_HOST = "bella.proxy.host";
    private static final String PROP_PROXY_PORT = "bella.proxy.port";
    private static final String PROP_PROXY_TYPE = "bella.proxy.type"; // 值为
                                                                      // "socks"
                                                                      // 或
                                                                      // "http"
    private static final String PROP_PROXY_DOMAINS = "bella.proxy.domains"; // 多个域名用逗号分隔

    // 静态初始化块，从环境变量和系统属性读取代理配置
    static {
        logger.info("ProxyUtils 静态初始化块开始执行...");
        try {
            initProxyFromSystemProperties();

            if(proxyHost != null && proxyPort > 0) {
                logger.info("代理初始化成功: {}:{} ({}), 代理域名: {}",
                        proxyHost, proxyPort, proxyType,
                        proxyDomains.isEmpty() ? "所有域名" : proxyDomains);
            }
        } catch (Exception e) {
            logger.warn("初始化代理配置失败: {}", e.getMessage());
        }
        logger.info("ProxyUtils 静态初始化块执行完成");
    }

    /**
     * 从系统属性初始化代理配置
     */
    private static void initProxyFromSystemProperties() {
        String host = System.getProperty(PROP_PROXY_HOST);
        String portStr = System.getProperty(PROP_PROXY_PORT);
        String typeStr = System.getProperty(PROP_PROXY_TYPE);
        String domainsStr = System.getProperty(PROP_PROXY_DOMAINS);

        if(host != null && !host.isEmpty() && portStr != null && !portStr.isEmpty()) {
            try {
                int port = Integer.parseInt(portStr);
                Proxy.Type type = Proxy.Type.DIRECT;

                if("socks".equalsIgnoreCase(typeStr)) {
                    type = Proxy.Type.SOCKS;
                } else if("http".equalsIgnoreCase(typeStr)) {
                    type = Proxy.Type.HTTP;
                }

                String[] domains = null;
                if(domainsStr != null && !domainsStr.isEmpty()) {
                    domains = domainsStr.split(",");
                }

                setProxyConfig(host, port, type, domains);
                logger.info("从系统属性配置代理: {}:{} ({})", host, port, type);
            } catch (NumberFormatException e) {
                logger.warn("代理端口配置错误: {}", portStr);
            }
        }
    }

    /**
     * 设置代理配置
     */
    private static void setProxyConfig(String host, int port, Proxy.Type type, String[] domains) {
        proxyHost = host;
        proxyPort = port;
        proxyType = (type == Proxy.Type.HTTP || type == Proxy.Type.SOCKS) ? type : Proxy.Type.DIRECT;

        // 清除并重新设置需要代理的域名
        proxyDomains.clear();
        if(domains != null && domains.length > 0) {
            for (String domain : domains) {
                if(domain != null && !domain.trim().isEmpty()) {
                    proxyDomains.add(domain.trim());
                }
            }
        }
    }

    /**
     * 检查指定URL是否需要使用代理
     * 
     * @param url 请求URL
     * 
     * @return 如果需要代理返回true，否则返回false
     */
    public static boolean shouldUseProxy(String url) {
        // 如果没有配置代理或URL为空，则不使用代理
        if(proxyHost == null || proxyPort <= 0 || url == null || url.isEmpty()) {
            return false;
        }

        // 如果没有指定特定域名，则代理所有请求
        if(proxyDomains.isEmpty()) {
            return true;
        }

        // 检查URL是否包含需要代理的域名
        for (String domain : proxyDomains) {
            if(url.contains(domain)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取代理选择器
     * 
     * @return ProxySelector 实例
     */
    public static ProxySelector getProxySelector() {
        if(proxyHost == null || proxyPort <= 0 || proxyType == Proxy.Type.DIRECT) {
            return ProxySelector.getDefault();
        }

        final Proxy proxy = new Proxy(proxyType, new InetSocketAddress(proxyHost, proxyPort));

        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                String url = uri.toString();
                if(shouldUseProxy(url)) {
                    return Collections.singletonList(proxy);
                } else {
                    return Collections.singletonList(Proxy.NO_PROXY);
                }
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                logger.warn("代理连接失败: {} 到 {}, 错误: {}", uri, sa, ioe.getMessage());
            }
        };
    }
}
