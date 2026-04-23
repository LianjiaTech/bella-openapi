import axios, { AxiosInstance, AxiosError, AxiosRequestConfig, AxiosResponse } from 'axios';

function normalizeOrigin(value?: string): string {
  if (!value) return '';

  const withProtocol = /^https?:\/\//.test(value) ? value : `http://${value}`;
  return withProtocol.replace(/\/$/, '');
}

/**
 * 动态解析 baseURL
 * - 支持 SSR/CSR 环境
 * - 未配置真实后端时默认走相对路径
 * - 兼容旧配置 NEXT_PUBLIC_API_HOST
 */
export const getBaseURL = (): string => {
  const configuredOrigin = normalizeOrigin(
    process.env.NEXT_PUBLIC_API_ORIGIN ||
    process.env.NEXT_PUBLIC_API_BASE_URL ||
    process.env.NEXT_PUBLIC_API_HOST
  );

  return configuredOrigin || '/';
};

/**
 * 创建 axios 客户端实例
 */
export const apiClient: AxiosInstance = axios.create({
  baseURL: getBaseURL(),
  timeout: 300000, // 300秒超时
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
    // FormData 检测：让浏览器自动设置带 boundary 的 multipart/form-data
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type'];
    }

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

    // 二进制数据检测：Blob、ArrayBuffer 等类型直接返回
    if (data instanceof Blob || data instanceof ArrayBuffer || response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return data;
    }

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
    // 请求取消错误：直接抛出，不做处理
    if (axios.isCancel(error)) {
      return Promise.reject(error);
    }

    // 开发环境：打印错误日志
    if (process.env.NODE_ENV === 'development') {
      const errorInfo: Record<string, any> = {
        message: error.message || 'Unknown error',
      };
      if (error.config) {
        errorInfo.url = error.config.url;
        errorInfo.method = error.config.method;
      }
      if (error.response) {
        errorInfo.status = error.response.status;
        errorInfo.data = error.response.data;
      }
      console.error('[API Error]', errorInfo);
    }

    // HTTP 错误处理
    if (error.response) {
      const { status, data } = error.response;

      // data.error 可能是字符串或对象（OpenAI 兼容格式），统一提取为字符串
      const extractErrorMessage = (fallback: string): string => {
        const err = (data as any)?.error;
        if (typeof err === 'string') return err;
        if (err && typeof err === 'object') return err.message || fallback;
        return (data as any)?.message || fallback;
      };

      switch (status) {
        case 400:
          error.message = extractErrorMessage('请求参数错误');
          break;
        case 401:
          error.message = extractErrorMessage('未授权访问');

          // 401 自动重定向逻辑
          // 检查是否有 X-Redirect-Login 响应头（CAS企业登录模式）
          const loginUrl = error.response.headers['X-Redirect-Login'] || error.response.headers['x-redirect-login'];

          if (loginUrl && typeof window !== 'undefined') {

            // CAS模式：直接跳转到企业登录页
            // 添加回跳 URL 参数（包含当前页面地址）
            const redirectUrl = loginUrl + encodeURIComponent(window.location.href);
            window.location.href = redirectUrl;
            // 返回永不 resolve 的 Promise，阻塞后续请求
            return new Promise(() => {});
          }

          // OAuth模式：没有X-Redirect-Login响应头
          // 由AuthGuard组件处理重定向到/login页面
          break;
        case 403:
          error.message = extractErrorMessage('禁止访问');
          break;
        case 404:
          error.message = extractErrorMessage('资源不存在');
          break;
        case 500:
          error.message = extractErrorMessage('服务器内部错误');
          break;
        default:
          error.message = extractErrorMessage(`请求失败 (${status})`);
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
 * @param config 额外配置（包含 signal 用于请求取消）
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
 * @param config 额外配置（包含 signal 用于请求取消）
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
 * @param config 额外配置（包含 signal 用于请求取消）
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
 * @param config 额外配置（包含 signal 用于请求取消）
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
 * @param config 额外配置（包含 signal 用于请求取消）
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
