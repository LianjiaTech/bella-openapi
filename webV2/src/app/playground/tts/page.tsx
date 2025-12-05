"use client"

import { TopBar } from "@/components/top-bar"
import { Volume2, Download, VolumeX, Play, Pause } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Card } from "@/components/ui/card"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Slider } from "@/components/ui/slider"
import { Switch } from "@/components/ui/switch"
import { Badge } from "@/components/ui/badge"
import { useState, useRef, useEffect } from "react"

export default function TTSPlaygroundPage() {
  const [isStreaming, setIsStreaming] = useState(false)
  const [audioGenerated, setAudioGenerated] = useState(true) // 默认显示播放器
  const [isPlaying, setIsPlaying] = useState(false)
  const [currentTime, setCurrentTime] = useState(0)
  const [duration] = useState(10) // 10秒音频
  const [volume, setVolume] = useState(100)
  const [playbackRate, setPlaybackRate] = useState(1)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const animationRef = useRef<number>()

  const drawStaticWaveform = () => {
    const canvas = canvasRef.current
    if (!canvas) return

    const ctx = canvas.getContext("2d")
    if (!ctx) return

    const width = canvas.width
    const height = canvas.height
    const centerY = height / 2

    ctx.clearRect(0, 0, width, height)

    // 绘制波形条
    ctx.fillStyle = "hsl(var(--muted-foreground))"
    const bars = 150
    const barWidth = 3
    const gap = 2

    for (let i = 0; i < bars; i++) {
      const x = i * (barWidth + gap)
      const amplitude = Math.random() * 0.6 + 0.2
      const barHeight = height * amplitude

      ctx.fillRect(x, centerY - barHeight / 2, barWidth, barHeight)
    }
  }

  useEffect(() => {
    drawStaticWaveform()
  }, [])

  useEffect(() => {
    if (isPlaying) {
      const interval = setInterval(() => {
        setCurrentTime((prev) => {
          if (prev >= duration) {
            setIsPlaying(false)
            return 0
          }
          return prev + 0.1
        })
      }, 100)
      return () => clearInterval(interval)
    }
  }, [isPlaying, duration])

  const handleGenerate = () => {
    setAudioGenerated(true)
    setIsPlaying(false)
    setTimeout(() => drawStaticWaveform(), 100)
  }

  const togglePlay = () => {
    setIsPlaying(!isPlaying)
  }

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = Math.floor(seconds % 60)
    return `${mins}:${secs.toString().padStart(2, "0")}`
  }

  return (
    <div className="flex h-screen flex-col">
      <TopBar title="TTS Playground" description="文本转语音合成" />
      <main className="flex flex-1 overflow-hidden">
        {/* 左侧主要内容区域 */}
        <div className="flex-1 overflow-auto p-6">
          <div className="mx-auto max-w-4xl space-y-6">
            <Card className="p-6">
              <div className="mb-4 flex items-center gap-2">
                <Volume2 className="h-5 w-5 text-primary" />
                <h3 className="font-semibold">输入文本</h3>
              </div>
              <Textarea
                placeholder="输入要转换为语音的文本...&#10;&#10;支持多种语言和音色，可以生成自然流畅的语音。"
                className="min-h-[200px]"
              />
              <Button onClick={handleGenerate} className="mt-4 w-full" size="lg">
                生成语音
              </Button>
            </Card>

            {audioGenerated && (
              <Card className="p-6">
                <div className="space-y-4">
                  {/* 标题和下载按钮 */}
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Volume2 className="h-5 w-5 text-muted-foreground" />
                      <span className="text-sm font-medium text-red-500">Output Audio</span>
                    </div>
                    <Button variant="ghost" size="sm" className="text-red-500 hover:text-red-600 hover:bg-red-50">
                      <div className="flex items-center gap-2">
                        <div className="flex h-6 w-6 items-center justify-center rounded-full bg-red-500 text-xs text-white">
                          19
                        </div>
                        <span>音频下载</span>
                        <Download className="h-4 w-4" />
                      </div>
                    </Button>
                  </div>

                  {/* 标题 */}
                  <h3 className="text-xl font-semibold text-red-500">音频生成结果</h3>

                  {/* 波形显示 */}
                  <div className="relative rounded-lg bg-background border p-4">
                    <canvas ref={canvasRef} width={1200} height={80} className="w-full" />

                    {/* 时间显示 */}
                    <div className="mt-2 flex items-center justify-between text-sm text-muted-foreground">
                      <span>{formatTime(currentTime)}</span>
                      <span>{formatTime(duration)}</span>
                    </div>
                  </div>

                  {/* 播放控制栏 */}
                  {!isStreaming && (
                    <div className="flex items-center justify-center gap-6">
                      {/* 音量控制 */}
                      <div className="flex items-center gap-2">
                        <Button variant="ghost" size="icon" className="h-8 w-8">
                          {volume > 0 ? <Volume2 className="h-4 w-4" /> : <VolumeX className="h-4 w-4" />}
                        </Button>
                      </div>

                      {/* 播放按钮 */}
                      <Button
                        onClick={togglePlay}
                        variant="ghost"
                        size="icon"
                        className="h-12 w-12 rounded-full bg-red-500 hover:bg-red-600 text-white"
                      >
                        {isPlaying ? (
                          <Pause className="h-6 w-6 fill-current" />
                        ) : (
                          <Play className="h-6 w-6 fill-current ml-0.5" />
                        )}
                      </Button>

                      {/* 占位 */}
                      <div className="w-20"></div>
                    </div>
                  )}

                  {isStreaming && (
                    <div className="text-center">
                      <div className="inline-flex items-center gap-2 rounded-full bg-green-500 px-4 py-1.5 text-sm font-medium text-white animate-pulse">
                        <Volume2 className="h-4 w-4" />
                        正在流式播放...
                      </div>
                    </div>
                  )}

                  {/* 播放提示 */}
                  <div className="text-center">
                    <div className="inline-flex items-center gap-2 rounded-full bg-red-500 px-4 py-1.5 text-sm font-medium text-white">
                      <div className="flex h-5 w-5 items-center justify-center rounded-full bg-white text-xs text-red-500">
                        20
                      </div>
                      音频播放
                    </div>
                  </div>
                </div>
              </Card>
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
                  <Select defaultValue="tts-1-hd">
                    <SelectTrigger className="w-full">
                      <SelectValue>TTS-1-HD</SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="tts-1-hd">
                        <div className="flex flex-col gap-1 py-1">
                          <div className="font-medium">TTS-1-HD</div>
                          <div className="text-xs text-muted-foreground">高清音质，自然流畅</div>
                          <div className="flex flex-wrap gap-1 mt-1">
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              高清
                            </Badge>
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              多音色
                            </Badge>
                          </div>
                        </div>
                      </SelectItem>
                      <SelectItem value="tts-1">
                        <div className="flex flex-col gap-1 py-1">
                          <div className="font-medium">TTS-1</div>
                          <div className="text-xs text-muted-foreground">标准音质，性价比高</div>
                          <div className="flex flex-wrap gap-1 mt-1">
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              标准
                            </Badge>
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              快速
                            </Badge>
                          </div>
                        </div>
                      </SelectItem>
                    </SelectContent>
                  </Select>

                  {/* 选择后的模型信息显示在下方 */}
                  <div className="mt-3 rounded-lg border bg-muted/50 p-3 space-y-2">
                    <p className="text-xs text-muted-foreground leading-relaxed">高清音质，自然流畅</p>
                    <div className="flex flex-wrap gap-1">
                      <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                        高清
                      </Badge>
                      <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                        多音色
                      </Badge>
                    </div>
                  </div>
                </div>

                <div className="flex items-center justify-between rounded-lg border p-3">
                  <Label>流式输出</Label>
                  <Switch checked={isStreaming} onCheckedChange={setIsStreaming} />
                </div>

                <div>
                  <Label className="mb-2 block">语音音色</Label>
                  <Select defaultValue="alloy">
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="alloy">Alloy (中性)</SelectItem>
                      <SelectItem value="echo">Echo (男性)</SelectItem>
                      <SelectItem value="fable">Fable (英式)</SelectItem>
                      <SelectItem value="onyx">Onyx (深沉)</SelectItem>
                      <SelectItem value="nova">Nova (女性)</SelectItem>
                      <SelectItem value="shimmer">Shimmer (柔和)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <Label className="mb-2 block">语速: 1.0x</Label>
                  <Slider defaultValue={[1.0]} min={0.25} max={4.0} step={0.25} />
                  <p className="mt-2 text-xs text-muted-foreground">范围: 0.25x - 4.0x</p>
                </div>

                <div>
                  <Label className="mb-2 block">输出格式</Label>
                  <Select defaultValue="mp3">
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="mp3">MP3</SelectItem>
                      <SelectItem value="opus">Opus</SelectItem>
                      <SelectItem value="aac">AAC</SelectItem>
                      <SelectItem value="flac">FLAC</SelectItem>
                      <SelectItem value="wav">WAV</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </div>

            <div className="border-t pt-6">
              <h4 className="mb-3 text-sm font-medium">模型信息</h4>
              <div className="space-y-2 text-xs text-muted-foreground">
                <div className="flex justify-between">
                  <span>最大输入</span>
                  <span className="font-medium">4096 字符</span>
                </div>
                <div className="flex justify-between">
                  <span>支持语言</span>
                  <span className="font-medium">50+</span>
                </div>
                <div className="flex justify-between">
                  <span>价格 (HD)</span>
                  <span className="font-medium">$15/1M 字符</span>
                </div>
                <div className="flex justify-between">
                  <span>价格 (标准)</span>
                  <span className="font-medium">$7.5/1M 字符</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}
