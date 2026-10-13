package com.ke.bella.openapi;

import java.util.List;

public interface ComponentList<E> extends List<E> {
    Class<E> getComponentType();
}
