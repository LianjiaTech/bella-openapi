"use client";

import { useState, useCallback } from "react";
import { post } from "@/lib/api/client";
import { OCRConfig, ImageInputState, OCRResponse } from "../types";

interface UseOCRRequestResult {
  response: OCRResponse | null;
  isLoading: boolean;
  error: string;
  recognize: () => Promise<void>;
  clear: () => void;
}

export function useOCRRequest(
  config: OCRConfig,
  inputState: ImageInputState
): UseOCRRequestResult {
  const [response, setResponse] = useState<OCRResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");

  const recognize = useCallback(async () => {
    // 校验配置
    if (!config.model) {
      setError("请选择模型");
      return;
    }

    // 校验输入
    const { inputMethod, uploadImageBase64, imageBase64, imageUrl, fileId } =
      inputState;

    if (inputMethod === "upload" && !uploadImageBase64) {
      setError("请上传图片");
      return;
    }
    if (inputMethod === "url" && !imageUrl) {
      setError("请输入图片 URL");
      return;
    }
    if (inputMethod === "fileId" && !fileId) {
      setError("请输入文件 ID");
      return;
    }
    if (inputMethod === "base64" && !imageBase64) {
      setError("请粘贴 Base64 字符串");
      return;
    }

    setIsLoading(true);
    setError("");
    setResponse(null);

    try {
      const requestBody: Record<string, unknown> = {
        model: config.model,
      };

      if (inputMethod === "upload") {
        requestBody.image_base64 = uploadImageBase64;
      } else if (inputMethod === "base64") {
        requestBody.image_base64 = imageBase64;
      } else if (inputMethod === "url") {
        requestBody.image_url = imageUrl;
      } else if (inputMethod === "fileId") {
        requestBody.file_id = fileId;
      }

      const data = await post<OCRResponse>(config.ocrType, requestBody);
      setResponse(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "识别失败，请重试");
    } finally {
      setIsLoading(false);
    }
  }, [config, inputState]);

  const clear = useCallback(() => {
    setResponse(null);
    setError("");
  }, []);

  return { response, isLoading, error, recognize, clear };
}
