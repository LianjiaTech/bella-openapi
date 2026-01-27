package com.ke.bella.openapi.protocol;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AdaptorManager {
    private static final AdaptorManager INSTANCE = new AdaptorManager();
    private final Map<String, Map<String, IProtocolAdaptor>> adaptors = new ConcurrentHashMap<>();

    private AdaptorManager() {
    }

    public static AdaptorManager getInstance() {
        return INSTANCE;
    }

    public Set<String> getProtocols(String endpoint) {
        if(!adaptors.containsKey(endpoint)) {
            return Sets.newHashSet();
        }
        return adaptors.get(endpoint).keySet();
    }

    public boolean support(String endpoint, String protocol) {
        return getProtocols(endpoint).contains(protocol);
    }

    public <T extends IProtocolAdaptor> T getProtocolAdaptor(String endpoint, String protocol, Class<T> clazz) {
        return clazz.cast(adaptors.get(endpoint).get(protocol));
    }

    public IProtocolAdaptor getProtocolAdaptor(String endpoint, String protocol) {
        return adaptors.get(endpoint).get(protocol);
    }

    public Map<String, IProtocolAdaptor> getProtocolAdaptors(String endpoint) {
        if(!adaptors.containsKey(endpoint)) {
            return Maps.newHashMap();
        }
        return adaptors.get(endpoint);
    }

    public synchronized void register(String endpoint, IProtocolAdaptor adaptor) {
        Map<String, IProtocolAdaptor> map = adaptors.computeIfAbsent(endpoint, k -> new HashMap<>());
        map.put(adaptor.getClass().getSimpleName(), adaptor);
    }
}
