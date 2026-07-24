"use client";

import { useState, useCallback, useEffect } from "react";
import { useSearchParams } from "next/navigation";
import { TopBar } from "@/components/layout";
import { ConfigPanel, ImageInputArea, OCRResult } from "./components";
import { useOCRRequest } from "./hooks/useOCRRequest";
import { OCRConfig, ImageInputState, OCR_TYPES } from "./types";

const DEFAULT_CONFIG: OCRConfig = {
  ocrType: OCR_TYPES[0].value,
  model: "",
};

const DEFAULT_INPUT_STATE: ImageInputState = {
  inputMethod: "upload",
  uploadImageBase64: "",
  imageBase64: "",
  imageUrl: "",
  fileId: "",
};

export default function OCRPlaygroundPage() {
  const searchParams = useSearchParams();
  const [config, setConfig] = useState<OCRConfig>(DEFAULT_CONFIG);
  const [inputState, setInputState] = useState<ImageInputState>(DEFAULT_INPUT_STATE);

  // 初始化配置：从 URL 参数读取 ocrType 和 model
  // 职责：统一处理配置初始化逻辑，支持通过 URL 参数预设配置
  // 优先级：URL 参数 > 默认配置
  useEffect(() => {
    const ocrTypeFromUrl = searchParams.get("ocrType");
    const modelFromUrl = searchParams.get("model");

    // 如果 URL 有参数，更新配置
    if (ocrTypeFromUrl || modelFromUrl) {
      setConfig((prev) => ({
        ...prev,
        ...(ocrTypeFromUrl && { ocrType: ocrTypeFromUrl }),
        ...(modelFromUrl && { model: modelFromUrl }),
      }));
    }
  }, [searchParams]);

  const handleConfigChange = useCallback((partial: Partial<OCRConfig>) => {
    setConfig((prev) => ({ ...prev, ...partial }));
  }, []);

  const handleInputStateChange = useCallback((partial: Partial<ImageInputState>) => {
    setInputState((prev) => ({ ...prev, ...partial }));
  }, []);

  const { response, isLoading, error, recognize, clear } = useOCRRequest(
    config,
    inputState
  );

  // 清除：重置输入区 + 清除结果
  const handleClear = useCallback(() => {
    setInputState(DEFAULT_INPUT_STATE);
    clear();
  }, [clear]);

  return (
    <div className="flex flex-col h-full">
      <TopBar title="OCR Playground" description="图像文字识别" />

      {/* 主内容：输入区 + 结果区 + 配置面板 */}
      <div className="flex flex-1 overflow-hidden">
        {/* 左侧：图片输入区 */}
        <div className="flex-1 overflow-hidden flex flex-col p-6 border-r border-border">
          <ImageInputArea
            inputState={inputState}
            onInputStateChange={handleInputStateChange}
            onRecognize={recognize}
            onClear={handleClear}
            isLoading={isLoading}
          />
        </div>

        {/* 中间：识别结果区（独立滚动） */}
        <div className="flex-1 overflow-y-auto p-6 border-r border-border flex flex-col">
          <OCRResult response={response} isLoading={isLoading} error={error} />
        </div>

        {/* 右侧：配置面板 */}
        <ConfigPanel config={config} onConfigChange={handleConfigChange} />
      </div>
    </div>
  );
}
