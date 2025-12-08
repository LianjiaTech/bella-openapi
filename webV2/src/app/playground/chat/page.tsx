"use client"

import { useState, useEffect, useRef } from "react"
import { TopBar } from "@/components/top-bar"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Slider } from "@/components/ui/slider"
import { Switch } from "@/components/ui/switch"
import { MessageSquare, Send, StopCircle } from "lucide-react"
import { Separator } from "@/components/ui/separator"
import { usePlaygroundData } from "@/hooks/use-playground-data"
import { Model } from "@/lib/types/openapi"
import { ChatCompletionsProcessor } from "@/lib/sse/ChatCompletionsProcessor"
import { ChatCompletionsEventType } from "@/lib/sse/types"
import { useSearchParams } from "next/navigation"
import { useUser } from "@/lib/context/user-context"
import { Message } from "./types"
import { MessageList } from "./components/MessageList"

export default function ChatPlaygroundPage() {
  const searchParams = useSearchParams()
  const { userInfo } = useUser()
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState("")
  const [modelList, setModelList] = useState<Model[]>([])
  const [model, setModel] = useState("")
  const [temperature, setTemperature] = useState([0.7])
  const [maxTokens, setMaxTokens] = useState([2048])
  const [thinkingMode, setThinkingMode] = useState(true)
  const [streamMode, setStreamMode] = useState(true)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string>("")

  const chatProcessorRef = useRef<ChatCompletionsProcessor | null>(null)

  const { endpointDetails, loading, error: endpointError, refetch, currentEndpoint } = usePlaygroundData()
  const models = endpointDetails?.models || []
  
  // 监听 endpointDetails.models，当有值时设置第一项为默认值
  useEffect(() => {
    if (endpointDetails?.models && endpointDetails.models.length > 0) {
      const modelList = endpointDetails?.models || []
      setModelList(modelList)
      setModel(modelList[0].modelName || '')
    }
  }, [endpointDetails?.models])

  // 初始化 ChatCompletionsProcessor
  useEffect(() => {
    const endpoint = searchParams.get("endpoint")
    if (!endpoint) {
      setError("缺少 endpoint 参数")
      return
    }

    // 清理旧的 processor 实例
    if (chatProcessorRef.current) {
      chatProcessorRef.current.cancel("创建新实例")
      chatProcessorRef.current.removeAllListeners()
    }

    // 创建 ChatCompletionsProcessor 实例
    const processor = new ChatCompletionsProcessor({
      url: endpoint,
      headers: {
        "Content-Type": "application/json",
      },
    })

    // 监听 START 事件
    processor.on(ChatCompletionsEventType.START, () => {
      console.log('START event triggered')
      // 开始接收响应，添加一个空的 assistant 消息
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          contents: [],
        },
      ])
    })

    // 监听 DELTA 事件
    processor.on(ChatCompletionsEventType.DELTA, (data) => {
      console.log('DELTA event triggered:', data)
      setMessages((prev) => {
        const newMessages = [...prev]
        const lastMessage = newMessages[newMessages.length - 1]

        if (lastMessage && lastMessage.role === "assistant") {
          // 处理 reasoning_content（思考过程）
          if (data.isReasoningContent && data.reasoning_content) {
            const reasoningContent = lastMessage.contents.find((c) => c.type === "reasoning_content")
            if (reasoningContent) {
              reasoningContent.content += data.reasoning_content
            } else {
              lastMessage.contents.push({
                type: "reasoning_content",
                content: data.reasoning_content,
              })
            }
          }
          // 处理普通 content
          else if (data.content) {
            const textContent = lastMessage.contents.find((c) => c.type === "text")
            if (textContent) {
              textContent.content += data.content
            } else {
              lastMessage.contents.push({
                type: "text",
                content: data.content,
              })
            }
          }
        }

        return newMessages
      })
    })

    // 监听 FINISH 事件
    processor.on(ChatCompletionsEventType.FINISH, () => {
      setIsLoading(false)
    })

    // 监听 ERROR 事件
    processor.on(ChatCompletionsEventType.ERROR, (errorMsg) => {
      setError(`请求错误: ${errorMsg}`)
      setIsLoading(false)
    })

    chatProcessorRef.current = processor

    // 清理函数：取消请求并移除监听器
    return () => {
      processor.cancel("组件卸载")
      processor.removeAllListeners()
    }
  }, [searchParams])

  const handleSend = async () => {
    if (!input.trim() || isLoading) return

    const processor = chatProcessorRef.current
    if (!processor) {
      setError("ChatCompletionsProcessor 未初始化")
      return
    }

    // 添加用户消息
    const userMessage: Message = {
      role: "user",
      contents: [{ type: "text", content: input }],
    }
    setMessages((prev) => [...prev, userMessage])

    // 清空输入框并设置加载状态
    setInput("")
    setIsLoading(true)
    setError("")

    try {
      // 构建请求消息列表
      const requestMessages = messages
        .concat(userMessage)
        .map((msg) => ({
          role: msg.role,
          content: msg.contents
            .filter((c) => c.type === "text")
            .map((c) => c.content)
            .join("\n"),
        }))

      // 构建请求参数
      const request = {
        model: model,
        messages: requestMessages,
        stream: streamMode,
        temperature: temperature[0],
        max_tokens: maxTokens[0],
        user: userInfo?.userId || 1000000030873314
      }
      console.log('request:', request)
      // 发送请求
      await processor.send(request)
    } catch (err) {
      setError(`发送请求失败: ${err instanceof Error ? err.message : String(err)}`)
      setIsLoading(false)
    }
  }

  const handleCancel = () => {
    const processor = chatProcessorRef.current
    if (processor) {
      processor.cancel("用户取消")
      setIsLoading(false)
    }
  }
  return (
    <>
      <TopBar />

      <div className="flex h-[calc(100vh-4rem)]">
        <div className="flex flex-1 flex-col">
          <div className="flex-1 overflow-y-auto p-6">
            <MessageList messages={messages} />
          </div>

          <div className="border-t bg-background p-4">
            <div className="mx-auto max-w-3xl">
              {error && (
                <div className="mb-3 rounded-lg bg-destructive/10 border border-destructive/20 p-3">
                  <p className="text-sm text-destructive">{error}</p>
                </div>
              )}
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
                  disabled={isLoading}
                />
                {isLoading ? (
                  <Button
                    size="icon"
                    variant="destructive"
                    className="absolute bottom-2 right-2"
                    onClick={handleCancel}
                  >
                    <StopCircle className="h-4 w-4" />
                  </Button>
                ) : (
                  <Button
                    size="icon"
                    className="absolute bottom-2 right-2"
                    onClick={handleSend}
                    disabled={!input.trim()}
                  >
                    <Send className="h-4 w-4" />
                  </Button>
                )}
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
                    <SelectItem key={modelItem.modelName} value={modelItem.modelName || ""}>
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
