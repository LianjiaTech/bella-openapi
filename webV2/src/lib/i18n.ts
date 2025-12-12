export const languages = {
    zh: "中文",
    en: "English",
  } as const
  
  export type Language = keyof typeof languages
  
  export const translations = {
    zh: {
      // Navigation
      home: "主页",
      models: "模型目录",
      playground: "Playground",
      apiKeys: "API Keys",
      logs: "日志查询",
      docs: "文档",
      settings: "设置",
      help: "帮助",
  
      // Playground items
      chat: "对话",
      embedding: "向量嵌入",
      audio: "语音识别",
      tts: "语音合成",
      realtime: "实时语音",
      images: "图像生成",
      knowledge: "知识库",
      rag: "RAG检索",
      docparse: "文档解析",
      workflow: "工作流",
      search: "搜索",
      ocr: "OCR识别",
  
      // Top bar
      searchPlaceholder: "搜索模型、文档...",
      toggleTheme: "切换主题",
      notifications: "通知",
  
      // User section
      loginRegister: "登录/注册",
      accessFullFeatures: "访问完整功能",
  
      // Common
      new: "新",
      beta: "测试",
      stable: "稳定",
  
      // Home page
      welcome: "欢迎使用 AI OpenAPI 平台",
      welcomeDesc: "体验强大的 AI 能力，从对话生成到图像创作，一站式 API 服务平台",
      getStarted: "开始使用",
      viewDocs: "查看文档",
      quickAccess: "快速入口",
      quickAccessDesc: "快速访问常用功能",
      chatWithModels: "与模型对话",
      chatDesc: "体验最新的对话模型",
      viewAPIKeys: "查看 API Keys",
      apiKeysDesc: "管理您的 API 密钥",
      monitorUsage: "监控使用情况",
      usageDesc: "查看 API 调用日志",
      whatsNew: "最新动态",
      tryNow: "立即试用",
      codeExample: "代码示例",
  
      // Models page
      findModels: "找到适合您的模型来生成自定义 AI 解决方案",
      announcements: "公告",
      checkOutModels: "查看模型",
      readBlog: "阅读博客",
      industry: "行业",
      capabilities: "能力",
      recommendations: "推荐",
      inference: "推理任务",
      license: "许可证",
      modelsCount: "模型",
      searchModels: "搜索",
      all: "全部",
      textGeneration: "文本生成",
      code: "代码",
      imageGen: "图像生成",
      speech: "语音",
      multimodal: "多模态",
      heavyDuty: "高性能",
      highSpeed: "高速",
  
      // API page
      introduction: "简介",
      authentication: "认证",
      apiReference: "API 参考",
      chatCompletion: "对话补全",
      imageGeneration: "图像生成",
      audioTranscription: "语音转录",
      quickStart: "快速开始",
      quickStartDesc: "通过几行代码快速接入我们的 API",
      step1: "步骤 1: 获取 API Key",
      step1Desc: "在控制台创建您的 API Key",
      step2: "步骤 2: 安装 SDK",
      step2Desc: "使用 npm 或 pip 安装我们的 SDK",
      step3: "步骤 3: 发起请求",
      step3Desc: "使用您的 API Key 调用接口",
      authDesc: "所有 API 请求都需要在 Authorization 头中包含您的 API Key",
      exampleRequest: "示例请求",
      response: "响应",
  
      // Language
      language: "语言",
      changeLanguage: "切换语言",
    },
    en: {
      // Navigation
      home: "Home",
      models: "Models",
      playground: "Playground",
      apiKeys: "API Keys",
      logs: "Logs",
      docs: "Docs",
      settings: "Settings",
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
  
      // Top bar
      searchPlaceholder: "Search models, docs...",
      toggleTheme: "Toggle theme",
      notifications: "Notifications",
  
      // User section
      loginRegister: "Login/Register",
      accessFullFeatures: "Access full features",
  
      // Common
      new: "New",
      beta: "Beta",
      stable: "Stable",
  
      // Home page
      welcome: "Welcome to AI OpenAPI Platform",
      welcomeDesc:
        "Experience powerful AI capabilities, from conversation generation to image creation, all-in-one API service platform",
      getStarted: "Get Started",
      viewDocs: "View Docs",
      quickAccess: "Quick Access",
      quickAccessDesc: "Quick access to common features",
      chatWithModels: "Chat with models",
      chatDesc: "Experience the latest conversation models",
      viewAPIKeys: "View API Keys",
      apiKeysDesc: "Manage your API keys",
      monitorUsage: "Monitor usage",
      usageDesc: "View API call logs",
      whatsNew: "What's new",
      tryNow: "Try now",
      codeExample: "Code Example",
  
      // Models page
      findModels: "Find the right model to generate custom AI solutions",
      announcements: "Announcements",
      checkOutModels: "Check out models",
      readBlog: "Read blog",
      industry: "Industry",
      capabilities: "Capabilities",
      recommendations: "Recommendations",
      inference: "Inference",
      license: "License",
      modelsCount: "models",
      searchModels: "Search",
      all: "All",
      textGeneration: "Text Generation",
      code: "Code",
      imageGen: "Image Generation",
      speech: "Speech",
      multimodal: "Multimodal",
      heavyDuty: "Heavy Duty",
      highSpeed: "High Speed",
  
      // API page
      introduction: "Introduction",
      authentication: "Authentication",
      apiReference: "API Reference",
      chatCompletion: "Chat Completion",
      imageGeneration: "Image Generation",
      audioTranscription: "Audio Transcription",
      quickStart: "Quick Start",
      quickStartDesc: "Get started with our API in just a few lines of code",
      step1: "Step 1: Get API Key",
      step1Desc: "Create your API Key in the console",
      step2: "Step 2: Install SDK",
      step2Desc: "Install our SDK using npm or pip",
      step3: "Step 3: Make a request",
      step3Desc: "Call the API with your API Key",
      authDesc: "All API requests must include your API Key in the Authorization header",
      exampleRequest: "Example Request",
      response: "Response",
  
      // Language
      language: "Language",
      changeLanguage: "Change Language",
    },
  } as const
  
  export type TranslationKey = keyof typeof translations.zh
  