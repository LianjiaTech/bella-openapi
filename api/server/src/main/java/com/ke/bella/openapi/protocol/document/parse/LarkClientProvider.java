package com.ke.bella.openapi.protocol.document.parse;

import com.lark.oapi.Client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LarkClientProvider {
    private final static Map<String, Client> clientCache = new ConcurrentHashMap<>();

    public static Client client(String clientId, String clientSecret) {
        return clientCache.computeIfAbsent(clientId, id -> Client.newBuilder(id, clientSecret).build());
    }

}
