/**
 * OCR 识别类型（对应后端 endpoint）
 */
export interface OCRType {
  /** 显示名称 */
  label: string;
  /** 对应的 API endpoint，作为请求路径和 useModelList 的 key */
  value: string;
}

/**
 * 内置 OCR 类型列表
 */
export const OCR_TYPES: OCRType[] = [
  { label: "通用 OCR", value: "/v1/ocr/general" },
  { label: "身份证", value: "/v1/ocr/idcard" },
  { label: "银行卡", value: "/v1/ocr/bankcard" },
  { label: "港澳台居住证", value: "/v1/ocr/hmt-residence-permit" },
  { label: "临时身份证", value: "/v1/ocr/tmp-idcard" },
];

/**
 * 图片输入方式
 */
export type ImageInputMethod = "upload" | "url" | "fileId" | "base64";

/**
 * OCR 配置参数
 */
export interface OCRConfig {
  /** 识别类型（OCR endpoint 路径） */
  ocrType: string;
  /** 选择的模型名 */
  model: string;
}

/**
 * 图片输入区域状态
 */
export interface ImageInputState {
  /** 当前输入方式 */
  inputMethod: ImageInputMethod;
  /** 上传图片的 base64（含 data URL 前缀） */
  uploadImageBase64: string;
  /** base64 输入框的值 */
  imageBase64: string;
  /** 图片 URL */
  imageUrl: string;
  /** 文件 ID */
  fileId: string;
}

/**
 * OCR 识别结果
 */
export interface OCRResponse {
  [key: string]: unknown;
}

/**
 * ConfigPanel Props
 */
export interface ConfigPanelProps {
  config: OCRConfig;
  onConfigChange: (config: Partial<OCRConfig>) => void;
}

/**
 * ImageInputArea Props
 */
export interface ImageInputAreaProps {
  inputState: ImageInputState;
  onInputStateChange: (state: Partial<ImageInputState>) => void;
  onRecognize: () => void;
  onClear: () => void;
  isLoading: boolean;
}

/**
 * OCRResult Props
 */
export interface OCRResultProps {
  response: OCRResponse | null;
  isLoading: boolean;
  error: string;
}
