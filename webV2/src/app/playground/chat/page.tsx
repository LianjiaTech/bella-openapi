"use client"

import { useState, useEffect } from "react"
import { TopBar } from "@/components/top-bar"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Slider } from "@/components/ui/slider"
import { Switch } from "@/components/ui/switch"
import {
  MessageSquare,
  Send,
  Bot,
  User,
  Copy,
  Check,
  Brain,
  ImageIcon,
  ChevronDown,
  ChevronUp,
  Volume2,
  Play,
} from "lucide-react"
import { Separator } from "@/components/ui/separator"
import { usePlaygroundData } from "@/hooks/use-playground-data"
import { Model } from "@/lib/types/openapi"

interface MessageContent {
  type: "text" | "code" | "thinking" | "image" | "audio"
  content: string
  language?: string
  caption?: string
}

interface Message {
  role: "user" | "assistant"
  contents: MessageContent[]
}

export default function ChatPlaygroundPage() {
  const [messages, setMessages] = useState<Message[]>([
    {
      role: "assistant",
      contents: [{ type: "text", content: "你好！我是AI助手，有什么可以帮助你的吗？" }],
    },
    {
      role: "user",
      contents: [{ type: "text", content: "请帮我写一个 TypeScript 的用户数据获取函数，并解释一下代码架构。" }],
    },
    {
      role: "assistant",
      contents: [
        {
          type: "thinking",
          content:
            "让我分析一下这个问题...\n\n首先，我需要理解用户的意图。用户想要一个 TypeScript 函数来获取用户数据，这意味着需要定义类型接口、处理异步请求和错误处理。\n\n其次，我应该提供清晰的代码示例来说明最佳实践，包括类型安全和现代的 async/await 语法。\n\n最后，我会配合架构图来展示整体的数据流程，让用户更好地理解系统设计。",
        },
        {
          type: "text",
          content: "好的！我来为你编写一个 TypeScript 的用户数据获取函数。这是一个包含类型定义和错误处理的完整示例：",
        },
        {
          type: "code",
          language: "typescript",
          content: `// 定义用户接口
interface User {
  id: string;
  name: string;
  email: string;
  avatar?: string;
  createdAt: Date;
}

// 定义 API 响应类型
interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
}

// 用户数据获取函数
async function fetchUser(id: string): Promise<User> {
  try {
    const response = await fetch(\`/api/users/\${id}\`);
    
    if (!response.ok) {
      throw new Error(\`HTTP error! status: \${response.status}\`);
    }
    
    const result: ApiResponse<User> = await response.json();
    
    if (!result.success || !result.data) {
      throw new Error(result.error || 'Failed to fetch user');
    }
    
    return result.data;
  } catch (error) {
    console.error('Error fetching user:', error);
    throw error;
  }
}

// 使用示例
async function example() {
  try {
    const user = await fetchUser("123");
    console.log(\`用户名称: \${user.name}\`);
    console.log(\`电子邮件: \${user.email}\`);
  } catch (error) {
    console.error('获取用户失败:', error);
  }
}`,
        },
        {
          type: "text",
          content:
            "这段代码展示了如何使用 TypeScript 的类型系统来确保类型安全，并使用现代的 async/await 语法处理异步操作。下面是系统的架构示意图：",
        },
        {
          type: "image",
          content: "/code-architecture-diagram.jpg",
          caption: "用户数据获取流程 - 从前端请求到后端响应的完整架构",
        },
        {
          type: "text",
          content:
            "这个架构展示了数据如何从客户端流向服务器，以及如何处理响应。关键点包括：\n\n1. **类型安全**：使用 TypeScript 接口定义数据结构\n2. **错误处理**：完善的 try-catch 机制\n3. **响应验证**：检查 HTTP 状态和业务逻辑状态\n4. **可维护性**：清晰的代码结构便于后续扩展\n\n希望这些示例对你有帮助！如果有任何问题，随时问我。",
        },
      ],
    },
  ])
  const [input, setInput] = useState("")
  const [modelList, setModelList] = useState<Model[]>([])
  const [model, setModel] = useState("")
  const [temperature, setTemperature] = useState([0.7])
  const [maxTokens, setMaxTokens] = useState([2048])
  const [thinkingMode, setThinkingMode] = useState(true)
  const [streamMode, setStreamMode] = useState(true)
  const [copiedIndex, setCopiedIndex] = useState<string | null>(null)
  const [thinkingExpanded, setThinkingExpanded] = useState<Record<string, boolean>>({ "2-0": true })

  const { endpointDetails, loading, error, refetch, currentEndpoint } = usePlaygroundData()
  const models = endpointDetails?.models || []
  
  // 监听 endpointDetails.models，当有值时设置第一项为默认值
  useEffect(() => {
    if (endpointDetails?.models && endpointDetails.models.length > 0) {
      const modelList = endpointDetails?.models || []
      setModelList(modelList)
      setModel(modelList[0].modelName || '')
    }
  }, [endpointDetails?.models])

  const handleSend = () => {
    if (!input.trim()) return

    setMessages([...messages, { role: "user", contents: [{ type: "text", content: input }] }])
    setInput("")

    setTimeout(() => {
      const demoContents: MessageContent[] = []

      if (thinkingMode) {
        demoContents.push({
          type: "thinking",
          content:
            "让我分析一下这个问题...\n\n首先，我需要理解用户的意图。\n\n其次，我应该提供清晰的代码示例来说明。\n\n最后，我会总结关键要点。",
        })
      }

      demoContents.push({
        type: "text",
        content: "这是一个带有多种元素的示例响应。",
      })

      setMessages((prev) => [...prev, { role: "assistant", contents: demoContents }])
    }, 1000)
  }

  const copyToClipboard = (text: string, index: string) => {
    navigator.clipboard.writeText(text)
    setCopiedIndex(index)
    setTimeout(() => setCopiedIndex(null), 2000)
  }

  const renderContent = (content: MessageContent, messageIndex: number, contentIndex: number) => {
    const key = `${messageIndex}-${contentIndex}`
    const isExpanded = thinkingExpanded[key] !== false

    switch (content.type) {
      case "thinking":
        return (
          <div className="my-3 rounded-lg border border-primary/20 bg-primary/5 dark:border-primary/30 dark:bg-primary/10 overflow-hidden">
            <button
              className="w-full flex items-center justify-between p-4 hover:bg-primary/10 dark:hover:bg-primary/20 transition-colors"
              onClick={() => setThinkingExpanded((prev) => ({ ...prev, [key]: !isExpanded }))}
            >
              <div className="flex items-center gap-2 text-sm font-medium text-primary dark:text-primary">
                <Brain className="h-4 w-4" />
                思考过程
              </div>
              {isExpanded ? (
                <ChevronUp className="h-4 w-4 text-primary dark:text-primary" />
              ) : (
                <ChevronDown className="h-4 w-4 text-primary dark:text-primary" />
              )}
            </button>
            {isExpanded && (
              <div className="px-4 pb-4 whitespace-pre-wrap text-sm text-primary/80 dark:text-primary/70 leading-relaxed">
                {content.content}
              </div>
            )}
          </div>
        )

      case "code":
        return (
          <div className="my-3 rounded-lg border bg-muted/50 overflow-hidden">
            <div className="flex items-center justify-between border-b bg-muted px-4 py-2">
              <span className="text-xs font-medium text-muted-foreground">{content.language || "code"}</span>
              <Button
                size="sm"
                variant="ghost"
                className="h-7 gap-1.5 px-2"
                onClick={() => copyToClipboard(content.content, key)}
              >
                {copiedIndex === key ? (
                  <>
                    <Check className="h-3 w-3" />
                    <span className="text-xs">已复制</span>
                  </>
                ) : (
                  <>
                    <Copy className="h-3 w-3" />
                    <span className="text-xs">复制</span>
                  </>
                )}
              </Button>
            </div>
            <pre className="overflow-x-auto p-4">
              <code className="text-xs leading-relaxed">{content.content}</code>
            </pre>
          </div>
        )

      case "image":
        return (
          <div className="my-3">
            <div className="rounded-lg border bg-muted/30 overflow-hidden">
              <img
                src={content.content || "/placeholder.svg"}
                alt={content.caption || "Generated image"}
                className="w-full h-auto"
              />
              {content.caption && (
                <div className="flex items-center gap-2 border-t bg-muted/50 px-4 py-2">
                  <ImageIcon className="h-3.5 w-3.5 text-muted-foreground" />
                  <span className="text-xs text-muted-foreground">{content.caption}</span>
                </div>
              )}
            </div>
          </div>
        )

      case "audio":
        return (
          <div className="my-3">
            <div className="rounded-lg border bg-muted/30 overflow-hidden">
              <div className="flex items-center gap-3 p-4">
                <Volume2 className="h-10 w-10 text-primary" />
                <div className="flex-1">
                  <p className="text-sm font-medium">生成的音频内容</p>
                  <p className="text-xs text-muted-foreground">{content.caption || "点击播放音频"}</p>
                </div>
                <Button size="sm" variant="outline">
                  <Play className="h-4 w-4 mr-2" />
                  播放
                </Button>
              </div>
            </div>
          </div>
        )

      case "text":
      default:
        return <p className="text-sm leading-relaxed whitespace-pre-wrap">{content.content}</p>
    }
  }
  return (
    <>
      <TopBar />

      <div className="flex h-[calc(100vh-4rem)]">
        <div className="flex flex-1 flex-col">
          <div className="flex-1 overflow-y-auto p-6">
            <div className="mx-auto max-w-3xl space-y-6">
              {messages.map((message, messageIndex) => (
                <div key={messageIndex} className="flex gap-3">
                  <div
                    className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${
                      message.role === "assistant" ? "bg-primary/10" : "bg-muted"
                    }`}
                  >
                    {message.role === "assistant" ? (
                      <Bot className="h-4 w-4 text-primary" />
                    ) : (
                      <User className="h-4 w-4" />
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="mb-2 text-sm font-medium text-muted-foreground">
                      {message.role === "assistant" ? "助手" : "你"}
                    </p>
                    <div className="space-y-2">
                      {message.contents.map((content, contentIndex) => (
                        <div key={contentIndex}>{renderContent(content, messageIndex, contentIndex)}</div>
                      ))}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="border-t bg-background p-4">
            <div className="mx-auto max-w-3xl">
              <div className="relative">
                <Textarea
                  placeholder="输入你的消息... (Shift+Enter 换行)"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && !e.shiftKey) {
                      e.preventDefault()
                      handleSend()
                    }
                  }}
                  className="min-h-[80px] resize-none pr-12"
                />
                <Button size="icon" className="absolute bottom-2 right-2" onClick={handleSend}>
                  <Send className="h-4 w-4" />
                </Button>
              </div>
            </div>
          </div>
        </div>

        <div className="w-80 border-l bg-muted/30 p-6 overflow-y-auto">
          <div className="space-y-6">
            <div>
              <Label className="mb-3 flex items-center gap-2 text-base font-semibold">
                <MessageSquare className="h-4 w-4" />
                模型选择
              </Label>
              <Select value={model} onValueChange={setModel}>
                <SelectTrigger className="w-full focus:ring-0 focus:ring-offset-0 focus-visible:ring-0 focus-visible:ring-offset-0">
                  <SelectValue placeholder="选择模型" />
                </SelectTrigger>
                <SelectContent className="max-h-[60vh]">
                  {modelList.map((modelItem) => (
                    <SelectItem key={modelItem.modelName} value={modelItem.modelName}>
                      {modelItem.modelName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>

              {modelList.length > 0 && (
                <div className="mt-3 rounded-lg border bg-muted/50 p-3 space-y-2">
                  <p className="text-xs text-muted-foreground leading-relaxed">
                    当前已选择: {model || "未选择"}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    共 {modelList.length} 个可用模型
                  </p>
                </div>
              )}
            </div>

            <Separator />

            <div>
              <Label className="mb-2 flex items-center justify-between">
                <span>温度 (Temperature)</span>
                <span className="font-mono text-sm">{temperature[0]}</span>
              </Label>
              <Slider value={temperature} onValueChange={setTemperature} min={0} max={2} step={0.1} className="mb-2" />
              <p className="text-xs text-muted-foreground">控制输出的随机性，值越高越随机</p>
            </div>

            <div className="flex items-center justify-between">
              <div>
                <Label>思考模式</Label>
                <p className="text-xs text-muted-foreground">启用推理过程可视化</p>
              </div>
              <Switch checked={thinkingMode} onCheckedChange={setThinkingMode} />
            </div>

            <div>
              <Label className="mb-2 flex items-center justify-between">
                <span>最大长度 (Max Tokens)</span>
                <span className="font-mono text-sm">{maxTokens[0]}</span>
              </Label>
              <Slider value={maxTokens} onValueChange={setMaxTokens} min={256} max={4096} step={256} className="mb-2" />
              <p className="text-xs text-muted-foreground">控制生成的最大长度</p>
            </div>

            <div className="flex items-center justify-between">
              <div>
                <Label>流式输出</Label>
                <p className="text-xs text-muted-foreground">实时显示生成结果</p>
              </div>
              <Switch checked={streamMode} onCheckedChange={setStreamMode} />
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
