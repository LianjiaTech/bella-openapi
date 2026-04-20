package com.ke.bella.openapi.apikey;

import com.ke.bella.openapi.common.EntityConstants;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class AkPermissionMatrix {

    private AkPermissionMatrix() {}

    static final Set<AkOperation> ALL_OPS =
            Collections.unmodifiableSet(EnumSet.allOf(AkOperation.class));
    static final Set<AkOperation> NO_OPS =
            Collections.unmodifiableSet(EnumSet.noneOf(AkOperation.class));
    static final Set<AkOperation> MANAGER_ALL_OPS_EXCEPT_GOVERN =
            Collections.unmodifiableSet(EnumSet.complementOf(EnumSet.of(
                    AkOperation.CHANGE_OWNER,
                    AkOperation.CHANGE_PARENT,
                    AkOperation.VIEW_CHANGE_HISTORY
            )));

    private static final Map<String, Map<AkRelation, Set<AkOperation>>> MATRIX;

    static {
        Map<String, Map<AkRelation, Set<AkOperation>>> m = new HashMap<>();

        // all：全部放行
        Map<AkRelation, Set<AkOperation>> allMap = new EnumMap<>(AkRelation.class);
        for (AkRelation r : AkRelation.values()) allMap.put(r, ALL_OPS);
        m.put(EntityConstants.ALL, Collections.unmodifiableMap(allMap));

        // console
        Set<AkOperation> consoleSameOrg = Collections.unmodifiableSet(EnumSet.of(
                AkOperation.QUERY, AkOperation.RESET, AkOperation.RENAME, AkOperation.CHANGE_STATUS));
        Set<AkOperation> consoleUnrelated = Collections.unmodifiableSet(EnumSet.of(
                AkOperation.QUERY,
                AkOperation.CHANGE_OWNER,
                AkOperation.CHANGE_PARENT,
                AkOperation.VIEW_CHANGE_HISTORY
        ));
        Map<AkRelation, Set<AkOperation>> consoleMap = new EnumMap<>(AkRelation.class);
        consoleMap.put(AkRelation.OWNER,     ALL_OPS);
        consoleMap.put(AkRelation.MANAGER,   ALL_OPS);
        consoleMap.put(AkRelation.SAME_ORG,  consoleSameOrg);
        consoleMap.put(AkRelation.UNRELATED, consoleUnrelated);
        m.put(EntityConstants.CONSOLE, Collections.unmodifiableMap(consoleMap));

        // high
        Set<AkOperation> highOwner = Collections.unmodifiableSet(EnumSet.of(
                AkOperation.QUERY, AkOperation.RESET, AkOperation.RENAME,
                AkOperation.CHANGE_STATUS, AkOperation.CREATE_CHILD, AkOperation.TRANSFER,
                AkOperation.VIEW_TRANSFER_HISTORY, AkOperation.UPDATE_MANAGER));
        Map<AkRelation, Set<AkOperation>> highMap = new EnumMap<>(AkRelation.class);
        highMap.put(AkRelation.OWNER,     highOwner);
        highMap.put(AkRelation.MANAGER,   MANAGER_ALL_OPS_EXCEPT_GOVERN);
        highMap.put(AkRelation.SAME_ORG,  NO_OPS);
        highMap.put(AkRelation.UNRELATED, NO_OPS);
        m.put(EntityConstants.HIGH, Collections.unmodifiableMap(highMap));

        // low
        Set<AkOperation> lowOwner = Collections.unmodifiableSet(EnumSet.of(
                AkOperation.QUERY, AkOperation.RESET, AkOperation.RENAME,
                AkOperation.CHANGE_STATUS, AkOperation.CREATE_CHILD, AkOperation.TRANSFER,
                AkOperation.VIEW_TRANSFER_HISTORY, AkOperation.UPDATE_MANAGER));
        Map<AkRelation, Set<AkOperation>> lowMap = new EnumMap<>(AkRelation.class);
        lowMap.put(AkRelation.OWNER,     lowOwner);
        lowMap.put(AkRelation.MANAGER,   MANAGER_ALL_OPS_EXCEPT_GOVERN);
        lowMap.put(AkRelation.SAME_ORG,  NO_OPS);
        lowMap.put(AkRelation.UNRELATED, NO_OPS);
        m.put(EntityConstants.LOW, Collections.unmodifiableMap(lowMap));

        MATRIX = Collections.unmodifiableMap(m);
    }

    public static boolean isAllowed(String roleCode, AkRelation relation, AkOperation operation) {
        Map<AkRelation, Set<AkOperation>> inner = MATRIX.get(roleCode);
        if (inner == null) return false;
        Set<AkOperation> ops = inner.get(relation);
        return ops != null && ops.contains(operation);
    }

    public static Set<AkOperation> getAllowedOps(String roleCode, AkRelation relation) {
        Map<AkRelation, Set<AkOperation>> inner = MATRIX.get(roleCode);
        if (inner == null) return NO_OPS;
        Set<AkOperation> ops = inner.get(relation);
        return ops != null ? ops : NO_OPS;
    }
}
