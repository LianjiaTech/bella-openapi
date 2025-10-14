package com.ke.bella.openapi.protocol;

/**
 * 内存清理接口，用于在对象生命周期结束后清理大型数据以便垃圾回收
 * 主要用于在长时间HTTP请求期间释放不再需要的大对象内存
 *
 * @author Bella OpenAPI
 */
public interface IMemoryClearable {

    /**
     * 清理对象中的大型数据，释放内存
     * 调用此方法后，会清除对象中对大内存对象的引用
     */
    void clearLargeData();

    /**
     * 检查对象是否已经被清理
     * @return true 如果对象已被清理
     */
     boolean isCleared();
}
