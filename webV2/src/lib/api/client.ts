import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from "axios";
import type { ApiResponse } from "@/types/common";

/**
 * API client configuration
 */
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "/api";
const API_TIMEOUT = 30000;

/**
 * Create axios instance with default configuration
 */
const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: API_TIMEOUT,
  headers: {
    "Content-Type": "application/json",
  },
});

/**
 * Request interceptor
 */
apiClient.interceptors.request.use(
  (config) => {
    // Add authentication token if available
    const token = typeof window !== "undefined" ? localStorage.getItem("token") : null;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

/**
 * Response interceptor
 */
apiClient.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    return response;
  },
  (error) => {
    // Handle common errors
    if (error.response) {
      const status = error.response.status;
      if (status === 401) {
        // Unauthorized - redirect to login
        if (typeof window !== "undefined") {
          window.location.href = "/login";
        }
      } else if (status === 403) {
        // Forbidden
        console.error("Access forbidden");
      } else if (status >= 500) {
        // Server error
        console.error("Server error:", error.response.data);
      }
    }
    return Promise.reject(error);
  }
);

/**
 * Generic GET request
 */
export async function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.get<ApiResponse<T>>(url, config);
  return response.data.data;
}

/**
 * Generic POST request
 */
export async function post<T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.post<ApiResponse<T>>(url, data, config);
  return response.data.data;
}

/**
 * Generic PUT request
 */
export async function put<T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.put<ApiResponse<T>>(url, data, config);
  return response.data.data;
}

/**
 * Generic DELETE request
 */
export async function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.delete<ApiResponse<T>>(url, config);
  return response.data.data;
}

export default apiClient;
