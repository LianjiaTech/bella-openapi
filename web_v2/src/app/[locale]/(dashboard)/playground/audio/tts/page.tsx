"use client"

import { TopBar } from "@/components/layout/top-bar"
import { Volume2, Download, Play, Trash2, Loader2 } from "lucide-react"
import { Button } from "@/components/common/button"
import { Textarea } from "@/components/common/textarea"
import { Card } from "@/components/common/card"
import { Label } from "@/components/common/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/common/select"
import { Slider } from "@/components/common/slider"
import { Combobox, ComboboxOption } from "@/components/ui/combobox"
import { useState, useRef, useEffect, useMemo } from "react"
import { useSearchParams } from "next/navigation"
import { useModelList } from "../../hooks/useModelList"
import { useAuth } from "@/components/providers/auth-provider"
import { generateSpeech } from "@/lib/api/tts"
import { useVoiceSelector } from "@/hooks/useVoiceSelector"

// TTS 输入文本长度限制（字符数）
const MAX_INPUT_LENGTH = 4000;

export default function TTSPlaygroundPage() {
    const { models, loading: modelsLoading, error: modelsError } = useModelList("/v1/audio/speech")
    const { user: authUser } = useAuth()
    const searchParams = useSearchParams()

    const [inputText, setInputText] = useState("")
    const [isGenerating, setIsGenerating] = useState(false)
    const [audioUrl, setAudioUrl] = useState<string | null>(null)
    const [isPlaying, setIsPlaying] = useState(false)
    const [generateError, setGenerateError] = useState<string | null>(null)
    const audioRef = useRef<HTMLAudioElement | null>(null)

    // 右侧配置参数
    const [selectedModel, setSelectedModel] = useState("")
    const [responseFormat, setResponseFormat] = useState("mp3")
    const [speed, setSpeed] = useState([1.0])
    const [sampleRate, setSampleRate] = useState("24000")

    // 使用声音选择器钩子
    const { voiceTypes, voiceName, setVoiceName, voiceLoading } = useVoiceSelector(true, selectedModel)

    // 初始化选中模型：URL 参数优先，否则使用第一个模型
    // 职责：统一处理模型初始化逻辑，避免多个 effect 相互触发造成 re-render
    // 优先级：URL 参数 > 默认第一个模型
    useEffect(() => {
        if (models.length === 0) return

        const modelFromUrl = searchParams.get("model")

        // 如果 URL 有指定模型，使用 URL 的值
        if (modelFromUrl) {
            setSelectedModel(modelFromUrl)
            return
        }

        // 如果没有选中模型，使用第一个模型作为默认值
        if (!selectedModel) {
            setSelectedModel(models[0].modelName)
        }
    }, [models, searchParams, selectedModel])

    // 将模型数据转换为 Combobox 需要的格式
    const modelOptions: ComboboxOption[] = useMemo(() => {
        return models.map((model) => ({
            value: model.modelName,
            label: model.modelName,
        }))
    }, [models])

    // 缓存当前选中的模型对象
    const selectedModelData = useMemo(() => {
        return models.find(m => m.modelName === selectedModel)
    }, [models, selectedModel])

    // 提取错误信息的辅助函数
    const extractErrorMessage = async (error: any): Promise<string> => {
        const fallback = error.message || "未知错误"

        if (error.response?.data instanceof Blob) {
            try {
                const text = await error.response.data.text()
                const errorData = JSON.parse(text)
                return errorData?.error?.message || errorData?.message || fallback
            } catch {
                return fallback
            }
        }

        if (error.response?.data) {
            const errorData = error.response.data
            return errorData?.error?.message || errorData?.message || fallback
        }

        return fallback
    }

    // 监听音频播放结束
    useEffect(() => {
        const audio = audioRef.current
        if (!audio) return

        const handleEnded = () => {
            setIsPlaying(false)
        }

        audio.addEventListener("ended", handleEnded)

        return () => {
            audio.removeEventListener("ended", handleEnded)
        }
    }, [audioUrl])

    // 生成语音
    const handleGenerate = async () => {
        if (!inputText.trim()) {
            setGenerateError("请输入要转换的文本")
            return
        }

        if (inputText.length > MAX_INPUT_LENGTH) {
            setGenerateError(`文本长度不能超过 ${MAX_INPUT_LENGTH} 个字符（当前 ${inputText.length} 字符）`)
            return
        }

        if (!selectedModel) {
            setGenerateError("请选择模型")
            return
        }

        try {
            setIsGenerating(true)
            setGenerateError(null)

            // 调用 TTS API
            const audioBlob = await generateSpeech({
                model: selectedModel,
                input: inputText,
                ...(voiceName && { voice: voiceTypes[voiceName] || voiceName }),
                response_format: responseFormat,
                speed: speed[0],
                sample_rate: parseInt(sampleRate),
                ...(authUser?.userId && { user: String(authUser.userId) }),
                stream: false,
            })

            // 创建音频 URL
            const url = URL.createObjectURL(audioBlob)

            // 清理旧的音频 URL
            if (audioUrl) {
                URL.revokeObjectURL(audioUrl)
            }

            setAudioUrl(url)

            // 创建音频元素并自动播放
            if (audioRef.current) {
                audioRef.current.pause()
                audioRef.current = null // 释放引用
            }
            const audio = new Audio(url)
            audioRef.current = audio

            // 自动播放（处理浏览器可能阻止自动播放的情况）
            audio.play().catch(error => {
                console.error("音频播放失败:", error)
                setGenerateError("音频自动播放被浏览器阻止，请点击下载按钮手动播放")
                setIsPlaying(false)
            })
            setIsPlaying(true)
        } catch (error: any) {
            console.error("生成语音失败:", error)

            // 使用提取的错误处理函数
            const message = await extractErrorMessage(error)
            setGenerateError(`生成语音失败：${message}`)
        } finally {
            setIsGenerating(false)
        }
    }

    const handleClear = () => {
        setInputText("")

        // 停止播放并清理音频
        if (audioRef.current) {
            audioRef.current.pause()
            audioRef.current = null
        }

        if (audioUrl) {
            URL.revokeObjectURL(audioUrl)
            setAudioUrl(null)
        }

        setIsPlaying(false)
    }

    const handleDownload = () => {
        if (!audioUrl) return

        const a = document.createElement("a")
        a.href = audioUrl
        a.download = `tts_${Date.now()}.${responseFormat}`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
    }

    // 清理资源 - 仅在组件卸载时执行
    useEffect(() => {
        return () => {
            if (audioUrl) {
                URL.revokeObjectURL(audioUrl)
            }
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    return (
        <div className="flex h-screen flex-col">
            <TopBar title="语音合成 Playground" description="文本转语音" />
            <main className="flex flex-1 overflow-hidden">
                {/* 左侧主要内容区域 */}
                <div className="flex-1 overflow-auto p-6">
                    <div className="mx-auto max-w-6xl space-y-6">
                        {/* 输入区域 */}
                        <Card className="p-6">
                            <div className="mb-4 flex items-center gap-2">
                                <Volume2 className="h-5 w-5 text-primary" />
                                <h3 className="font-semibold">语音合成</h3>
                            </div>
                            <Textarea
                                placeholder={"请输入要转换为语音的文本..."}
                                className="min-h-[280px]"
                                value={inputText}
                                onChange={(e) => setInputText(e.target.value)}
                            />
                            <div className="mt-2 flex justify-between text-xs text-muted-foreground">
                                <span>字符数：{inputText.length} / {MAX_INPUT_LENGTH}</span>
                                {inputText.length > MAX_INPUT_LENGTH && (
                                    <span className="text-destructive font-medium">超出限制</span>
                                )}
                            </div>
                            <div className="mt-4 flex items-center justify-center gap-3">
                                <Button
                                    onClick={handleGenerate}
                                    size="lg"
                                    disabled={!inputText.trim() || inputText.length > MAX_INPUT_LENGTH || isGenerating || !selectedModel || isPlaying}
                                    className="gap-2 px-8"
                                >
                                    {isGenerating ? (
                                        <>
                                            <Loader2 className="h-4 w-4 animate-spin" />
                                            生成中...
                                        </>
                                    ) : (
                                        <>
                                            <Play className="h-4 w-4" />
                                            生成语音
                                        </>
                                    )}
                                </Button>
                                {audioUrl && (
                                    <Button
                                        onClick={handleDownload}
                                        variant="outline"
                                        size="lg"
                                        className="gap-2 px-8"
                                    >
                                        <Download className="h-4 w-4" />
                                        下载音频
                                    </Button>
                                )}
                                <Button
                                    onClick={handleClear}
                                    variant="outline"
                                    size="lg"
                                    className="gap-2 px-8"
                                    disabled={isGenerating || isPlaying}
                                >
                                    <Trash2 className="h-4 w-4" />
                                    清除
                                </Button>
                            </div>

                            {/* 错误提示 */}
                            {generateError && (
                                <div className="mt-4 rounded-lg border border-destructive bg-destructive/10 p-4">
                                    <p className="text-sm text-destructive">{generateError}</p>
                                </div>
                            )}
                        </Card>

                        {/* 使用说明 */}
                        <Card className="p-6">
                            <h4 className="mb-3 text-sm font-semibold">使用说明：</h4>
                            <ol className="space-y-1.5 text-sm text-muted-foreground list-decimal list-inside leading-relaxed">
                                <li>输入要转换为语音的文本</li>
                                <li>选择模型和声音类型（如果可用）</li>
                                <li>点击"生成语音"按钮</li>
                                <li>生成完成后会自动播放音频</li>
                                <li>可以点击"下载音频"保存生成的语音文件</li>
                            </ol>
                        </Card>
                    </div>
                </div>

                {/* 右侧配置面板 */}
                <div className="w-80 border-l bg-muted/30 p-6 overflow-auto">
                    <div className="space-y-6">
                        <div>
                            <h3 className="mb-4 font-semibold">模型配置</h3>

                            <div className="space-y-4">
                                {/* 模型选择 */}
                                <div>
                                    <Label className="mb-2 block">模型选择</Label>
                                    <Combobox
                                        options={modelOptions}
                                        value={selectedModel}
                                        onValueChange={setSelectedModel}
                                        placeholder={modelsLoading ? "加载中..." : modelsError ? "加载失败" : models.length === 0 ? "暂无可用模型" : "选择模型"}
                                        searchPlaceholder="搜索模型..."
                                        emptyText="未找到匹配的模型"
                                        disabled={modelsLoading || !!modelsError || models.length === 0}
                                        className="w-full"
                                    />

                                    {/* 状态提示 */}
                                    {modelsLoading && (
                                        <p className="mt-2 text-xs text-muted-foreground">正在获取模型列表...</p>
                                    )}
                                    {modelsError && (
                                        <p className="mt-2 text-xs text-destructive">{modelsError}</p>
                                    )}
                                    {!modelsLoading && !modelsError && models.length === 0 && (
                                        <p className="mt-2 text-xs text-muted-foreground">当前没有可用的 TTS 模型</p>
                                    )}
                                </div>

                                {/* 语音音色 - 只有当有声音选项时才显示 */}
                                {(voiceLoading || Object.keys(voiceTypes).length > 0) && (
                                    <div>
                                        <Label className="mb-2 block">声音 (voice)</Label>
                                        <Select
                                            value={voiceName}
                                            onValueChange={setVoiceName}
                                            disabled={voiceLoading || Object.keys(voiceTypes).length === 0}
                                        >
                                            <SelectTrigger>
                                                <SelectValue placeholder={voiceLoading ? "加载中..." : "选择声音"} />
                                            </SelectTrigger>
                                            <SelectContent>
                                                {Object.keys(voiceTypes).map((voiceKey) => (
                                                    <SelectItem key={voiceKey} value={voiceKey}>
                                                        {voiceKey}
                                                    </SelectItem>
                                                ))}
                                            </SelectContent>
                                        </Select>
                                        {voiceLoading && (
                                            <p className="mt-2 text-xs text-muted-foreground">正在加载声音选项...</p>
                                        )}
                                    </div>
                                )}

                                {/* 输出格式 */}
                                <div>
                                    <Label className="mb-2 block">输出格式 (response_format)</Label>
                                    <Select value={responseFormat} onValueChange={setResponseFormat}>
                                        <SelectTrigger>
                                            <SelectValue />
                                        </SelectTrigger>
                                        <SelectContent>
                                            <SelectItem value="mp3">mp3</SelectItem>
                                            <SelectItem value="opus">opus</SelectItem>
                                            <SelectItem value="aac">aac</SelectItem>
                                            <SelectItem value="flac">flac</SelectItem>
                                            <SelectItem value="wav">wav</SelectItem>
                                            <SelectItem value="pcm">pcm</SelectItem>
                                        </SelectContent>
                                    </Select>
                                </div>

                                {/* 语速 */}
                                <div>
                                    <Label className="mb-2 flex items-center justify-between">
                                        <span>语速 (speed)</span>
                                        <span className="font-mono text-sm">{speed[0].toFixed(2)}</span>
                                    </Label>
                                    <Slider
                                        value={speed}
                                        onValueChange={setSpeed}
                                        min={0.25}
                                        max={4.0}
                                        step={0.25}
                                    />
                                    <p className="mt-2 text-xs text-muted-foreground">范围: 0.25 - 4.0</p>
                                </div>

                                {/* 采样率 */}
                                <div>
                                    <Label className="mb-2 block">采样率 (sample_rate)</Label>
                                    <Select value={sampleRate} onValueChange={setSampleRate}>
                                        <SelectTrigger>
                                            <SelectValue />
                                        </SelectTrigger>
                                        <SelectContent>
                                            <SelectItem value="8000">8000 Hz</SelectItem>
                                            <SelectItem value="16000">16000 Hz</SelectItem>
                                            <SelectItem value="22050">22050 Hz</SelectItem>
                                            <SelectItem value="24000">24000 Hz</SelectItem>
                                            <SelectItem value="44100">44100 Hz</SelectItem>
                                            <SelectItem value="48000">48000 Hz</SelectItem>
                                        </SelectContent>
                                    </Select>
                                </div>

                            </div>
                        </div>

                        {selectedModelData?.priceDetails?.displayPrice && Object.keys(selectedModelData.priceDetails.displayPrice).length > 0 && (
                            <div className="border-t pt-6">
                                <h4 className="mb-3 text-sm font-medium">价格信息</h4>
                                <div className="space-y-2 text-xs text-muted-foreground">
                                    <div className="flex justify-between">
                                        <div className="font-medium text-right">
                                            {Object.entries(selectedModelData.priceDetails.displayPrice).map(([key, value]) => (
                                                <div key={key}>{key}: {value}</div>
                                            ))}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </main>
        </div>
    )
}
