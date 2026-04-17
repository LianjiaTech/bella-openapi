import { useState, useEffect } from "react";
import { Model } from "@/lib/types/openapi";
import { getEndpointDetails } from "@/lib/api/meta";

/**
 * 模型列表状态
 */
interface ModelListState {
  /** 模型列表 */
  models: Model[];
  /** 加载状态 */
  loading: boolean;
  /** 错误信息 */
  error: string | null;
}

/**
 * 获取聊天模型列表的 Hook
 * @param endpoint - API 端点路径
 * @param features - 功能特性列表
 * @returns 模型列表状态
 */
export function useModelList(
  endpoint: string = "/v1/chat/completions",
): ModelListState {
  const [state, setState] = useState<ModelListState>({
    models: [],
    loading: true,
    error: null,
  });

  useEffect(() => {
    let isMounted = true;

    async function fetchModels() {
      try {
        setState((prev) => ({ ...prev, loading: true, error: null }));

        const data = await getEndpointDetails(endpoint, "", []);

        if (isMounted) {
          setState({
            models: data.models || [],
            loading: false,
            error: null,
          });
        }
      } catch (err) {
        if (isMounted) {
          setState({
            models: [],
            loading: false,
            error: err instanceof Error ? err.message : "获取模型列表失败",
          });
        }
      }
    }

    fetchModels();

    return () => {
      isMounted = false;
    };
  }, [endpoint]);

  return state;
}
