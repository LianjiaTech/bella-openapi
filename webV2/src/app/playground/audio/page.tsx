"use client"

import { TopBar } from "@/components/top-bar"
import { Mic, Upload, Circle, Square } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Slider } from "@/components/ui/slider"
import { useState, useRef, useEffect } from "react"
import { Badge } from "@/components/ui/badge"

export default function AudioPlaygroundPage() {
  const [isStreaming, setIsStreaming] = useState(false)
  const [isRecording, setIsRecording] = useState(false)
  const [streamText, setStreamText] = useState("")
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const animationRef = useRef<number>()
  const [selectedModel, setSelectedModel] = useState("whisper-1")

  const drawWaveform = (isActive: boolean) => {
    const canvas = canvasRef.current
    if (!canvas) return

    const ctx = canvas.getContext("2d")
    if (!ctx) return

    const width = canvas.width
    const height = canvas.height
    const centerY = height / 2

    ctx.clearRect(0, 0, width, height)

    if (isActive) {
      ctx.strokeStyle = "hsl(var(--primary))"
      ctx.lineWidth = 2
      ctx.beginPath()

      const time = Date.now() / 1000
      for (let x = 0; x < width; x++) {
        const y = centerY + Math.sin(x * 0.05 + time * 5) * (10 + Math.random() * 20)
        if (x === 0) ctx.moveTo(x, y)
        else ctx.lineTo(x, y)
      }

      ctx.stroke()
      animationRef.current = requestAnimationFrame(() => drawWaveform(true))
    }
  }

  useEffect(() => {
    if (isRecording) {
      drawWaveform(true)
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
  }, [isRecording])

  const handleStartRecording = () => {
    setIsRecording(true)
    setStreamText("")

    // 模拟实时文本输出
    const words = ["你好", "我是", "语音", "识别", "系统", "正在", "实时", "转录", "你的", "语音"]
    let index = 0
    const interval = setInterval(() => {
      if (index < words.length) {
        setStreamText((prev) => prev + (prev ? " " : "") + words[index])
        index++
      } else {
        clearInterval(interval)
      }
    }, 500)
  }

  const handleStopRecording = () => {
    setIsRecording(false)
  }

  return (
    <div className="flex h-screen flex-col">
      <TopBar title="Audio Playground" description="语音识别和转录" />
      <main className="flex flex-1 overflow-hidden">
        {/* 左侧主要内容区域 */}
        <div className="flex-1 overflow-auto p-6">
          <div className="mx-auto max-w-4xl space-y-6">
            {isStreaming ? (
              <>
                <Card className="p-6">
                  <div className="grid md:grid-cols-2 gap-6">
                    {/* 左侧：上传文件 */}
                    <div>
                      <div className="mb-4 flex items-center gap-2">
                        <Upload className="h-5 w-5 text-primary" />
                        <h3 className="font-semibold">上传音频文件</h3>
                      </div>
                      <div className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-muted-foreground/25 p-8 text-center min-h-[200px]">
                        <Upload className="mb-4 h-12 w-12 text-muted-foreground" />
                        <p className="mb-2 text-sm font-medium">拖拽音频文件或点击上传</p>
                        <p className="text-xs text-muted-foreground">支持 MP3, WAV, M4A, FLAC 等格式</p>
                      </div>
                    </div>

                    {/* 右侧：麦克风录音 */}
                    <div>
                      <div className="mb-4 flex items-center gap-2">
                        <Mic className="h-5 w-5 text-primary" />
                        <h3 className="font-semibold">麦克风录音</h3>
                      </div>
                      <div className="flex flex-col items-center justify-center rounded-lg border-2 border-primary/25 p-8 text-center min-h-[200px]">
                        <div
                          className={`mb-4 flex h-16 w-16 items-center justify-center rounded-full ${isRecording ? "bg-red-500/10 animate-pulse" : "bg-primary/10"}`}
                        >
                          <Mic className={`h-8 w-8 ${isRecording ? "text-red-500" : "text-primary"}`} />
                        </div>
                        {!isRecording ? (
                          <Button onClick={handleStartRecording} size="lg">
                            开始录音
                          </Button>
                        ) : (
                          <Button onClick={handleStopRecording} variant="destructive" size="lg">
                            <Square className="mr-2 h-5 w-5" />
                            结束录音
                          </Button>
                        )}
                      </div>
                    </div>
                  </div>
                </Card>

                {isRecording && (
                  <Card className="p-6">
                    <div className="mb-4 flex items-center justify-between">
                      <h3 className="font-semibold">音频输入轨道</h3>
                      <div className="flex items-center gap-2 text-red-500 animate-pulse">
                        <Circle className="h-3 w-3 fill-current" />
                        <span className="text-sm font-medium">录音中</span>
                      </div>
                    </div>
                    <div className="rounded-lg bg-muted p-4">
                      <canvas ref={canvasRef} width={800} height={100} className="w-full" />
                    </div>
                  </Card>
                )}

                <Card className="p-6">
                  <h3 className="mb-4 font-semibold">实时文本输出</h3>
                  <div className="min-h-[200px] rounded-lg bg-muted p-4">
                    <p className="text-sm whitespace-pre-wrap">
                      {streamText || <span className="text-muted-foreground">等待语音输入...</span>}
                      {isRecording && <span className="inline-block w-1 h-4 bg-primary ml-1 animate-pulse" />}
                    </p>
                  </div>
                </Card>
              </>
            ) : (
              // 非流式模式
              <>
                <Card className="p-6">
                  <div className="mb-4 flex items-center gap-2">
                    <Mic className="h-5 w-5 text-primary" />
                    <h3 className="font-semibold">上传音频文件</h3>
                  </div>
                  <div className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-muted-foreground/25 p-12 text-center">
                    <Upload className="mb-4 h-12 w-12 text-muted-foreground" />
                    <p className="mb-2 text-sm font-medium">拖拽音频文件或点击上传</p>
                    <p className="text-xs text-muted-foreground">支持 MP3, WAV, M4A, FLAC 等格式，最大 25MB</p>
                  </div>
                  <Button className="mt-4 w-full" size="lg">
                    开始识别
                  </Button>
                </Card>

                <Card className="p-6">
                  <h3 className="mb-4 font-semibold">转录结果</h3>
                  <div className="min-h-[200px] rounded-lg bg-muted p-4 text-sm">
                    <p className="text-muted-foreground">上传音频文件后，转录结果将显示在这里...</p>
                  </div>
                  <div className="mt-4 flex gap-2">
                    <Button variant="outline" size="sm">
                      复制文本
                    </Button>
                    <Button variant="outline" size="sm">
                      下载 JSON
                    </Button>
                    <Button variant="outline" size="sm">
                      下载 SRT
                    </Button>
                  </div>
                </Card>
              </>
            )}
          </div>
        </div>

        {/* 右侧配置面板 */}
        <div className="w-80 border-l bg-muted/30 p-6 overflow-auto">
          <div className="space-y-6">
            <div>
              <h3 className="mb-4 font-semibold">模型配置</h3>

              <div className="space-y-4">
                <div>
                  <Label className="mb-2 block">模型选择</Label>
                  <Select defaultValue="whisper-1" onValueChange={setSelectedModel}>
                    <SelectTrigger className="w-full">
                      <SelectValue>Whisper Large V3</SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="whisper-1">
                        <div className="flex flex-col gap-1 py-1">
                          <div className="font-medium">Whisper Large V3</div>
                          <div className="text-xs text-muted-foreground">最强语音识别，支持98+语言</div>
                          <div className="flex flex-wrap gap-1 mt-1">
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              多语言
                            </Badge>
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              高精度
                            </Badge>
                          </div>
                        </div>
                      </SelectItem>
                      <SelectItem value="whisper-medium">
                        <div className="flex flex-col gap-1 py-1">
                          <div className="font-medium">Whisper Medium</div>
                          <div className="text-xs text-muted-foreground">平衡性能和速度</div>
                          <div className="flex flex-wrap gap-1 mt-1">
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              多语言
                            </Badge>
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              平衡
                            </Badge>
                          </div>
                        </div>
                      </SelectItem>
                      <SelectItem value="whisper-small">
                        <div className="flex flex-col gap-1 py-1">
                          <div className="font-medium">Whisper Small</div>
                          <div className="text-xs text-muted-foreground">快速识别，适合实时场景</div>
                          <div className="flex flex-wrap gap-1 mt-1">
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              快速
                            </Badge>
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              实时
                            </Badge>
                          </div>
                        </div>
                      </SelectItem>
                    </SelectContent>
                  </Select>

                  {/* 选择后的模型信息显示在下方 */}
                  <div className="mt-3 rounded-lg border bg-muted/50 p-3 space-y-2">
                    <p className="text-xs text-muted-foreground leading-relaxed">最强语音识别，支持98+语言</p>
                    <div className="flex flex-wrap gap-1">
                      <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                        多语言
                      </Badge>
                      <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                        高精度
                      </Badge>
                    </div>
                  </div>
                </div>

                <div className="flex items-center justify-between rounded-lg border p-3">
                  <Label>流式识别</Label>
                  <Switch checked={isStreaming} onCheckedChange={setIsStreaming} />
                </div>

                <div>
                  <Label className="mb-2 block">语言</Label>
                  <Select defaultValue="auto">
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="auto">自动检测</SelectItem>
                      <SelectItem value="zh">中文</SelectItem>
                      <SelectItem value="en">English</SelectItem>
                      <SelectItem value="ja">日本語</SelectItem>
                      <SelectItem value="ko">한국어</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <Label className="mb-2 block">响应格式</Label>
                  <Select defaultValue="json">
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="json">JSON</SelectItem>
                      <SelectItem value="text">纯文本</SelectItem>
                      <SelectItem value="srt">SRT 字幕</SelectItem>
                      <SelectItem value="vtt">VTT 字幕</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="flex items-center justify-between">
                  <Label>时间戳</Label>
                  <Switch defaultChecked />
                </div>

                <div>
                  <Label className="mb-2 block">温度: 0</Label>
                  <Slider defaultValue={[0]} max={1} step={0.1} />
                  <p className="mt-2 text-xs text-muted-foreground">控制输出的随机性</p>
                </div>
              </div>
            </div>

            <div className="border-t pt-6">
              <h4 className="mb-3 text-sm font-medium">模型信息</h4>
              <div className="space-y-2 text-xs text-muted-foreground">
                <div className="flex justify-between">
                  <span>最大音频长度</span>
                  <span className="font-medium">25 MB</span>
                </div>
                <div className="flex justify-between">
                  <span>支持语言</span>
                  <span className="font-medium">98+</span>
                </div>
                <div className="flex justify-between">
                  <span>价格</span>
                  <span className="font-medium">$0.006/分钟</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}
