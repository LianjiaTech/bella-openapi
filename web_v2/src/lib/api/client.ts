import axios, { AxiosInstance, AxiosError, AxiosRequestConfig, AxiosResponse } from 'axios';

/**
 * 动态解析 baseURL
 * - 支持 SSR/CSR 环境
 * - 自动适配 HTTP/HTTPS 协议
 * - 开发环境使用相对路径
 */
const getBaseURL = (): string => {
  // 开发环境使用相对路径
  if (process.env.NODE_ENV === 'development') {
    return '/';
  }

  // 读取环境变量配置
  const apiHost = process.env.NEXT_PUBLIC_API_HOST;
  if (!apiHost) {
    return '/'; // 未配置时使用相对路径
  }

  // SSR: 服务端渲染时使用 http 协议
  if (typeof window === 'undefined') {
    return `http://${apiHost}`;
  }

  // CSR: 客户端使用当前协议（支持 http/https 自动切换）
  return `${window.location.protocol}//${apiHost}`;
};

/**
 * 创建 axios 客户端实例
 */
export const apiClient: AxiosInstance = axios.create({
  baseURL: getBaseURL(),
  timeout: 30000, // 30秒超时
  headers: {
    'Content-Type': 'application/json',
    'X-BELLA-CONSOLE': 'true', // 标识控制台请求（Java 后端需要）
  },
  withCredentials: true, // 支持跨域认证（携带 Cookie）
});

/**
 * 请求拦截器
 * - 开发环境日志
 * - 预留公共请求头扩展点
 */
apiClient.interceptors.request.use(
  (config) => {
    // 开发环境：打印请求日志
    if (process.env.NODE_ENV === 'development') {
      console.log(`[API Request] ${config.method?.toUpperCase()} ${config.url}`, {
        params: config.params,
        data: config.data,
      });
    }

    return config;
  },
  (error) => {
    console.error('[API Request Error]', error);
    return Promise.reject(error);
  }
);

/**
 * 响应拦截器
 * - 智能响应格式处理（支持多种后端格式）
 * - HTTP 错误信息规范化
 * - 开发环境日志
 */
apiClient.interceptors.response.use(
  (response: AxiosResponse) => {
    // 开发环境：打印响应日志
    if (process.env.NODE_ENV === 'development') {
        console.log(`[API Response] ${response.config.method?.toUpperCase()} ${response.config.url}`, {
        status: response.status,
        data: response.data,
      });
    }

    const data = response.data;

    // 格式检测：Java 后端包裹格式 { code, data, message }
    if (data && typeof data === 'object' && 'code' in data) {
      const apiData = data as any; // 类型断言以处理动态格式
      if (apiData.code === 200) {
        // 自动解包，返回 data 字段
        return apiData.data;
      } else {
        // 业务错误
        const error: any = new Error(apiData.message || 'API业务错误');
        error.code = apiData.code;
        return Promise.reject(error);
      }
    }

    // 格式2：Next.js API Routes 扁平格式（直接返回数据）
    return data;
  },
  (error: AxiosError) => {
    // 开发环境：打印错误日志
    if (process.env.NODE_ENV === 'development') {
      console.error('[API Error]', {
        url: error.config?.url || 'unknown',
        method: error.config?.method || 'unknown',
        status: error.response?.status || 'unknown',
        data: error.response?.data || {},
        message: error.message || 'Unknown error',
      });
    }

    // HTTP 错误处理
    if (error.response) {
      const { status, data } = error.response;

      switch (status) {
        case 400:
          error.message = (data as any)?.error || '请求参数错误';
          break;
        case 401:
          error.message = (data as any)?.error || '未授权访问';

          // 401 自动重定向逻辑（排除 userInfo 接口）
          if (!error.config?.url?.endsWith('/openapi/userInfo')) {
            // 支持大小写兼容的 Header 读取
            const loginUrl = error.response.headers['X-Redirect-Login'] ||
                            error.response.headers['x-redirect-login'];

            if (loginUrl && typeof window !== 'undefined') {
              // 添加回跳 URL 参数
              const redirectUrl = loginUrl + encodeURIComponent(window.location.href);
              window.location.href = redirectUrl;
              // 返回永不 resolve 的 Promise，阻塞后续请求
              return new Promise(() => {});
            }
          }
          break;
        case 403:
          error.message = (data as any)?.error || '禁止访问';
          break;
        case 404:
          error.message = (data as any)?.error || '资源不存在';
          break;
        case 500:
          error.message = (data as any)?.error || '服务器内部错误';
          break;
        default:
          error.message = (data as any)?.error || `请求失败 (${status})`;
      }
    } else if (error.request) {
      // 网络错误
      error.message = '网络连接失败，请检查网络设置';
    } else {
      // 其他错误
      error.message = error.message || '未知错误';
    }

    return Promise.reject(error);
  }
);

/**
 * GET 请求辅助方法
 * @param url 请求路径
 * @param params 查询参数
 * @param config 额外配置
 */
export async function get<T = any>(
  url: string,
  params?: Record<string, any>,
  config?: AxiosRequestConfig
): Promise<T> {
  return apiClient.get<T, T>(url, { params, ...config });
}

/**
 * POST 请求辅助方法
 * @param url 请求路径
 * @param data 请求体数据
 * @param config 额外配置
 */
export async function post<T = any>(
  url: string,
  data?: any,
  config?: AxiosRequestConfig
): Promise<T> {
  return apiClient.post<T, T>(url, data, config);
}

/**
 * PUT 请求辅助方法
 * @param url 请求路径
 * @param data 请求体数据
 * @param config 额外配置
 */
export async function put<T = any>(
  url: string,
  data?: any,
  config?: AxiosRequestConfig
): Promise<T> {
  return apiClient.put<T, T>(url, data, config);
}

/**
 * DELETE 请求辅助方法
 * @param url 请求路径
 * @param config 额外配置
 */
export async function del<T = any>(
  url: string,
  config?: AxiosRequestConfig
): Promise<T> {
  return apiClient.delete<T, T>(url, config);
}

/**
 * PATCH 请求辅助方法
 * @param url 请求路径
 * @param data 请求体数据
 * @param config 额外配置
 */
export async function patch<T = any>(
  url: string,
  data?: any,
  config?: AxiosRequestConfig
): Promise<T> {
  return apiClient.patch<T, T>(url, data, config);
}

// 默认导出 axios 实例，供高级用户使用
export default apiClient;
