/**
 * 安全的 sessionStorage 操作工具函数
 * 提供统一的异常处理和降级策略
 */

/**
 * 检查 sessionStorage 是否可用
 */
function isSessionStorageAvailable(): boolean {
  try {
    if (typeof window === "undefined" || !window.sessionStorage) {
      return false
    }
    // 测试是否真的可用（某些浏览器虽然有 sessionStorage 但被禁用）
    const testKey = "__storage_test__"
    window.sessionStorage.setItem(testKey, "test")
    window.sessionStorage.removeItem(testKey)
    return true
  } catch {
    return false
  }
}

/**
 * 安全地从 sessionStorage 读取字符串值
 * @param key - 存储键名
 * @param defaultValue - 当读取失败时返回的默认值
 * @returns 读取到的值或默认值
 */
export function safeGetItem(key: string, defaultValue: string | null = null): string | null {
  try {
    if (!isSessionStorageAvailable()) {
      return defaultValue
    }
    return window.sessionStorage.getItem(key) ?? defaultValue
  } catch (error) {
    console.warn(`[Storage] Failed to get item "${key}":`, error)
    return defaultValue
  }
}

/**
 * 安全地从 sessionStorage 读取并解析 JSON 值
 * @param key - 存储键名
 * @param defaultValue - 当读取或解析失败时返回的默认值
 * @returns 解析后的值或默认值
 */
export function safeGetJSON<T>(key: string, defaultValue: T): T {
  try {
    if (!isSessionStorageAvailable()) {
      return defaultValue
    }
    const item = window.sessionStorage.getItem(key)
    if (item === null) {
      return defaultValue
    }
    return JSON.parse(item) as T
  } catch (error) {
    console.warn(`[Storage] Failed to get/parse JSON for "${key}":`, error)
    return defaultValue
  }
}

/**
 * 安全地向 sessionStorage 写入字符串值
 * @param key - 存储键名
 * @param value - 要存储的值
 * @returns 是否写入成功
 */
export function safeSetItem(key: string, value: string): boolean {
  try {
    if (!isSessionStorageAvailable()) {
      return false
    }
    window.sessionStorage.setItem(key, value)
    return true
  } catch (error) {
    console.warn(`[Storage] Failed to set item "${key}":`, error)
    return false
  }
}

/**
 * 安全地向 sessionStorage 写入 JSON 值
 * @param key - 存储键名
 * @param value - 要存储的值（将被序列化为 JSON）
 * @returns 是否写入成功
 */
export function safeSetJSON(key: string, value: any): boolean {
  try {
    if (!isSessionStorageAvailable()) {
      return false
    }
    const serialized = JSON.stringify(value)
    window.sessionStorage.setItem(key, serialized)
    return true
  } catch (error) {
    console.warn(`[Storage] Failed to set/stringify JSON for "${key}":`, error)
    return false
  }
}

/**
 * 安全地从 sessionStorage 删除值
 * @param key - 存储键名
 * @returns 是否删除成功
 */
export function safeRemoveItem(key: string): boolean {
  try {
    if (!isSessionStorageAvailable()) {
      return false
    }
    window.sessionStorage.removeItem(key)
    return true
  } catch (error) {
    console.warn(`[Storage] Failed to remove item "${key}":`, error)
    return false
  }
}

/**
 * 安全地清空整个 sessionStorage
 * @returns 是否清空成功
 */
export function safeClear(): boolean {
  try {
    if (!isSessionStorageAvailable()) {
      return false
    }
    window.sessionStorage.clear()
    return true
  } catch (error) {
    console.warn("[Storage] Failed to clear sessionStorage:", error)
    return false
  }
}

