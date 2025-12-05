"use client"

import { TopBar } from "@/components/top-bar"
import { Radio, Mic, Square, MessageSquare } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Slider } from "@/components/ui/slider"
import { ScrollArea } from "@/components/ui/scroll-area"
import { useState, useRef, useEffect } from "react"

// 定义时间轴事件类型
interface TimelineEvent {
  startTime: number // 开始时间（秒）
  duration: number // 持续时间（秒）
  type: string // 事件类型
  label?: string // 事件标签
}

export default function RealtimePlaygroundPage() {
  const [isActive, setIsActive] = useState(false)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const animationRef = useRef<number>()
  const startTimeRef = useRef<number>(0)
  const eventsRef = useRef<{
    audioInput: TimelineEvent[]
    vad: TimelineEvent[]
    speaker: TimelineEvent[]
    llm: TimelineEvent[]
    tts: TimelineEvent[]
  }>({
    audioInput: [],
    vad: [],
    speaker: [],
    llm: [],
    tts: [],
  })
  const [dialogHistory, setDialogHistory] = useState<Array<{ type: "user" | "ai"; text: string; timestamp: string }>>(
    [],
  )

  // 生成模拟事件
  const generateMockEvents = (currentTime: number) => {
    const events = eventsRef.current

    // 音频输入：连续的短片段
    if (
      events.audioInput.length === 0 ||
      events.audioInput[events.audioInput.length - 1].startTime < currentTime - 0.5
    ) {
      events.audioInput.push({
        startTime: currentTime,
        duration: 0.3 + Math.random() * 0.5,
        type: "audio",
      })
    }

    // VAD 事件：间歇性的检测
    if (
      Math.random() > 0.95 &&
      (events.vad.length === 0 || events.vad[events.vad.length - 1].startTime < currentTime - 2)
    ) {
      events.vad.push({
        startTime: currentTime,
        duration: 1 + Math.random() * 2,
        type: "vad",
        label: "语音活动",
      })
    }

    // 说话人识别：在 VAD 后触发
    if (events.vad.length > 0 && events.speaker.length < events.vad.length) {
      const lastVad = events.vad[events.vad.length - 1]
      events.speaker.push({
        startTime: lastVad.startTime + 0.2,
        duration: lastVad.duration - 0.2,
        type: "speaker",
        label: Math.random() > 0.5 ? "用户" : "访客",
      })
    }

    // LLM 输出：在说话人后生成
    if (events.speaker.length > 0 && events.llm.length < events.speaker.length) {
      const lastSpeaker = events.speaker[events.speaker.length - 1]
      events.llm.push({
        startTime: lastSpeaker.startTime + lastSpeaker.duration + 0.3,
        duration: 1.5 + Math.random() * 2,
        type: "llm",
        label: "正在思考...",
      })
    }

    // TTS 输出：在 LLM 后生成
    if (events.llm.length > 0 && events.tts.length < events.llm.length) {
      const lastLlm = events.llm[events.llm.length - 1]
      events.tts.push({
        startTime: lastLlm.startTime + lastLlm.duration + 0.1,
        duration: 2 + Math.random() * 3,
        type: "tts",
        label: "语音合成",
      })
    }
  }

  const drawTimeline = () => {
    if (!isActive) return

    const canvas = canvasRef.current
    if (!canvas) return

    const ctx = canvas.getContext("2d")
    if (!ctx) return

    const width = canvas.width
    const height = canvas.height
    const currentTime = (Date.now() - startTimeRef.current) / 1000

    // 生成模拟事件
    generateMockEvents(currentTime)

    // 清空画布
    ctx.fillStyle = "#09090b"
    ctx.fillRect(0, 0, width, height)

    // 时间轴参数
    const pixelsPerSecond = 80 // 每秒占据的像素
    const trackHeight = height / 5 // 每个轨道的高度
    const viewportEndTime = currentTime // 视口最右边显示当前时间
    const viewportStartTime = viewportEndTime - width / pixelsPerSecond // 视口最左边的时间

    // 时间转换为X坐标（从右到左滚动）
    const timeToX = (time: number) => {
      return width - (viewportEndTime - time) * pixelsPerSecond
    }

    // 绘制时间刻度线（每秒一条）
    ctx.strokeStyle = "#27272a"
    ctx.lineWidth = 1
    for (let t = Math.floor(viewportStartTime); t <= Math.ceil(viewportEndTime); t++) {
      const x = timeToX(t)
      if (x >= 0 && x <= width) {
        ctx.beginPath()
        ctx.moveTo(x, 0)
        ctx.lineTo(x, height)
        ctx.stroke()

        // 绘制时间标签
        ctx.fillStyle = "#71717a"
        ctx.font = "10px monospace"
        ctx.fillText(`${t}s`, x + 2, 12)
      }
    }

    // 绘制轨道分隔线
    ctx.strokeStyle = "#27272a"
    ctx.lineWidth = 1
    for (let i = 1; i < 5; i++) {
      ctx.beginPath()
      ctx.moveTo(0, i * trackHeight)
      ctx.lineTo(width, i * trackHeight)
      ctx.stroke()
    }

    // 定义轨道配置
    const tracks = [
      { name: "音频输入", events: eventsRef.current.audioInput, color: "#8b5cf6", y: 0 },
      { name: "VAD 事件", events: eventsRef.current.vad, color: "#10b981", y: 1 },
      { name: "说话人", events: eventsRef.current.speaker, color: "#f59e0b", y: 2 },
      { name: "LLM 输出", events: eventsRef.current.llm, color: "#06b6d4", y: 3 },
      { name: "TTS 输出", events: eventsRef.current.tts, color: "#ec4899", y: 4 },
    ]

    // 绘制每个轨道的事件
    tracks.forEach((track) => {
      const trackY = track.y * trackHeight

      // 绘制轨道名称
      ctx.fillStyle = "#a1a1aa"
      ctx.font = "12px sans-serif"
      ctx.fillText(track.name, 8, trackY + 20)

      // 绘制事件块
      track.events.forEach((event) => {
        const eventEndTime = event.startTime + event.duration

        // 只绘制在可视区域内的事件
        if (eventEndTime >= viewportStartTime && event.startTime <= viewportEndTime) {
          const x = timeToX(event.startTime)
          const eventWidth = event.duration * pixelsPerSecond
          const blockY = trackY + 30
          const blockHeight = trackHeight - 35

          // 绘制事件块背景
          ctx.fillStyle = track.color
          ctx.fillRect(x, blockY, eventWidth, blockHeight)

          // 绘制事件块边框
          ctx.strokeStyle = track.color
          ctx.lineWidth = 2
          ctx.strokeRect(x, blockY, eventWidth, blockHeight)

          // 绘制事件标签（如果有）
          if (event.label && eventWidth > 40) {
            ctx.fillStyle = "#ffffff"
            ctx.font = "10px sans-serif"
            ctx.fillText(event.label, x + 4, blockY + blockHeight / 2 + 4, eventWidth - 8)
          }
        }
      })
    })

    // 绘制播放头（当前时间线）
    const playheadX = width - 2
    ctx.strokeStyle = "#ef4444"
    ctx.lineWidth = 2
    ctx.beginPath()
    ctx.moveTo(playheadX, 0)
    ctx.lineTo(playheadX, height)
    ctx.stroke()

    // 清理超出视口的旧事件（优化性能）
    Object.values(eventsRef.current).forEach((events) => {
      const cutoffTime = viewportStartTime - 10
      while (events.length > 0 && events[0].startTime + events[0].duration < cutoffTime) {
        events.shift()
      }
    })

    animationRef.current = requestAnimationFrame(drawTimeline)
  }

  useEffect(() => {
    if (isActive) {
      startTimeRef.current = Date.now()
      eventsRef.current = {
        audioInput: [],
        vad: [],
        speaker: [],
        llm: [],
        tts: [],
      }
      drawTimeline()
    } else {
      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current)
      }
    }
    return () => {
      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current)
      }
    }
  }, [isActive])

  useEffect(() => {
    if (isActive) {
      const interval = setInterval(() => {
        const now = new Date().toLocaleTimeString()
        const isUser = Math.random() > 0.5
        const userTexts = ["你好", "今天天气怎么样", "能帮我查一下", "谢谢"]
        const aiTexts = ["您好，我是AI助手", "今天天气很好，温度适宜", "好的，我来帮您查询", "不客气，很高兴帮到您"]

        setDialogHistory((prev) => [
          ...prev,
          {
            type: isUser ? "user" : "ai",
            text: isUser
              ? userTexts[Math.floor(Math.random() * userTexts.length)]
              : aiTexts[Math.floor(Math.random() * aiTexts.length)],
            timestamp: now,
          },
        ])
      }, 3000)

      return () => clearInterval(interval)
    } else {
      setDialogHistory([])
    }
  }, [isActive])

  const handleToggle = () => {
    setIsActive(!isActive)
  }

  return (
    <div className="flex h-screen flex-col">
      <TopBar title="Realtime Playground" description="实时语音对话" />
      <main className="flex flex-1 overflow-hidden">
        {/* 左侧主要内容区域 */}
        <div className="flex-1 overflow-auto p-6">
          <div className="mx-auto max-w-6xl space-y-6">
            <Card className="overflow-hidden border-primary/20 bg-gradient-to-br from-background to-muted/30">
              <div className="p-6">
                <div className="mb-6 flex items-center justify-between">
                  <div className="flex items-center gap-4">
                    <div
                      className={`flex h-14 w-14 items-center justify-center rounded-full border-2 ${isActive ? "border-red-500 bg-red-500/10 animate-pulse" : "border-primary bg-primary/5"}`}
                    >
                      <Radio className={`h-7 w-7 ${isActive ? "text-red-500" : "text-primary"}`} />
                    </div>
                    <div>
                      <h2 className="text-2xl font-bold">实时语音对话</h2>
                      <p className="text-sm text-muted-foreground mt-1">
                        {isActive ? "🔴 对话进行中，实时输出ASR和LLM文本..." : "点击开始按钮与 AI 进行实时对话"}
                      </p>
                    </div>
                  </div>
                  {!isActive ? (
                    <Button onClick={handleToggle} size="lg" className="shadow-lg">
                      <Mic className="mr-2 h-5 w-5" />
                      开始对话
                    </Button>
                  ) : (
                    <Button onClick={handleToggle} variant="destructive" size="lg" className="shadow-lg">
                      <Square className="mr-2 h-5 w-5" />
                      停止对话
                    </Button>
                  )}
                </div>

                {isActive && (
                  <div className="space-y-4">
                    <div className="rounded-xl border-2 border-primary/20 bg-background overflow-hidden shadow-inner">
                      <canvas
                        ref={canvasRef}
                        width={1200}
                        height={400}
                        className="w-full"
                        style={{ display: "block" }}
                      />
                    </div>

                    <div className="rounded-lg bg-primary/5 border border-primary/20 p-3 text-xs text-muted-foreground">
                      <p className="flex items-center gap-2">
                        <span className="inline-block h-2 w-2 rounded-full bg-red-500 animate-pulse"></span>
                        时间轴从右向左实时滚动 | 红色线表示当前时间 | 彩色块表示各类事件
                      </p>
                    </div>
                  </div>
                )}

                {!isActive && (
                  <div className="rounded-xl border-2 border-dashed border-muted-foreground/30 bg-muted/30 p-12 text-center">
                    <MessageSquare className="mx-auto h-12 w-12 text-muted-foreground/50 mb-4" />
                    <p className="text-sm text-muted-foreground">开始对话后，将实时显示类似视频编辑软件的连续时间轴</p>
                    <p className="text-xs text-muted-foreground mt-2">
                      展示音频输入、VAD事件、说话人识别、LLM输出和TTS输出
                    </p>
                  </div>
                )}
              </div>
            </Card>

            {isActive && (
              <Card className="border-primary/10">
                <div className="p-4 border-b bg-muted/30">
                  <h4 className="font-semibold flex items-center gap-2">
                    <MessageSquare className="h-4 w-4 text-primary" />
                    实时对话记录
                  </h4>
                  <p className="text-xs text-muted-foreground mt-1">ASR 文本和 LLM 响应实时输出</p>
                </div>
                <ScrollArea className="h-64 p-4">
                  <div className="space-y-3">
                    {dialogHistory.length === 0 ? (
                      <p className="text-sm text-muted-foreground text-center py-8">等待对话开始...</p>
                    ) : (
                      dialogHistory.map((msg, idx) => (
                        <div key={idx} className={`flex gap-3 ${msg.type === "ai" ? "flex-row" : "flex-row-reverse"}`}>
                          <div
                            className={`flex-shrink-0 h-8 w-8 rounded-full flex items-center justify-center text-xs font-bold ${msg.type === "user" ? "bg-primary/10 text-primary" : "bg-green-500/10 text-green-600"}`}
                          >
                            {msg.type === "user" ? "U" : "AI"}
                          </div>
                          <div
                            className={`flex-1 rounded-lg p-3 ${msg.type === "user" ? "bg-primary/10 text-right" : "bg-muted"}`}
                          >
                            <p className="text-sm">{msg.text}</p>
                            <p className="text-xs text-muted-foreground mt-1">{msg.timestamp}</p>
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </ScrollArea>
              </Card>
            )}
          </div>
        </div>

        {/* 右侧配置面板 */}
        <div className="w-80 border-l bg-muted/30 p-6 overflow-auto">
          <div className="space-y-6">
            <div className="rounded-lg border bg-card p-4">
              <h3 className="mb-4 font-semibold text-lg">模型配置</h3>

              <div className="space-y-4">
                <div>
                  <Label className="mb-2 block text-sm font-medium">Audio 模型</Label>
                  <Select defaultValue="whisper-large-v3">
                    <SelectTrigger className="h-9">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="whisper-large-v3">Whisper Large V3</SelectItem>
                      <SelectItem value="whisper-medium">Whisper Medium</SelectItem>
                    </SelectContent>
                  </Select>
                  <p className="mt-1.5 text-xs text-muted-foreground">语音识别模型</p>
                </div>

                <div>
                  <Label className="mb-2 block text-sm font-medium">LLM 模型</Label>
                  <Select defaultValue="gpt-4o">
                    <SelectTrigger className="h-9">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="gpt-4o">GPT-4o</SelectItem>
                      <SelectItem value="gpt-4o-mini">GPT-4o Mini</SelectItem>
                      <SelectItem value="gpt-4-turbo">GPT-4 Turbo</SelectItem>
                    </SelectContent>
                  </Select>
                  <p className="mt-1.5 text-xs text-muted-foreground">对话生成模型</p>
                </div>

                <div>
                  <Label className="mb-2 block text-sm font-medium">TTS 模型</Label>
                  <Select defaultValue="tts-1-hd">
                    <SelectTrigger className="h-9">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="tts-1-hd">TTS-1-HD</SelectItem>
                      <SelectItem value="tts-1">TTS-1</SelectItem>
                    </SelectContent>
                  </Select>
                  <p className="mt-1.5 text-xs text-muted-foreground">语音合成模型</p>
                </div>
              </div>
            </div>

            <div className="rounded-lg border bg-card p-4">
              <h4 className="mb-4 text-sm font-semibold">参数配置</h4>
              <div className="space-y-4">
                <div>
                  <Label className="mb-2 block text-sm">语音音色</Label>
                  <Select defaultValue="alloy">
                    <SelectTrigger className="h-9">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="alloy">Alloy</SelectItem>
                      <SelectItem value="echo">Echo</SelectItem>
                      <SelectItem value="shimmer">Shimmer</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <Label className="mb-2 block text-sm">温度: 0.8</Label>
                  <Slider defaultValue={[0.8]} max={2} step={0.1} className="py-2" />
                  <p className="mt-1.5 text-xs text-muted-foreground">控制输出的创造性</p>
                </div>

                <div className="flex items-center justify-between py-2">
                  <Label className="text-sm">自动检测语音活动</Label>
                  <Switch defaultChecked />
                </div>

                <div className="flex items-center justify-between py-2">
                  <Label className="text-sm">启用打断</Label>
                  <Switch defaultChecked />
                </div>

                <div>
                  <Label className="mb-2 block text-sm">输入语言</Label>
                  <Select defaultValue="auto">
                    <SelectTrigger className="h-9">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="auto">自动检测</SelectItem>
                      <SelectItem value="zh">中文</SelectItem>
                      <SelectItem value="en">English</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </div>

            <div className="rounded-lg border bg-card p-4">
              <h4 className="mb-3 text-sm font-semibold">系统信息</h4>
              <div className="space-y-2.5 text-xs">
                <div className="flex justify-between py-1">
                  <span className="text-muted-foreground">音频延迟</span>
                  <span className="font-medium">&lt; 300ms</span>
                </div>
                <div className="flex justify-between py-1">
                  <span className="text-muted-foreground">支持语言</span>
                  <span className="font-medium">多语言</span>
                </div>
                <div className="flex justify-between py-1">
                  <span className="text-muted-foreground">音频输入</span>
                  <span className="font-medium">$0.06/分钟</span>
                </div>
                <div className="flex justify-between py-1">
                  <span className="text-muted-foreground">音频输出</span>
                  <span className="font-medium">$0.24/分钟</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}
