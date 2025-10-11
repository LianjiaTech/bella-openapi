package com.ke.bella.openapi;

import org.springframework.beans.BeanUtils;

public interface ISummary {

    default ISummary summary() {
        try {
            ISummary summary = this.getClass().newInstance();
            BeanUtils.copyProperties(this, summary, ignoreFields());
            return summary;
        } catch (InstantiationException | IllegalAccessException e) {
            return this;
        }
    }

    String[] ignoreFields();
}
