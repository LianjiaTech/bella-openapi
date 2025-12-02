"use client"

import { useState, useMemo, useEffect } from "react"
import { useSearchParams } from "next/navigation"
import { TopBar } from "@/components/top-bar"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import {
  MessageSquare,
  ImageIcon,
  Mic,
  Search,
  ExternalLink,
  Plus,
  Building,
  Layers,
  Waves,
  Loader
} from "lucide-react"
import Link from "next/link"
import { useLanguage } from "@/components/language-provider"
import { useSidebar } from "@/lib/context/sidebar-context"
import { CategoryTree, EndpointDetails } from "@/lib/types/openapi"
import { getInitialEndpoint } from "@/lib/utils/endpoint-selection"
import { getEndpointDetails } from "@/lib/api/meta"

type CapabilityType = "chat" | "image" | "audio" | "embedding" | "realtime" | "tts" | "ocr"

// 扁平化 CategoryTree 结构，按 endpoint 组织数据
interface EndpointWithCategory {
  categoryCode: string
  categoryName: string
  endpoint: string
  endpointCode: string
  endpointName: string
  ctime: string
  cuName: string
  mtime: string
  muName: string
  status: string
}

function flattenCategoryTrees(trees: CategoryTree[]): EndpointWithCategory[] {
  const result: EndpointWithCategory[] = []

  function traverse(tree: CategoryTree) {
    // 处理当前节点的 endpoints
    if (tree.endpoints && tree.endpoints.length > 0) {
      tree.endpoints.forEach((endpoint) => {
        result.push({
          categoryCode: tree.categoryCode,
          categoryName: tree.categoryName,
          endpoint: endpoint.endpoint,
          endpointCode: endpoint.endpointCode,
          endpointName: endpoint.endpointName,
          ctime: endpoint.ctime,
          cuName: endpoint.cuName,
          mtime: endpoint.mtime,
          muName: endpoint.muName,
          status: endpoint.status,
        })
      })
    }

    // 递归处理子节点
    if (tree.children && tree.children.length > 0) {
      tree.children.forEach((child) => traverse(child))
    }
  }

  trees.forEach((tree) => traverse(tree))
  return result
}


const allModels = [
  {
    id: "gpt-5-pro",
    name: "GPT-5 Pro",
    provider: "OpenAI",
    type: "chat" as CapabilityType,
    icon: MessageSquare,
    tags: ["国外", "超长上下文", "深度思考", "流式"],
    inputTokens: 128000,
    outputTokens: 16000,
    features: ["函数调用", "JSON模式", "视觉理解"],
    pricing: { input: "$0.03/1K", output: "$0.06/1K" },
    description: "最先进的推理和代码生成能力",
  },
  {
    id: "gpt-5-mini",
    name: "GPT-5 Mini",
    provider: "OpenAI",
    type: "chat" as CapabilityType,
    icon: MessageSquare,
    tags: ["国外", "流式"],
    inputTokens: 16000,
    outputTokens: 4000,
    features: ["快速响应", "低成本"],
    pricing: { input: "$0.001/1K", output: "$0.002/1K" },
    description: "快速且经济实惠的模型",
  },
  {
    id: "claude-sonnet-4",
    name: "Claude Sonnet 4",
    provider: "Anthropic",
    type: "chat" as CapabilityType,
    icon: MessageSquare,
    tags: ["国外", "超长上下文", "流式"],
    inputTokens: 200000,
    outputTokens: 8000,
    features: ["长文本理解", "多语言", "代码生成"],
    pricing: { input: "$0.015/1K", output: "$0.03/1K" },
    description: "平衡性能与成本的Claude模型",
  },
  {
    id: "qwen-plus",
    name: "通义千问 Plus",
    provider: "阿里云",
    type: "chat" as CapabilityType,
    icon: MessageSquare,
    tags: ["国内", "超长上下文", "流式"],
    inputTokens: 32000,
    outputTokens: 8000,
    features: ["中文优化", "多模态"],
    pricing: { input: "¥0.004/1K", output: "$0.012/1K" },
    description: "阿里云自研大语言模型",
  },
  {
    id: "my-custom-model",
    name: "Custom Model",
    provider: "内部",
    type: "chat" as CapabilityType,
    icon: Building,
    tags: ["内部", "私有渠道", "流式"],
    inputTokens: 8000,
    outputTokens: 4000,
    features: ["定制化", "私有部署"],
    pricing: { input: "自定义", output: "自定义" },
    description: "企业内部定制模型",
  },
  {
    id: "dall-e-3",
    name: "DALL-E 3",
    provider: "OpenAI",
    type: "image" as CapabilityType,
    icon: ImageIcon,
    tags: ["国外", "文生图"],
    inputTokens: 0,
    outputTokens: 0,
    features: ["高质量图像", "1024x1024"],
    pricing: { input: "$0.04/image", output: "-" },
    description: "最先进的文本生成图像模型",
  },
  {
    id: "stable-diffusion-xl",
    name: "Stable Diffusion XL",
    provider: "Stability AI",
    type: "image" as CapabilityType,
    icon: ImageIcon,
    tags: ["国外", "文生图", "图生图"],
    inputTokens: 0,
    outputTokens: 0,
    features: ["开源", "高分辨率"],
    pricing: { input: "$0.02/image", output: "-" },
    description: "开源的图像生成模型",
  },
  {
    id: "whisper-large",
    name: "Whisper Large",
    provider: "OpenAI",
    type: "audio" as CapabilityType,
    icon: Mic,
    tags: ["国外", "语音识别"],
    inputTokens: 0,
    outputTokens: 0,
    features: ["多语言", "高准确率"],
    pricing: { input: "$0.006/min", output: "-" },
    description: "高精度语音识别模型",
  },
  {
    id: "text-embedding-3-large",
    name: "Text Embedding 3 Large",
    provider: "OpenAI",
    type: "embedding" as CapabilityType,
    icon: Layers,
    tags: ["国外", "高维度"],
    inputTokens: 8192,
    outputTokens: 0,
    features: ["3072维", "语义搜索"],
    pricing: { input: "$0.00013/1K", output: "-" },
    description: "高性能文本嵌入模型",
  },
  {
    id: "gpt-4o-realtime",
    name: "GPT-4o Realtime",
    provider: "OpenAI",
    type: "realtime" as CapabilityType,
    icon: Waves,
    tags: ["国外", "实时对话", "流式"],
    inputTokens: 128000,
    outputTokens: 16000,
    features: ["低延迟", "语音输入输出"],
    pricing: { input: "$0.06/1K", output: "$0.12/1K" },
    description: "实时语音对话模型",
  },
]

const tagColors = [
  "bg-green-500/10 text-green-500 border-green-500/20",
  "bg-blue-500/10 text-blue-500 border-blue-500/20",
  "bg-purple-500/10 text-purple-500 border-purple-500/20",
  "bg-orange-500/10 text-orange-500 border-orange-500/20",
  "bg-pink-500/10 text-pink-500 border-pink-500/20",
  "bg-indigo-500/10 text-indigo-500 border-indigo-500/20",
  "bg-cyan-500/10 text-cyan-500 border-cyan-500/20",
  "bg-red-500/10 text-red-500 border-red-500/20",
]

export default function ModelsPage() {
  const searchParams = useSearchParams()
  const [selectedCapability, setSelectedCapability] = useState<string>("")
  const [searchQuery, setSearchQuery] = useState("")
  const [selectedTags, setSelectedTags] = useState<string[]>([])
  const [endpointData, setEndpointData] = useState<EndpointDetails | null>(null)
  const [loading, setLoading] = useState(false)
  const { t } = useLanguage()
  const { categoryTrees } = useSidebar()

  const DATA_MOCK =  {
    endpoint: "/v1/chat/completions",
    models: [
        {
            "modelName": "qwq-plus",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/getting-started/models",
            "properties": "{\"max_input_context\":98304,\"max_output_context\":8192}",
            "features": "{\"stream\":true,\"function_call\":true,\"vision\":false,\"json_format\":false,\"stream_function_call\":true,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 1,
                    "output": 1,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "1",
                    "输出token单价（分/千token）": "1"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen3-vl-235b-a22b-thinking",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/models",
            "properties": "{\"max_input_context\":126976,\"max_output_context\":32768}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":false,\"parallel_tool_calls\":false,\"vision\":true,\"json_format\":true,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.2,
                    "output": 2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.2",
                    "输出token单价（分/千token）": "2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen3-vl-235b-a22b-instruct",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/models",
            "properties": "{\"max_input_context\":129024,\"max_output_context\":32768}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":false,\"parallel_tool_calls\":false,\"vision\":true,\"json_format\":true,\"reason_content\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.2,
                    "output": 0.8,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.2",
                    "输出token单价（分/千token）": "0.8"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen3-coder-plus",
            "documentUrl": "https://bailian.console.aliyun.com/?spm=5176.29597918.J_bNSze_09Z5SDm7ZHdpz3Y.1.7a987b08m5JeEe&tab=model#/model-market/detail/qwen3-coder-plus",
            "properties": "{\"max_input_context\":1000000,\"max_output_context\":100000}",
            "features": "{\"json_format\":true,\"stream_function_call\":true,\"function_call\":true,\"support_max_tokens\":true,\"vision\":false,\"support_top_P\":true,\"support_temperature\":true,\"stream\":true,\"prompt_cache\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 1,
                    "output": 4,
                    "cachedRead": 0.2,
                    "cachedCreation": 1.25,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "1",
                    "输出token单价（分/千token）": "4",
                    "命中缓存token单价（分/千token）": "0.2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen3-coder-480b",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/models?spm=a2c4g.11186623.help-menu-search-2400256.d_1#6c45e49509gtr",
            "properties": "{\"max_input_context\":204796,\"max_output_context\":65531}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"vision\":false,\"json_format\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 1.5,
                    "output": 6,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "1.5",
                    "输出token单价（分/千token）": "6"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen3-8b",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/models",
            "properties": "{\"max_input_context\":129024,\"max_output_context\":8191}",
            "features": "{\"vision\":false,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.05,
                    "output": 0.2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.05",
                    "输出token单价（分/千token）": "0.2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen3-4b",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/models",
            "properties": "{\"max_input_context\":129024,\"max_output_context\":8191}",
            "features": "{\"vision\":false,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.03,
                    "output": 0.12,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.03",
                    "输出token单价（分/千token）": "0.12"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen3-32b",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/models",
            "properties": "{\"max_input_context\":129024,\"max_output_context\":8192}",
            "features": "{\"vision\":false,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.2,
                    "output": 0.8,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.2",
                    "输出token单价（分/千token）": "0.8"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen3-30b-a3b-thinking-2507",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/models",
            "properties": "{\"max_input_context\":126976,\"max_output_context\":32768}",
            "features": "{\"vision\":false,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":true,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.075,
                    "output": 0.3,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.075",
                    "输出token单价（分/千token）": "0.3"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen3-30b-a3b",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/models",
            "properties": "{\"max_input_context\":129024,\"max_output_context\":8192}",
            "features": "{\"vision\":false,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.15,
                    "output": 0.6,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.15",
                    "输出token单价（分/千token）": "0.6"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen3-235b-a22b-thinking-2507",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/models",
            "properties": "{\"max_input_context\":126976,\"max_output_context\":32768}",
            "features": "{\"vision\":false,\"json_format\":false,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":true,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.2,
                    "output": 2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.2",
                    "输出token单价（分/千token）": "2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen3-235b-a22b-instruct-2507",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/use-qwen-by-calling-api?spm=a2c4g.11186623.0.0.6afa453an5glGq",
            "properties": "{\"max_input_context\":129024,\"max_output_context\":32768}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"vision\":false,\"json_format\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.2,
                    "output": 0.8,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.2",
                    "输出token单价（分/千token）": "0.8"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen3-235b-a22b",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/models",
            "properties": "{\"max_input_context\":129024,\"max_output_context\":8187}",
            "features": "{\"vision\":false,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":true,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.4,
                    "output": 1.2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.4",
                    "输出token单价（分/千token）": "1.2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen3-14b",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/models",
            "properties": "{\"max_input_context\":129024,\"max_output_context\":8192}",
            "features": "{\"vision\":false,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.1,
                    "output": 0.4,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.1",
                    "输出token单价（分/千token）": "0.4"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen3-1.7b",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/models",
            "properties": "{\"max_input_context\":30720,\"max_output_context\":8191}",
            "features": "{\"vision\":false,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.03,
                    "output": 0.12,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.03",
                    "输出token单价（分/千token）": "0.12"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen3-0.6b",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/models",
            "properties": "{\"max_input_context\":30720,\"max_output_context\":8191}",
            "features": "{\"vision\":false,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.03,
                    "output": 0.12,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.03",
                    "输出token单价（分/千token）": "0.12"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen-vl-plus-latest",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/developer-reference/qwen-vl-api",
            "properties": "{\"max_input_context\":129024,\"max_output_context\":8192}",
            "features": "{\"json_format\":true,\"stream_function_call\":true,\"vision\":true,\"stream\":true,\"parallel_tool_calls\":false,\"function_call\":true,\"json_schema\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.15,
                    "output": 0.45,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.15",
                    "输出token单价（分/千token）": "0.45"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen-vl-plus",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/developer-reference/qwen-vl-api",
            "properties": "{\"max_input_context\":6000,\"max_output_context\":1500}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"vision\":true,\"json_format\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.15,
                    "output": 0.45,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.15",
                    "输出token单价（分/千token）": "0.45"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen-vl-max-latest",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/developer-reference/qwen-vl-api",
            "properties": "{\"max_input_context\":129024,\"max_output_context\":8192}",
            "features": "{\"vision\":true,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":true,\"json_schema\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.3,
                    "output": 0.9,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.3",
                    "输出token单价（分/千token）": "0.9"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen-vl-max",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/developer-reference/qwen-vl-api",
            "properties": "{\"max_input_context\":32768,\"max_output_context\":8192}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"vision\":true,\"json_format\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.3,
                    "output": 0.9,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.3",
                    "输出token单价（分/千token）": "0.9"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen-turbo",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/developer-reference/what-is-qwen-llm",
            "properties": "{\"max_input_context\":1000000,\"max_output_context\":8192}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"vision\":false,\"json_format\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.03,
                    "output": 0.06,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.03",
                    "输出token单价（分/千token）": "0.06"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen-plus-latest",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/developer-reference/what-is-qwen-llm",
            "properties": "{\"max_input_context\":98304,\"max_output_context\":16384}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"vision\":false,\"json_format\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.08,
                    "output": 1.6,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.08",
                    "输出token单价（分/千token）": "1.6"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen-plus",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/developer-reference/what-is-qwen-llm",
            "properties": "{\"max_input_context\":129024,\"max_output_context\":8192}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"vision\":false,\"json_format\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.08,
                    "output": 0.2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.08",
                    "输出token单价（分/千token）": "0.2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen-max-latest",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/developer-reference/what-is-qwen-llm",
            "properties": "{\"max_input_context\":30720,\"max_output_context\":8192}",
            "features": "{\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.24,
                    "output": 0.96,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.24",
                    "输出token单价（分/千token）": "0.96"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen-max",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/developer-reference/what-is-qwen-llm",
            "properties": "{\"max_input_context\":30720,\"max_output_context\":8192}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"vision\":false,\"json_format\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.24,
                    "output": 0.96,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.24",
                    "输出token单价（分/千token）": "0.96"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "qwen-long",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/developer-reference/qwen-long-api",
            "properties": "{\"max_input_context\":10000000,\"max_output_context\":6000}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"vision\":false,\"json_format\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.05,
                    "output": 0.2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.05",
                    "输出token单价（分/千token）": "0.2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "peanut_quality_grading",
            "documentUrl": "",
            "properties": "{\"max_input_context\":32768,\"max_output_context\":32768}",
            "features": "{\"stream\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.05,
                    "output": 0.1,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.05",
                    "输出token单价（分/千token）": "0.1"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "kimi-k2-thinking",
            "documentUrl": "https://moonshotai.github.io/Kimi-K2/thinking.html",
            "properties": "{\"max_input_context\":216000,\"max_output_context\":16000}",
            "features": "{\"support_top_P\":true,\"json_format\":true,\"stream_function_call\":true,\"function_call\":true,\"support_max_tokens\":true,\"vision\":false,\"support_temperature\":true,\"stream\":true,\"reason_content_input\":false,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.4,
                    "output": 1.6,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.4",
                    "输出token单价（分/千token）": "1.6"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "kimi-k2",
            "documentUrl": "https://platform.moonshot.cn/docs/pricing/chat#%E7%94%9F%E6%88%90%E6%A8%A1%E5%9E%8B-kimi-k2",
            "properties": "{\"max_input_context\":128000,\"max_output_context\":16000}",
            "features": "{\"vision\":false,\"json_format\":true,\"stream_function_call\":false,\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.4,
                    "output": 1.6,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.4",
                    "输出token单价（分/千token）": "1.6"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "gpt-5.1-codex-mini",
            "documentUrl": "https://platform.openai.com/docs/models",
            "properties": "{\"max_input_context\":272000,\"max_output_context\":128000}",
            "features": "{\"support_top_P\":false,\"json_format\":true,\"stream_function_call\":true,\"function_call\":true,\"support_max_tokens\":false,\"support_temperature\":false,\"parallel_tool_calls\":true,\"stream\":true,\"json_schema\":true,\"reason_content_input\":false,\"reason_content\":true,\"prompt_cache\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.175,
                    "output": 1.4,
                    "cachedRead": 0.0175,
                    "cachedCreation": 0.21875,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.175",
                    "输出token单价（分/千token）": "1.4",
                    "命中缓存token单价（分/千token）": "0.0175"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "gpt-5.1-codex",
            "documentUrl": "https://platform.openai.com/docs/models",
            "properties": "{\"max_input_context\":272000,\"max_output_context\":128000}",
            "features": "{\"json_format\":true,\"stream_function_call\":true,\"function_call\":true,\"support_max_tokens\":false,\"reason_content_input\":false,\"reason_content\":true,\"prompt_cache\":true,\"vision\":true,\"stream\":true,\"json_schema\":true,\"parallel_tool_calls\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.875,
                    "output": 7,
                    "cachedRead": 0.0875,
                    "cachedCreation": 1.09375,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.875",
                    "输出token单价（分/千token）": "7",
                    "命中缓存token单价（分/千token）": "0.0875"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "gpt-5.1",
            "documentUrl": "https://platform.openai.com/docs/models",
            "properties": "{\"max_input_context\":272000,\"max_output_context\":128000}",
            "features": "{\"json_format\":true,\"stream_function_call\":true,\"function_call\":true,\"support_max_tokens\":true,\"vision\":true,\"parallel_tool_calls\":true,\"stream\":true,\"json_schema\":true,\"reason_content_input\":false,\"reason_content\":true,\"prompt_cache\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.875,
                    "output": 7,
                    "cachedRead": 0.0875,
                    "cachedCreation": 1.09375,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.875",
                    "输出token单价（分/千token）": "7",
                    "命中缓存token单价（分/千token）": "0.0875"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "gpt-5-nano",
            "documentUrl": "https://platform.openai.com/docs/models",
            "properties": "{\"max_input_context\": 272000,\"max_output_context\": 128000}",
            "features": "{\"vision\":true,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"stream\":true,\"function_call\":true,\"json_schema\":true,\"reason_content\":true,\"support_temperature\":false,\"support_top_P\":false,\"support_max_tokens\":false,\"prompt_cache\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.035,
                    "output": 0.28,
                    "cachedRead": 0.0035,
                    "cachedCreation": 0.043750000000000004,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.035",
                    "输出token单价（分/千token）": "0.28",
                    "命中缓存token单价（分/千token）": "0.0035"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "gpt-5-mini",
            "documentUrl": "https://platform.openai.com/docs/models",
            "properties": "{\"max_input_context\": 272000,\"max_output_context\": 128000}",
            "features": "{\"vision\":true,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"stream\":true,\"function_call\":true,\"json_schema\":true,\"reason_content\":true,\"support_temperature\":false,\"support_top_P\":false,\"support_max_tokens\":false,\"prompt_cache\":true,\"prompt_cache\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.175,
                    "output": 1.4,
                    "cachedRead": 0.0175,
                    "cachedCreation": 0.21875,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.175",
                    "输出token单价（分/千token）": "1.4",
                    "命中缓存token单价（分/千token）": "0.0175"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "gpt-5-codex",
            "documentUrl": "https://platform.openai.com/docs/models",
            "properties": "{\"max_input_context\": 272000,\"max_output_context\": 128000}",
            "features": "{\"vision\":true,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"stream\":true,\"function_call\":true,\"json_schema\":true,\"reason_content_input\":true,\"reason_content\":true,\"prompt_cache\":true,\"support_temperature\":false,\"support_top_P\":false,\"support_max_tokens\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.875,
                    "output": 7,
                    "cachedRead": 0.0875,
                    "cachedCreation": 1.09375,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.875",
                    "输出token单价（分/千token）": "7",
                    "命中缓存token单价（分/千token）": "0.0875"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "gpt-5-chat",
            "documentUrl": "https://platform.openai.com/docs/models",
            "properties": "{\"max_input_context\": 272000,\"max_output_context\": 128000}",
            "features": "{\"vision\":true,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"stream\":true,\"function_call\":true,\"json_schema\":true,\"prompt_cache\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.875,
                    "output": 7,
                    "cachedRead": 0.0875,
                    "cachedCreation": 1.09375,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.875",
                    "输出token单价（分/千token）": "7",
                    "命中缓存token单价（分/千token）": "0.0875"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "gpt-5",
            "documentUrl": "https://platform.openai.com/docs/models",
            "properties": "{\"max_input_context\": 272000,\"max_output_context\": 128000}",
            "features": "{\"vision\":true,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"stream\":true,\"function_call\":true,\"json_schema\":true,\"reason_content\":true,\"support_temperature\":false,\"support_top_P\":false,\"support_max_tokens\":false,\"prompt_cache\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.875,
                    "output": 7,
                    "cachedRead": 0.0875,
                    "cachedCreation": 1.09375,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.875",
                    "输出token单价（分/千token）": "7",
                    "命中缓存token单价（分/千token）": "0.0875"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "gpt-4o-mini",
            "documentUrl": "https://platform.openai.com/docs/models",
            "properties": "{\"max_input_context\":128000,\"max_output_context\":16384}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"vision\":true,\"json_format\":true,\"json_schema\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.105,
                    "output": 0.42,
                    "cachedRead": 0.0525,
                    "cachedCreation": 0.13125,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.105",
                    "输出token单价（分/千token）": "0.42",
                    "命中缓存token单价（分/千token）": "0.0525"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "gpt-4o",
            "documentUrl": "https://platform.openai.com/docs/models",
            "properties": "{\"max_input_context\":128000,\"max_output_context\": 16384}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"vision\":true,\"json_format\":true,\"json_schema\":true,\"prompt_cache\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 1.75,
                    "output": 7,
                    "cachedRead": 0.175,
                    "cachedCreation": 2.1875,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "1.75",
                    "输出token单价（分/千token）": "7",
                    "命中缓存token单价（分/千token）": "0.175"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "gemini-3-pro-image",
            "documentUrl": "",
            "properties": "{\"max_input_context\":2000000,\"max_output_context\":60000}",
            "features": "{\"vision\":true,\"stream\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 2.8,
                    "output": 12.6,
                    "imageInput": 2.8,
                    "imageOutput": 86.4,
                    "cachedRead": 0.28,
                    "cachedCreation": 3.5,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "2.8",
                    "输出token单价（分/千token）": "12.6",
                    "命中缓存token单价（分/千token）": "0.28",
                    "图片输入token单价（分/千token）": "2.8",
                    "图片输出token单价（分/千token）": "86.4"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "gemini-3-pro",
            "documentUrl": "",
            "properties": "{\"max_input_context\":1000000,\"max_output_context\":65536}",
            "features": "{\"json_format\":true,\"stream_function_call\":true,\"function_call\":true,\"stream\":true,\"reason_content\":true,\"prompt_cache\":false,\"vision\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 2.8,
                    "output": 12.6,
                    "cachedRead": 0.28,
                    "cachedCreation": 3.5,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "2.8",
                    "输出token单价（分/千token）": "12.6",
                    "命中缓存token单价（分/千token）": "0.28"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "gemini-2.5-flash-image-preview",
            "documentUrl": "https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-5-flash?hl=zh-cn#image",
            "properties": "{\"max_input_context\":2000000,\"max_output_context\":60000}",
            "features": "{\"vision\":true,\"json_format\":false,\"stream_function_call\":false,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":false,\"reason_content\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.21,
                    "output": 1.75,
                    "imageInput": 162.7907,
                    "imageOutput": 21.1628,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.21",
                    "输出token单价（分/千token）": "1.75",
                    "图片输入token单价（分/千token）": "162.7907",
                    "图片输出token单价（分/千token）": "21.1628"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "farui-plus",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/developer-reference/qwen-vl-api",
            "properties": "{\"max_input_context\":12000,\"max_output_context\":2000}",
            "features": "{\"stream\":true,\"function_call\":false,\"stream_function_call\":false,\"parallel_tool_calls\":false,\"vision\":false,\"json_format\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 2,
                    "output": 2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "2",
                    "输出token单价（分/千token）": "2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "ernie-x1-turbo-32k",
            "documentUrl": "https://console.bce.baidu.com/qianfan/modelcenter/model/buildIn/detail/am-an73qfuiy92d?tab=version",
            "properties": "{\"max_input_context\":24000,\"max_output_context\":28000}",
            "features": "{\"vision\":false,\"json_format\":false,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":true,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.1,
                    "output": 0.4,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.1",
                    "输出token单价（分/千token）": "0.4"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "ernie-4.5-vl-28b-a3b",
            "documentUrl": "https://console.bce.baidu.com/qianfan/modelcenter/model/buildIn/detail/am-c8fe1b412vwn?tab=version#ERNIE-4.5-VL-28B-A3B",
            "properties": "{\"max_input_context\":30000,\"max_output_context\":8000}",
            "features": "{\"vision\":false,\"json_format\":false,\"stream_function_call\":false,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":false,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.15,
                    "output": 1.5,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.15",
                    "输出token单价（分/千token）": "1.5"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "ernie-4.5-turbo-vl-32k-preview",
            "documentUrl": "https://console.bce.baidu.com/qianfan/modelcenter/model/buildIn/detail/am-04vq365sduvu?tab=version",
            "properties": "{\"max_input_context\":27000,\"max_output_context\":12000}",
            "features": "{\"vision\":false,\"json_format\":false,\"stream_function_call\":false,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":false,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.3,
                    "output": 0.9,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.3",
                    "输出token单价（分/千token）": "0.9"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "ernie-4.5-turbo-vl-32k",
            "documentUrl": "https://console.bce.baidu.com/qianfan/modelcenter/model/buildIn/detail/am-04vq365sduvu?tab=version",
            "properties": "{\"max_input_context\":27000,\"max_output_context\":12000}",
            "features": "{\"vision\":false,\"json_format\":false,\"stream_function_call\":false,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":false,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.3,
                    "output": 0.9,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.3",
                    "输出token单价（分/千token）": "0.9"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "ernie-4.5-turbo-128k",
            "documentUrl": "https://console.bce.baidu.com/qianfan/modelcenter/model/buildIn/detail/am-xpgukxjf6s0r?tab=version",
            "properties": "{\"max_input_context\":123000,\"max_output_context\":12000}",
            "features": "{\"vision\":false,\"json_format\":false,\"stream_function_call\":false,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":false,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.08,
                    "output": 0.32,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.08",
                    "输出token单价（分/千token）": "0.32"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "ernie-4.5-8k-preview",
            "documentUrl": "https://console.bce.baidu.com/qianfan/modelcenter/model/buildIn/detail/am-c8fe1b412vwn?tab=version#ERNIE-4.5-8K-Preview",
            "properties": "{\"max_input_context\":5000,\"max_output_context\":2000}",
            "features": "{\"vision\":false,\"json_format\":false,\"stream_function_call\":false,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":false,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.4,
                    "output": 1.6,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.4",
                    "输出token单价（分/千token）": "1.6"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "ernie-4.5-21b-a3b",
            "documentUrl": "https://console.bce.baidu.com/qianfan/modelcenter/model/buildIn/detail/am-c8fe1b412vwn?tab=version#ERNIE-4.5-21B-A3B",
            "properties": "{\"max_input_context\":120000,\"max_output_context\":8000}",
            "features": "{\"vision\":false,\"json_format\":false,\"stream_function_call\":false,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":false,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.12,
                    "output": 0.48,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.12",
                    "输出token单价（分/千token）": "0.48"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "ernie-4.5-0.3b",
            "documentUrl": "https://console.bce.baidu.com/qianfan/modelcenter/model/buildIn/detail/am-c8fe1b412vwn?tab=version#ERNIE-4.5-0.3B",
            "properties": "{\"max_input_context\":120000,\"max_output_context\":8000}",
            "features": "{\"vision\":false,\"json_format\":false,\"stream_function_call\":false,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":false,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.015,
                    "output": 0.06,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.015",
                    "输出token单价（分/千token）": "0.06"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "doubao-seed-code",
            "documentUrl": "https://www.volcengine.com/docs/82379/1949118?lang=zh",
            "properties": "{\"max_input_context\":224000,\"max_output_context\":32000}",
            "features": "{\"stream\": true,\"function_call\": true,\"stream_function_call\": true,\"vision\": true,\"json_format\": true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.28,
                    "output": 1.6,
                    "cachedRead": 0.024,
                    "cachedCreation": 0.0017,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.28",
                    "输出token单价（分/千token）": "1.6",
                    "命中缓存token单价（分/千token）": "0.024",
                    "创建缓存token单价（分/千token）": "0.0017"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "doubao-seed-1.6-thinking",
            "documentUrl": "https://www.volcengine.com/docs/82379/1593703",
            "properties": "{\"max_input_context\":224000,\"max_output_context\":16000}",
            "features": "{\"vision\":true,\"json_format\":true,\"stream\":true,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.08,
                    "output": 0.8,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.08",
                    "输出token单价（分/千token）": "0.8"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "doubao-seed-1.6-lite",
            "documentUrl": "https://www.volcengine.com/docs/82379/1874969",
            "properties": "{\"max_input_context\":224000,\"max_output_context\":4000}",
            "features": "{\"vision\":true,\"stream\":true,\"function_call\":true,\"json_format\":true,\"reason_content_input\":false,\"reason_content\":true,\"json_schema\":true,\"video\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.06,
                    "output": 0.4,
                    "cachedRead": 0.006,
                    "cachedCreation": 0.075,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.06",
                    "输出token单价（分/千token）": "0.4",
                    "命中缓存token单价（分/千token）": "0.006"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "doubao-seed-1.6-flash",
            "documentUrl": "https://www.volcengine.com/docs/82379/1593703",
            "properties": "{\"max_input_context\":224000,\"max_output_context\":16000}",
            "features": "{\"vision\":true,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"stream\":true,\"function_call\":true,\"json_schema\":true,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.0075,
                    "output": 0.075,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.0075",
                    "输出token单价（分/千token）": "0.075"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "doubao-seed-1.6-251015",
            "documentUrl": "https://www.volcengine.com/docs/82379/1593702",
            "properties": "{\"max_input_context\":224000,\"max_output_context\":32000}",
            "features": "{\"stream\":true,\"function_call\":true,\"vision\":true,\"json_format\":true,\"json_schema\":true,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.08,
                    "output": 0.8,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.08",
                    "输出token单价（分/千token）": "0.8"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "doubao-seed-1.6",
            "documentUrl": "https://www.volcengine.com/docs/82379/1593702",
            "properties": "{\"max_input_context\":224000,\"max_output_context\":16000}",
            "features": "{\"vision\":true,\"json_format\":true,\"stream\":true,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.08,
                    "output": 0.8,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.08",
                    "输出token单价（分/千token）": "0.8"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "doubao-pro-32k-fc",
            "documentUrl": "https://platform.moonshot.cn/docs",
            "properties": "{\"max_input_context\":32000,\"max_output_context\":4090}",
            "features": "{\"stream_function_call\":true,\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.08,
                    "output": 0.2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.08",
                    "输出token单价（分/千token）": "0.2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "doubao-1.5-pro-32k-250715",
            "documentUrl": "https://www.volcengine.com/docs/82379/1330310",
            "properties": "{\"max_input_context\":20000,\"max_output_context\":12000}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"vision\":false,\"json_format\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.08,
                    "output": 0.2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.08",
                    "输出token单价（分/千token）": "0.2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "deepseek-v3.1",
            "documentUrl": "https://www.volcengine.com/docs/82379/1801298",
            "properties": "{\"max_input_context\":96000,\"max_output_context\":32000}",
            "features": "{\"vision\":false,\"reason_content\":true,\"stream\":true,\"function_call\":true,\"parallel_tool_calls\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.4,
                    "output": 1.2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.4",
                    "输出token单价（分/千token）": "1.2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "deepseek-reasoner-v3.2",
            "documentUrl": "https://api-docs.deepseek.com/zh-cn/quick_start/pricing",
            "properties": "{\"max_input_context\":64000,\"max_output_context\":16000}",
            "features": "{\"json_format\":true,\"stream_function_call\":true,\"stream\":true,\"function_call\":true,\"agent_thought\":true,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.2,
                    "output": 0.3,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.2",
                    "输出token单价（分/千token）": "0.3"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "deepseek-reasoner-0528",
            "documentUrl": "https://api-docs.deepseek.com/",
            "properties": "{\"max_input_context\":64000,\"max_output_context\":8192}",
            "features": "{\"function_call\":true,\"stream\":true,\"reason_content\":true,\"stream_function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.4,
                    "output": 1.6,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.4",
                    "输出token单价（分/千token）": "1.6"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "deepseek-reasoner",
            "documentUrl": "https://api-docs.deepseek.com/",
            "properties": "{\"max_input_context\":64000,\"max_output_context\":16000}",
            "features": "{\"json_format\":false,\"stream_function_call\":true,\"stream\":true,\"function_call\":true,\"agent_thought\":true,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.4,
                    "output": 1.6,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.4",
                    "输出token单价（分/千token）": "1.6"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "deepseek-chat-v3.2",
            "documentUrl": "https://api-docs.deepseek.com/zh-cn/quick_start/pricing",
            "properties": "{\"max_input_context\":64000,\"max_output_context\":8192}",
            "features": "{\"json_format\":true,\"stream_function_call\":true,\"stream\":true,\"function_call\":true,\"parallel_tool_calls\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.2,
                    "output": 0.3,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.2",
                    "输出token单价（分/千token）": "0.3"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "deepseek-chat",
            "documentUrl": "https://api-docs.deepseek.com/zh-cn/",
            "properties": "{\"max_input_context\":64000,\"max_output_context\":8192}",
            "features": "{\"json_format\":false,\"stream_function_call\":true,\"stream\":true,\"function_call\":true,\"parallel_tool_calls\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.2,
                    "output": 0.8,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.2",
                    "输出token单价（分/千token）": "0.8"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "claude-4.5-sonnet",
            "documentUrl": "https://docs.anthropic.com/en/docs/intro-to-claude",
            "properties": "{\"max_input_context\":200000,\"max_output_context\":64000}",
            "features": "{\"vision\":true,\"json_format\":true,\"stream\":true,\"parallel_tool_calls\":true,\"stream_function_call\":true,\"reason_content\":true,\"function_call\":true,\"prompt_cache\":true,\"reason_content_input\":true,\"support_top_P\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 2.1,
                    "output": 10.5,
                    "cachedRead": 0.21,
                    "cachedCreation": 2.625,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "2.1",
                    "输出token单价（分/千token）": "10.5",
                    "命中缓存token单价（分/千token）": "0.21"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "claude-4.5-haiku",
            "documentUrl": "https://docs.claude.com/en/docs/about-claude/models/whats-new-claude-4-5",
            "properties": "{\"max_input_context\":200000,\"max_output_context\":64000}",
            "features": "{\"vision\":true,\"json_format\":true,\"stream\":true,\"parallel_tool_calls\":true,\"stream_function_call\":true,\"reason_content\":true,\"function_call\":true,\"prompt_cache\":true,\"reason_content_input\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.7,
                    "output": 3.5,
                    "cachedRead": 0.07,
                    "cachedCreation": 0.875,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.7",
                    "输出token单价（分/千token）": "3.5",
                    "命中缓存token单价（分/千token）": "0.07"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "claude-4-sonnet",
            "documentUrl": "https://docs.anthropic.com/en/docs/intro-to-claude",
            "properties": "{\"max_input_context\":200000,\"max_output_context\":64000}",
            "features": "{\"vision\":true,\"json_format\":true,\"stream\":true,\"parallel_tool_calls\":true,\"stream_function_call\":true,\"reason_content\":true,\"function_call\":true,\"prompt_cache\":true,\"reason_content_input\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 2.1,
                    "output": 10.5,
                    "cachedRead": 0.21,
                    "cachedCreation": 2.625,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "2.1",
                    "输出token单价（分/千token）": "10.5",
                    "命中缓存token单价（分/千token）": "0.21"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "claude-3.7-sonnet",
            "documentUrl": "https://docs.anthropic.com/en/docs/intro-to-claude",
            "properties": "{\"max_input_context\":200000,\"max_output_context\":32000}",
            "features": "{\"vision\":true,\"json_format\":false,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":true,\"reason_content\":true,\"prompt_cache\":true,\"reason_content_input\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 2.1,
                    "output": 10.5,
                    "cachedRead": 0.21,
                    "cachedCreation": 2.625,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "2.1",
                    "输出token单价（分/千token）": "10.5",
                    "命中缓存token单价（分/千token）": "0.21"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "claude-3.5-sonnet",
            "documentUrl": "https://docs.anthropic.com/en/docs/intro-to-claude",
            "properties": "{\"max_input_context\":200000,\"max_output_context\":8192}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":true,\"parallel_tool_calls\":true,\"vision\":true,\"json_format\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 2.1,
                    "output": 10.5,
                    "cachedRead": 0.21,
                    "cachedCreation": 2.625,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "2.1",
                    "输出token单价（分/千token）": "10.5",
                    "命中缓存token单价（分/千token）": "0.21"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "ali-qwen25-72b-base",
            "documentUrl": "",
            "properties": "{\"max_input_context\":8192,\"max_output_context\":8192}",
            "features": "{\"stream\":true,\"function_call\":false,\"stream_function_call\":false,\"parallel_tool_calls\":false,\"vision\":false,\"json_format\":false,\"json_schema\":false,\"agent_thought\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.4,
                    "output": 1.2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.4",
                    "输出token单价（分/千token）": "1.2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "ali-qwen25-32b-base",
            "documentUrl": "",
            "properties": "{\"max_input_context\":32768,\"max_output_context\":32764}",
            "features": "{\"json_format\":false,\"parallel_tool_calls\":false,\"stream\":true,\"stream_function_call\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.35,
                    "output": 0.7,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.35",
                    "输出token单价（分/千token）": "0.7"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "ali-qwen2-72b-base",
            "documentUrl": "",
            "properties": "{\"max_input_context\":32768,\"max_output_context\":32768}",
            "features": "{\"stream\":true,\"function_call\":false,\"stream_function_call\":false,\"parallel_tool_calls\":false,\"vision\":false,\"json_format\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.4,
                    "output": 1.2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.4",
                    "输出token单价（分/千token）": "1.2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "ali-qwen15-chathome",
            "documentUrl": "",
            "properties": "{\"max_input_context\":32768,\"max_output_context\":32759}",
            "features": "{\"stream\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.4,
                    "output": 1.2,
                    "unit": "分/千token",
                    "batchDiscount": 0.5
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.4",
                    "输出token单价（分/千token）": "1.2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "ali-qwen15-14b-base",
            "documentUrl": "",
            "properties": "{\"max_input_context\":32768,\"max_output_context\":32768}",
            "features": "{\"stream\":true,\"function_call\":false,\"stream_function_call\":false,\"parallel_tool_calls\":false,\"vision\":false,\"json_format\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.2,
                    "output": 0.6,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.2",
                    "输出token单价（分/千token）": "0.6"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "ali-qwen-25-7b-base",
            "documentUrl": "",
            "properties": "{\"max_input_context\":8192,\"max_output_context\":8192}",
            "features": "{\"stream\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.4,
                    "output": 1.2,
                    "unit": "分/千token",
                    "batchDiscount": 0.5
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.4",
                    "输出token单价（分/千token）": "1.2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "Qwen3-Max",
            "documentUrl": "https://help.aliyun.com/zh/model-studio/models",
            "properties": "{\"max_input_context\":258048,\"max_output_context\":32768}",
            "features": "{\"vision\":false,\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":true,\"reason_content\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 1.5,
                    "output": 6,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "1.5",
                    "输出token单价（分/千token）": "6"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "Qwen-3b-quality-assessment",
            "documentUrl": "",
            "properties": "{\"max_input_context\":32768,\"max_output_context\":32768}",
            "features": "{\"stream\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.03,
                    "output": 0.09,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.03",
                    "输出token单价（分/千token）": "0.09"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "Qwen-3b-IE",
            "documentUrl": "",
            "properties": "{\"max_input_context\":32768,\"max_output_context\":32768}",
            "features": "{\"stream\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.03,
                    "output": 0.09,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.03",
                    "输出token单价（分/千token）": "0.09"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "MiniMax-M2",
            "documentUrl": "https://platform.minimaxi.com/docs/api-reference/text-openai-api",
            "properties": "{\"max_input_context\":204800,\"max_output_context\":128000}",
            "features": "{\"support_top_P\":true,\"json_format\":true,\"stream_function_call\":true,\"function_call\":true,\"support_max_tokens\":true,\"support_temperature\":true,\"stream\":true,\"json_schema\":true,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.21,
                    "output": 0.84,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.21",
                    "输出token单价（分/千token）": "0.84"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "MiniMax-M1",
            "documentUrl": "https://platform.minimaxi.com/document/1?key=683ef4e94c5738213294ef51",
            "properties": "{\"max_input_context\":128000,\"max_output_context\":870000}",
            "features": "{\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"stream\":true,\"reason_content\":true,\"function_call\":true,\"json_schema\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.24,
                    "output": 2.4,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.24",
                    "输出token单价（分/千token）": "2.4"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "GLM-4.6",
            "documentUrl": "https://docs.bigmodel.cn/cn/guide/models/text/glm-4.6",
            "properties": "{\"max_input_context\":200000,\"max_output_context\":128000}",
            "features": "{\"json_format\":true,\"stream\":true,\"function_call\":true,\"prompt_cache\":true,\"reason_content\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.3,
                    "output": 1.4,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.3",
                    "输出token单价（分/千token）": "1.4"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "Doubao-1.5-vision-pro-32k",
            "documentUrl": "https://platform.moonshot.cn/docs",
            "properties": "{\"max_input_context\":32000,\"max_output_context\":12284}",
            "features": "{\"vision\":true,\"json_format\":true,\"stream_function_call\":false,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":false,\"json_schema\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.3,
                    "output": 0.9,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.3",
                    "输出token单价（分/千token）": "0.9"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "Doubao-1.5-pro-32k",
            "documentUrl": "https://platform.moonshot.cn/docs",
            "properties": "{\"max_input_context\":32000,\"max_output_context\":12285}",
            "features": "{\"json_format\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"stream\":true,\"function_call\":true,\"json_schema\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.08,
                    "output": 0.2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.08",
                    "输出token单价（分/千token）": "0.2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "Doubao-1.5-pro-256k",
            "documentUrl": "https://platform.moonshot.cn/docs",
            "properties": "{\"max_input_context\":256000,\"max_output_context\":12288}",
            "features": "{\"json_format\":true,\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.5,
                    "output": 0.9,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.5",
                    "输出token单价（分/千token）": "0.9"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "Doubao-1.5-lite-32k",
            "documentUrl": "https://www.volcengine.com/docs/82379/1330310",
            "properties": "{\"max_input_context\":32768,\"max_output_context\":12288}",
            "features": "{\"stream\":true,\"function_call\":true,\"stream_function_call\":true,\"parallel_tool_calls\":false,\"vision\":false,\"json_format\":false}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.03,
                    "output": 0.06,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.03",
                    "输出token单价（分/千token）": "0.06"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "A.M.-liqiu-7B-extract-C",
            "documentUrl": "",
            "properties": "{\"max_input_context\":131072,\"max_output_context\":131072}",
            "features": "{\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.05,
                    "output": 0.1,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.05",
                    "输出token单价（分/千token）": "0.1"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "A.M.-liqiu-72B",
            "documentUrl": "",
            "properties": "{\"max_input_context\":32768,\"max_output_context\":32768}",
            "features": "{\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.4,
                    "output": 1.2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.4",
                    "输出token单价（分/千token）": "1.2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "A.M.-lidong-7B-planning-v4",
            "documentUrl": "",
            "properties": "{\"max_input_context\":131072,\"max_output_context\":131702}",
            "features": "{\"stream\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.05,
                    "output": 0.1,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.05",
                    "输出token单价（分/千token）": "0.1"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "A.M.-lidong-7B",
            "documentUrl": "",
            "properties": "{\"max_input_context\":131072,\"max_output_context\":131072}",
            "features": "{\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.05,
                    "output": 0.1,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.05",
                    "输出token单价（分/千token）": "0.1"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "A.M.-lidong-72B",
            "documentUrl": "",
            "properties": "{\"max_input_context\":32768,\"max_output_context\":32768}",
            "features": "{\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.4,
                    "output": 1.2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.4",
                    "输出token单价（分/千token）": "1.2"
                },
                "unit": "分/千token"
            }
        },
        {
            "modelName": "A.M.-daxue-72B",
            "documentUrl": "",
            "properties": "{\"max_input_context\":32768,\"max_output_context\":32768}",
            "features": "{\"stream\":true,\"function_call\":true}",
            "priceDetails": {
                "priceInfo": {
                    "input": 0.4,
                    "output": 1.2,
                    "unit": "分/千token",
                    "batchDiscount": 1.0
                },
                "displayPrice": {
                    "输入token单价（分/千token）": "0.4",
                    "输出token单价（分/千token）": "1.2"
                },
                "unit": "分/千token"
            }
        }
    ],
    features: [
        {
            "code": "overseas",
            "name": "国外"
        },
        {
            "code": "mainland",
            "name": "国内"
        },
        {
            "code": "inner",
            "name": "内部"
        },
        {
            "code": "protected",
            "name": "内部已备案"
        },
        {
            "code": "long_input_context",
            "name": "超长上下文"
        },
        {
            "code": "long_output_context",
            "name": "超长输出"
        },
        {
            "code": "reason_content",
            "name": "深度思索"
        },
        {
            "code": "stream",
            "name": "流式"
        },
        {
            "code": "function_call",
            "name": "工具调用"
        },
        {
            "code": "stream_function_call",
            "name": "流式工具调用"
        },
        {
            "code": "vision",
            "name": "视觉"
        },
        {
            "code": "json_format",
            "name": "json格式"
        },
        {
            "code": "prompt_cache",
            "name": "提示词缓存"
        }
    ]
  }

  // 使用 useMemo 缓存扁平化后的数据
  const flattenedEndpoints = useMemo(() => {
    return flattenCategoryTrees(categoryTrees)
  }, [categoryTrees])

  // 获取 endpoint 详情数据
  async function fetchEndpointDetails(endpoint: string) {
    if (!endpoint) return

    try {
      setLoading(true)
      const data1 = await getEndpointDetails(endpoint, "", [])
      const data = DATA_MOCK 

      // 检查并处理数据
      if (data && data.models) {
        // 对每个模型进行处理
        data.models = data.models.map(model => {
          const processedModel = { ...model }

          // 如果存在 features 字段且为字符串，进行 JSON.parse
          if (processedModel.features && typeof processedModel.features === 'string') {
            try {
              const parsedFeatures = JSON.parse(processedModel.features)
              // 提取值为 true 的键组成字符串数组
              processedModel.features = Object.keys(parsedFeatures).filter(
                key => parsedFeatures[key] === true
              ).join(',');
            } catch (e) {
              console.error('Failed to parse features:', e)
            }
          }

          // 如果存在 properties 字段且为字符串，进行 JSON.parse
          if (processedModel.properties && typeof processedModel.properties === 'string') {
            try {
              const parsedProps = JSON.parse(processedModel.properties)
              // 对 max_input_context 和 max_output_context 应用千分位分隔
              if (parsedProps.max_input_context !== undefined && typeof parsedProps.max_input_context === "number") {
                parsedProps.max_input_context = parsedProps.max_input_context.toLocaleString()
              }
              if (parsedProps.max_output_context !== undefined && typeof parsedProps.max_output_context === "number") {
                parsedProps.max_output_context = parsedProps.max_output_context.toLocaleString()
              }
              processedModel.properties = parsedProps
            } catch (e) {
              console.error('Failed to parse properties:', e)
            }
          }

          return processedModel
        })
      }

      console.log('data1', data)
      setEndpointData(data)
    } catch (error) {
      console.error('Error fetching endpoint details:', error)
      setEndpointData(null)
    } finally {
      setLoading(false)
    }
  }

  // 初始化选中的 endpoint
  useEffect(() => {
    const endpoint = getInitialEndpoint(searchParams.get("endpoint"))
    setSelectedCapability(endpoint)
  }, [searchParams])

  // 当 selectedCapability 变化时，获取数据
  useEffect(() => {
    if (selectedCapability) {
      fetchEndpointDetails(selectedCapability)
    }
  }, [selectedCapability])

  const toggleTag = (tag: string) => {
    setSelectedTags((prev) => (prev.includes(tag) ? prev.filter((t) => t !== tag) : [...prev, tag]))
  }

  const filteredModels = allModels.filter((model) => {
    const matchesCapability = model.type === selectedCapability

    const matchesSearch =
      model.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      model.description.toLowerCase().includes(searchQuery.toLowerCase())

    const matchesTags = selectedTags.length === 0 || selectedTags.some((tag) => model.tags.includes(tag))

    return matchesCapability && matchesSearch && matchesTags
  })
  

  return (
    <>
      <TopBar title={t("modelCatalog")} description={t("modelCatalogDesc")} />

      <div className="flex h-[calc(100vh-4rem)] flex-col overflow-hidden">
        <div className="flex-1 overflow-y-auto">
          <div className="container px-6 py-8">
            <div className="mb-6">
              <div className="mb-3 flex items-center gap-2">
                <Layers className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm font-medium text-muted-foreground">{t("capabilityCategory")}</span>
              </div>
              <div className="flex gap-2 overflow-x-auto pb-2">
                {flattenedEndpoints.map((capability: EndpointWithCategory) => {
                  const isSelected = selectedCapability === capability.endpoint
                  return (
                    <Button
                      key={capability.endpoint}
                      variant={isSelected ? "default" : "outline"}
                      size="sm"
                      onClick={() => setSelectedCapability(capability.endpoint)}
                      className={`flex items-center gap-2 whitespace-nowrap ${isSelected ? "bg-primary text-primary-foreground" : ""
                        }`}
                    >
                      {capability.endpointName}
                    </Button>
                  )
                })}
              </div>
            </div>

            {/* Search */}
            <div className="mb-6">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  placeholder={t("searchModels")}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-10 text-sm"
                />
              </div>
            </div>

            {/* Tags */}
            <div className="mb-8">
              <div className="mb-3 flex items-center gap-2">
                <Layers className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm font-medium text-muted-foreground">{t("quickFilter")}</span>
              </div>
              {loading ? (
                <div className="flex items-center gap-2 text-muted-foreground">
                  <Loader className="h-4 w-4 animate-spin" />
                  <span className="text-sm">加载中...</span>
                </div>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {endpointData?.features.map((feature) => (
                    <Badge
                      key={feature.code}
                      variant={selectedTags.includes(feature.code) ? "default" : "outline"}
                      className={`cursor-pointer transition-colors`}
                      onClick={() => toggleTag(feature.code)}
                    >
                      {feature.name}
                    </Badge>
                  ))}
                  {(!endpointData?.features || endpointData.features.length === 0) && (
                    <span className="text-sm text-muted-foreground">暂无可用筛选标签</span>
                  )}
                </div>
              )}
            </div>

            {/* Model Grid */}
            <div className="mb-4">
              <h2 className="text-sm font-medium text-muted-foreground">
                {t("foundModels")} {endpointData?.models.length} {t("modelsCount")}
              </h2>
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
              {endpointData?.models.map((model) => {
                return (
                  <Card
                    key={model.modelName}
                    className="border-border/50 transition-all hover:border-primary/50 hover:shadow-md"
                  >
                    <CardContent className="p-5 h-full flex flex-col justify-between">
                      <div className="mb-4 flex items-start justify-between">
                        <div className="flex items-center gap-3">
                          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
                            <MessageSquare className="h-5 w-5 text-primary" />
                          </div>
                          <div>
                            <h3 className="font-semibold">{model.modelName}</h3>
                            {/* <p className="text-xs text-muted-foreground">{model.provider}</p> */}
                          </div>
                        </div>
                        <Button
                          size="sm"
                          variant="ghost"
                          className="h-8 w-8 p-0 hover:bg-primary/10"
                          title="添加私有渠道"
                        >
                          <Plus className="h-4 w-4" />
                        </Button>
                      </div>

                      {/* <p className="mb-3 text-sm text-muted-foreground">{model.description}</p> */}

                      {/* Tags */}
                      {/* <div className="mb-3 flex flex-wrap gap-1">
                        {model.tags.slice(0, 3).map((tag) => (
                          <Badge key={tag} variant="outline" className={`text-xs ${tagColors[tag]}`}>
                            {tag}
                          </Badge>
                        ))}
                      </div> */}

                      <div className="mb-3 min-h-[40px] space-y-1 text-xs">
                        {model.properties && (
                          <div className="flex justify-between">
                            <span className="text-muted-foreground">输入/输出长度:</span>
                            <span className="font-medium">
                            {model.properties?.max_input_context} / {model.properties?.max_output_context}
                            </span>
                          </div>
                        )}
                        <div className="flex justify-between">
                          <span className="text-muted-foreground">输入/输出定价（{model.priceDetails.unit}）:</span>
                          <span className="font-medium">
                            ¥{model.priceDetails.priceInfo?.input} / ¥{model.priceDetails.priceInfo?.output} 
                          </span>
                        </div>
                        {
                          model.priceDetails.priceInfo?.cachedRead && (
                            <div className="flex justify-between">
                              <span className="text-muted-foreground">命中缓存定价（{model.priceDetails.unit}）:</span>
                              <span className="font-medium">
                                ¥{model.priceDetails.priceInfo?.cachedRead} 
                              </span>
                            </div>
                          )
                        }
                      </div>

                      {/* Features */}
                      <div className="mb-4 flex flex-wrap gap-1">
                        {typeof model.features === 'string' ? model.features.split(',').map((feature: string,index) => (
                          <Badge key={feature} variant="secondary" className={`text-xs ${tagColors[index]}`}>
                            {feature}
                          </Badge>
                        )):null}
                      </div>

                      {/* Actions */}
                      <div className="flex gap-2">
                        <Button size="sm" className="flex-1" asChild>
                          <Link href={`/playground/chat?model=${model.id}`}>试用</Link>
                        </Button>
                        <Button size="sm" variant="outline" asChild>
                          <Link href={`/docs/api/${model.id}`}>
                            <ExternalLink className="h-4 w-4" />
                          </Link>
                        </Button>
                      </div>
                    </CardContent>
                  </Card>
                )
              })}
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
