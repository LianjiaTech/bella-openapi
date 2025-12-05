"use client"

import type React from "react"
import { createContext, useContext, useEffect, useState } from "react"

type Language = "zh-CN" | "en-US"

interface LanguageContextType {
  language: Language
  setLanguage: (language: Language) => void
  t: (key: string) => string
}

const LanguageContext = createContext<LanguageContextType | undefined>(undefined)

const translations = {
  "zh-CN": {
    // Navigation
    home: "主页",
    models: "模型目录",
    playground: "Playground",
    apiKeys: "API Keys",
    logs: "日志查询",
    modelStatus: "模型状态",
    metadata: "元数据管理",
    docs: "文档",
    settings: "设置",
    login: "退出", // 添加登录翻译
    help: "帮助",

    // Playground items
    chat: "Chat",
    embedding: "Embedding",
    audio: "Audio",
    tts: "TTS",
    realtime: "Realtime",
    images: "Images",
    knowledge: "Knowledge",
    rag: "RAG",
    docparse: "DocParse",
    workflow: "Workflow",
    search: "Search",
    ocr: "OCR",

    // Settings
    language: "语言",
    theme: "主题",
    light: "浅色",
    dark: "深色",
    chinese: "简体中文",
    english: "English",

    // Home page
    aiDeveloperPlatform: "AI 开发者平台",
    fastestPathToProduction: "从构思到生产的最快路径",
    homeDescription: "企业级大模型 OpenAPI 平台，提供 ChatCompletion、图像生成、语音识别等多种 AI 能力",
    startBuilding: "开始构建",
    viewDocs: "查看文档",
    browseModels: "浏览模型目录",
    browseModelsDesc: "探索 AI 能力，找到适合你的模型",
    testInPlayground: "在 Playground 中测试",
    testInPlaygroundDesc: "在线体验各种 AI 能力",
    monitorUsage: "监控使用情况",
    monitorUsageDesc: "查看 API 调用日志和统计",
    whatsNew: "最新动态",
    gpt5ProTitle: "GPT-5 Pro 现已推出",
    gpt5ProDesc: "最先进的推理、分析和代码生成能力",
    sora2Title: "Sora-2 视频生成",
    sora2Desc: "顶尖的文本到视频生成模型，支持音效",
    realtimeApiTitle: "实时语音 API",
    realtimeApiDesc: "低延迟双向语音通信，延迟 <100ms",
    functionCallingTitle: "函数调用增强",
    functionCallingDesc: "支持并行函数调用和结构化输出",
    newFeature: "新功能",
    beta: "Beta",
    quickStart: "快速开始",
    viewAPIKeys: "查看 API Keys",
    browseDocs: "浏览文档",

    // Models page
    modelCatalog: "模型目录",
    modelCatalogDesc: "浏览并选择适合您需求的AI模型",
    capabilityCategory: "能力分类",
    searchModels: "搜索模型名称、描述...",
    quickFilter: "快速筛选",
    foundModels: "找到",
    modelsCount: "个模型",
    addPrivateChannel: "添加私有渠道",
    tryNow: "试用",
    domestic: "国内",
    international: "国外",
    internal: "内部",
    longContext: "超长上下文",
    longOutput: "超长输出",
    deepThinking: "深度思考",
    streaming: "流式",
    privateChannel: "私有渠道",
    inputOutputLength: "输入/输出长度",
    pricing: "定价",

    // Metadata page
    metadataManagement: "元数据管理",
    metadataManagementDesc: "管理和配置数据库、表、集合等元数据信息",
    metadataType: "元数据类型",
    searchMetadata: "搜索元数据名称、描述...",
    foundMetadata: "找到",
    itemsCount: "项",

    // Common
    star: "Star",
    toggleTheme: "切换主题",
  },
  "en-US": {
    // Navigation
    home: "Home",
    models: "Models",
    playground: "Playground",
    apiKeys: "API Keys",
    logs: "Logs",
    modelStatus: "Model Status",
    metadata: "Metadata",
    docs: "Docs",
    settings: "Settings",
    login: "Login", // 添加登录翻译
    help: "Help",

    // Playground items
    chat: "Chat",
    embedding: "Embedding",
    audio: "Audio",
    tts: "TTS",
    realtime: "Realtime",
    images: "Images",
    knowledge: "Knowledge",
    rag: "RAG",
    docparse: "DocParse",
    workflow: "Workflow",
    search: "Search",
    ocr: "OCR",

    // Settings
    language: "Language",
    theme: "Theme",
    light: "Light",
    dark: "Dark",
    chinese: "简体中文",
    english: "English",

    // Home page
    aiDeveloperPlatform: "AI Developer Platform",
    fastestPathToProduction: "The Fastest Path from Concept to Production",
    homeDescription:
      "Enterprise-grade AI OpenAPI platform providing ChatCompletion, image generation, speech recognition, and more",
    startBuilding: "Start Building",
    viewDocs: "View Docs",
    browseModels: "Browse Models",
    browseModelsDesc: "Explore AI capabilities and find the right model for you",
    testInPlayground: "Test in Playground",
    testInPlaygroundDesc: "Experience various AI capabilities online",
    monitorUsage: "Monitor Usage",
    monitorUsageDesc: "View API call logs and statistics",
    whatsNew: "What's New",
    gpt5ProTitle: "GPT-5 Pro Now Available",
    gpt5ProDesc: "State-of-the-art reasoning, analysis, and code generation",
    sora2Title: "Sora-2 Video Generation",
    sora2Desc: "Top-tier text-to-video model with sound effects",
    realtimeApiTitle: "Realtime Voice API",
    realtimeApiDesc: "Low-latency bidirectional voice communication <100ms",
    functionCallingTitle: "Enhanced Function Calling",
    functionCallingDesc: "Support for parallel function calls and structured outputs",
    newFeature: "New",
    beta: "Beta",
    quickStart: "Quick Start",
    viewAPIKeys: "View API Keys",
    browseDocs: "Browse Docs",

    // Models page
    modelCatalog: "Model Catalog",
    modelCatalogDesc: "Browse and select AI models for your needs",
    capabilityCategory: "Capability",
    searchModels: "Search model name, description...",
    quickFilter: "Quick Filter",
    foundModels: "Found",
    modelsCount: "models",
    addPrivateChannel: "Add Private Channel",
    tryNow: "Try Now",
    domestic: "Domestic",
    international: "International",
    internal: "Internal",
    longContext: "Long Context",
    longOutput: "Long Output",
    deepThinking: "Deep Thinking",
    streaming: "Streaming",
    privateChannel: "Private Channel",
    inputOutputLength: "Input/Output Length",
    pricing: "Pricing",

    // Metadata page
    metadataManagement: "Metadata Management",
    metadataManagementDesc: "Manage and configure database, table, collection metadata",
    metadataType: "Metadata Type",
    searchMetadata: "Search metadata name, description...",
    foundMetadata: "Found",
    itemsCount: "items",

    // Common
    star: "Star",
    toggleTheme: "Toggle Theme",
  },
}

export function LanguageProvider({ children }: { children: React.ReactNode }) {
  const [language, setLanguageState] = useState<Language>("zh-CN")

  useEffect(() => {
    const savedLanguage = localStorage.getItem("language") as Language
    if (savedLanguage && (savedLanguage === "zh-CN" || savedLanguage === "en-US")) {
      setLanguageState(savedLanguage)
    }
  }, [])

  const setLanguage = (newLanguage: Language) => {
    setLanguageState(newLanguage)
    if (typeof window !== "undefined") {
      localStorage.setItem("language", newLanguage)
      document.documentElement.lang = newLanguage
    }
  }

  const t = (key: string): string => {
    return translations[language][key as keyof (typeof translations)["zh-CN"]] || key
  }

  return <LanguageContext.Provider value={{ language, setLanguage, t }}>{children}</LanguageContext.Provider>
}

export function useLanguage() {
  const context = useContext(LanguageContext)
  if (context === undefined) {
    throw new Error("useLanguage must be used within a LanguageProvider")
  }
  return context
}
