"use client"

import { TopBar } from "@/components/layout/top-bar"
import { Mic, Trash2, MicOff, Loader2, FileText, AudioLines } from "lucide-react"
import { Button } from "@/components/common/button"
import { Card } from "@/components/common/card"
import { Label } from "@/components/common/label"
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/common/select"
import { Slider } from "@/components/common/slider"
import { Combobox, ComboboxOption } from "@/components/ui/combobox"
import { useState, useRef, useEffect, useMemo } from "react"
import { useSearchParams } from "next/navigation"
import { useModelList } from "../../../hooks/useModelList"
import { useAudioDevices } from "@/hooks/useAudioDevices"
import {
    FlashAudioRecorder,
    FlashAudioRecorderEventType,
    FlashTranscriptionResponse
} from "@/lib/audio/FlashAudioRecorder"
import { getBaseURL } from "@/lib/api/client"
import { formatPriceValue } from "@/lib/utils/priceFormatter"
import { AudioDeviceSelector } from "@/components/playground/audio/AudioDeviceSelector"
import { logger } from "@/lib/utils/logger"

export default function TranscriptionPlaygroundPage() {
    // 获取模型列表
    const { models, loading: modelsLoading, error: modelsError } = useModelList("/v1/audio/asr/flash")
    const searchParams = useSearchParams()

    // 状态管理
    const [isRecording, setIsRecording] = useState(false)
    const [isProcessing, setIsProcessing] = useState(false)
    const [transcriptText, setTranscriptText] = useState("")
    const [selectedModel, setSelectedModel] = useState("")
    const [sampleRate, setSampleRate] = useState(16000)
    const [maxSentenceSilence, setMaxSentenceSilence] = useState([3000])
    const [debouncedMaxSentenceSilence, setDebouncedMaxSentenceSilence] = useState([3000])
    const [errorMessage, setErrorMessage] = useState("")

    // 使用音频设备选择钩子
    const { audioSources, selectedSource, setSelectedSource, reloadDevices } = useAudioDevices((error) => {
        setErrorMessage(error)
    })

    // Refs
    const flashAudioRecorderRef = useRef<FlashAudioRecorder | null>(null)
    const debounceTimerRef = useRef<NodeJS.Timeout | null>(null)

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

    // Debounce maxSentenceSilence 变化，避免slider拖动时频繁重建recorder
    useEffect(() => {
        if (debounceTimerRef.current) {
            clearTimeout(debounceTimerRef.current)
        }

        debounceTimerRef.current = setTimeout(() => {
            setDebouncedMaxSentenceSilence(maxSentenceSilence)
        }, 500) // 500ms debounce

        return () => {
            if (debounceTimerRef.current) {
                clearTimeout(debounceTimerRef.current)
            }
        }
    }, [maxSentenceSilence])

    // 初始化音频录制器（仅在设备/模型/参数变化时重新创建）
    useEffect(() => {
        if (!selectedSource || !selectedModel) return
        if (typeof window === 'undefined') return

        // 创建 FlashAudioRecorder 实例
        const recorder = new FlashAudioRecorder({
            url: `${getBaseURL().replace(/\/$/, '')}/v1/audio/asr/flash`,
            deviceId: selectedSource,
            model: selectedModel,
            sampleRate: sampleRate,
            maxDuration: 60000,
            maxSentenceSilence: debouncedMaxSentenceSilence[0],
        })

        // 设置事件监听
        recorder.on(FlashAudioRecorderEventType.RECORDING_START, () => {
            setIsRecording(true)
            setErrorMessage('')
        })

        recorder.on(FlashAudioRecorderEventType.RECORDING_COMPLETE, () => {
            setIsRecording(false)
            setIsProcessing(true)
        })

        recorder.on(FlashAudioRecorderEventType.TRANSCRIPTION_START, () => {
            setIsProcessing(true)
        })

        recorder.on(FlashAudioRecorderEventType.TRANSCRIPTION_COMPLETE, (result: FlashTranscriptionResponse) => {
            setIsProcessing(false)

            // 显示转录结果
            if (result.flash_result && result.flash_result.sentences && result.flash_result.sentences.length > 0) {
                const transcription = result.flash_result.sentences
                    .map(sentence => sentence.text)
                    .join('\n')

                setTranscriptText(transcription)
            } else {
                setTranscriptText('未检测到语音内容')
            }
        })

        recorder.on(FlashAudioRecorderEventType.ERROR, (error: string) => {
            setErrorMessage(error)
            setIsRecording(false)
            setIsProcessing(false)
        })

        flashAudioRecorderRef.current = recorder

        // 清理函数：仅在组件卸载或配置参数变化时才销毁
        return () => {
            if (flashAudioRecorderRef.current) {
                // 异步销毁录音器资源
                flashAudioRecorderRef.current.destroy().catch(error => {
                    logger.error('销毁录音器失败:', error)
                })
                flashAudioRecorderRef.current = null
            }
        }
    }, [selectedSource, selectedModel, sampleRate, debouncedMaxSentenceSilence])

    // 开始录音
    const handleStartRecording = async () => {
        if (!selectedSource) {
            setErrorMessage('请先选择音频设备')
            return
        }

        if (!selectedModel) {
            setErrorMessage('请先选择模型')
            return
        }

        if (!flashAudioRecorderRef.current) {
            setErrorMessage('录音器未初始化')
            return
        }

        try {
            setTranscriptText('')
            setErrorMessage('')
            await flashAudioRecorderRef.current.start()
        } catch (error) {
            logger.error('启动录音失败:', error)
            setErrorMessage(`启动录音失败: ${error}`)
        }
    }

    // 停止录音
    const handleStopRecording = async () => {
        if (!flashAudioRecorderRef.current || !isRecording) return

        try {
            await flashAudioRecorderRef.current.stop()
        } catch (error) {
            logger.error('停止录音失败:', error)
            setErrorMessage(`停止录音失败: ${error}`)
        }
    }

    // 清除转录内容
    const handleClear = () => {
        setTranscriptText("")
        setErrorMessage("")
    }

    // 处理音频源选择
    const handleSourceChange = (deviceId: string) => {
        setSelectedSource(deviceId)
    }

    // 手动请求麦克风权限
    const handleRequestPermission = async () => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
            stream.getTracks().forEach(track => track.stop())
            setErrorMessage('')
            // 重新加载设备列表，无需刷新整个页面
            await reloadDevices()
        } catch (error) {
            setErrorMessage(`麦克风权限请求失败: ${error instanceof Error ? error.message : String(error)}`)
        }
    }

    return (
        <div className="flex h-screen flex-col">
            <TopBar
                title="一句话语音识别 Playground"
                description="录制短音频并转录为文字"
            />
            <main className="flex flex-1 overflow-hidden">
                {/* Left main content area */}
                <div className="flex-1 overflow-auto p-6">
                    <div className="mx-auto max-w-6xl space-y-6">
                        {/* 错误提示 */}
                        {errorMessage && (
                            <div className="rounded-lg border border-destructive bg-destructive/10 p-4">
                                <p className="text-sm text-destructive mb-3">{errorMessage}</p>
                                {errorMessage.includes('权限') && (
                                    <>
                                        <Button
                                            onClick={handleRequestPermission}
                                            variant="outline"
                                            size="sm"
                                            className="mb-3"
                                        >
                                            重新请求麦克风权限
                                        </Button>
                                        <div className="text-xs text-muted-foreground">
                                            <p className="font-medium mb-2">如果按钮无效，请手动设置：</p>
                                            <ol className="list-decimal list-inside space-y-1">
                                                <li>点击浏览器地址栏左侧的锁图标 🔒</li>
                                                <li>找到"麦克风"权限设置</li>
                                                <li>将权限设置为"允许"</li>
                                                <li>刷新页面</li>
                                            </ol>
                                        </div>
                                    </>
                                )}
                            </div>
                        )}

                        {/* 转录区域 */}
                        <Card className="p-6">
                            {/* 标题栏和控制栏 */}
                            <div className="mb-4 flex items-center justify-between">
                                {/* 左侧标题 */}
                                <div className="flex items-center gap-2">
                                    <AudioLines className="h-5 w-5 text-primary" />
                                    <h3 className="font-semibold">一句话转录</h3>
                                </div>

                                {/* 右侧控制栏 */}
                                <div className="flex items-center gap-2">
                                    <AudioDeviceSelector
                                        audioSources={audioSources}
                                        selectedSource={selectedSource}
                                        onSourceChange={handleSourceChange}
                                        disabled={isRecording || isProcessing}
                                    />

                                    {!isRecording ? (
                                        <Button
                                            size="icon"
                                            onClick={handleStartRecording}
                                            disabled={!selectedSource || !selectedModel || isProcessing}
                                            className="h-10 w-10 rounded-full shrink-0"
                                        >
                                            <Mic className="h-5 w-5" />
                                        </Button>
                                    ) : (
                                        <Button
                                            size="icon"
                                            variant="destructive"
                                            onClick={handleStopRecording}
                                            className="h-10 w-10 rounded-full shrink-0"
                                        >
                                            <MicOff className="h-5 w-5" />
                                        </Button>
                                    )}

                                    <Button
                                        size="icon"
                                        variant="outline"
                                        onClick={handleClear}
                                        disabled={isRecording || isProcessing || (!transcriptText && !errorMessage)}
                                        className="h-10 w-10 shrink-0"
                                    >
                                        <Trash2 className="h-5 w-5" />
                                    </Button>
                                </div>
                            </div>

                            {/* 转录结果显示区域 */}
                            <div className="min-h-[280px] rounded-lg border bg-muted/30 p-6">
                                {transcriptText ? (
                                    <div className="space-y-3">
                                        <div className="flex items-center justify-between pb-2 border-b border-border/50">
                                            <div className="flex items-center gap-2">
                                                <FileText className="h-4 w-4 text-primary" />
                                                <span className="text-sm font-medium">转录结果</span>
                                            </div>
                                            <span className="text-xs text-muted-foreground">
                                                {transcriptText.length} 字符
                                            </span>
                                        </div>
                                        <p className="text-base leading-relaxed whitespace-pre-wrap">
                                            {transcriptText}
                                        </p>
                                    </div>
                                ) : (
                                    <div className="flex flex-col items-center justify-center h-full min-h-[240px] gap-4">
                                        {isProcessing ? (
                                            <>
                                                <Loader2 className="h-10 w-10 animate-spin text-primary" />
                                                <p className="text-muted-foreground">正在转录中，请稍候...</p>
                                            </>
                                        ) : isRecording ? (
                                            <>
                                                <div className="relative">
                                                    <AudioLines className="h-10 w-10 text-primary animate-pulse" />
                                                    <div className="absolute -top-1 -right-1 h-2.5 w-2.5 bg-red-500 rounded-full animate-pulse"></div>
                                                </div>
                                                <p className="text-muted-foreground">正在录音中，请说话...</p>
                                            </>
                                        ) : (
                                            <>
                                                <FileText className="h-12 w-12 text-muted-foreground/40" />
                                                <p className="text-muted-foreground text-center text-sm">
                                                    点击录音按钮开始录制
                                                </p>
                                            </>
                                        )}
                                    </div>
                                )}
                            </div>
                        </Card>

                        {/* Usage instructions */}
                        <Card className="bg-muted/50 p-4">
                            <p className="mb-2 text-sm font-medium text-muted-foreground">
                                使用说明:
                            </p>
                            <ol className="list-inside list-decimal space-y-1 text-sm text-muted-foreground">
                                <li>选择一个麦克风设备</li>
                                <li>点击录音按钮开始录制</li>
                                <li>说出您想要转录的内容</li>
                                <li>点击停止按钮结束录制</li>
                                <li>系统会自动处理您的录音并显示转录结果</li>
                            </ol>
                        </Card>
                    </div>
                </div>

                {/* Right side configuration panel */}
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
                                        disabled={modelsLoading || !!modelsError || models.length === 0 || isRecording || isProcessing}
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
                                        <p className="mt-2 text-xs text-muted-foreground">当前没有可用的 ASR 模型</p>
                                    )}
                                </div>

                                <div>
                                    <Label className="mb-2 block">采样率 (sample_rate)</Label>
                                    <Select
                                        value={sampleRate ? String(sampleRate) : "16000"}
                                        onValueChange={(val) => setSampleRate(Number(val))}
                                        disabled={isRecording || isProcessing}
                                    >
                                        <SelectTrigger>
                                            <SelectValue />
                                        </SelectTrigger>
                                        <SelectContent>
                                            <SelectItem value="8000">8000 Hz</SelectItem>
                                            <SelectItem value="16000">16000 Hz</SelectItem>
                                            <SelectItem value="24000">24000 Hz</SelectItem>
                                            <SelectItem value="48000">48000 Hz</SelectItem>
                                        </SelectContent>
                                    </Select>
                                </div>

                                <div>
                                    <Label className="mb-2 block">最大句子静默 (max_sentence_silence)</Label>
                                    <Slider
                                        value={maxSentenceSilence}
                                        onValueChange={setMaxSentenceSilence}
                                        min={1000}
                                        max={10000}
                                        step={500}
                                        disabled={isRecording || isProcessing}
                                    />
                                    <div className="mt-2 text-right">
                                        <span className="font-mono text-sm text-muted-foreground">{maxSentenceSilence[0]} ms</span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {selectedModelData && (
                            <div className="border-t pt-6">
                                <h4 className="mb-3 text-sm font-medium">价格信息</h4>
                                <div className="space-y-2 text-xs text-muted-foreground">
                                    {selectedModelData.priceDetails?.displayPrice && (
                                        <div className="flex justify-between">
                                            <div className="font-medium text-right">
                                                {Object.entries(selectedModelData.priceDetails.displayPrice).map(([key, value]) => (
                                                    <div key={key}>{key}: {formatPriceValue(value)}</div>
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </main>
        </div>
    )
}
