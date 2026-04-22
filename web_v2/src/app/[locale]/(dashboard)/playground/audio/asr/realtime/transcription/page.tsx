"use client"

import { TopBar } from "@/components/layout/top-bar"
import { Mic, Trash2, MicOff, Loader2, FileText, AudioLines } from "lucide-react"
import { Button } from "@/components/common/button"
import { Card } from "@/components/common/card"
import { Label } from "@/components/common/label"
import { Combobox, ComboboxOption } from "@/components/ui/combobox"
import { useState, useRef, useEffect, useMemo, useCallback } from "react"
import { useSearchParams } from "next/navigation"
import { useModelList } from "../../../../hooks/useModelList"
import { useAudioDevices } from "@/hooks/useAudioDevices"
import {
    RealtimeAudioRecorder,
    RealtimeAudioRecorderEventType
} from "@/lib/audio/RealtimeAudioRecorder"
import { getBaseURL } from "@/lib/api/client"
import { formatPriceValue } from "@/lib/utils/priceFormatter"
import { AudioDeviceSelector } from "@/components/playground/audio/AudioDeviceSelector"
import { logger } from "@/lib/utils/logger"

export default function RealtimeTranscriptionPage() {
    // 获取模型列表
    const { models, loading: modelsLoading, error: modelsError } = useModelList("/v1/audio/asr/stream")
    const searchParams = useSearchParams()

    // 状态管理
    const [isRecording, setIsRecording] = useState(false)
    const [isConnected, setIsConnected] = useState(false)
    const [transcriptText, setTranscriptText] = useState("")
    const [selectedModel, setSelectedModel] = useState("")
    const [error, setError] = useState<string>("")

    // 使用音频设备选择钩子
    const { audioSources, selectedSource, setSelectedSource, reloadDevices } = useAudioDevices((errorMsg) => {
        setError(errorMsg)
    })

    // Refs
    const realtimeAudioRecorderRef = useRef<RealtimeAudioRecorder | null>(null)

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

    // 初始化音频录制器
    useEffect(() => {
        if (!selectedSource || !selectedModel) return
        if (typeof window === 'undefined') return

        // 构建 WebSocket URL：将 http(s) 替换为 ws(s)
        const wsUrl = `${getBaseURL().replace(/\/$/, '').replace(/^http/, 'ws')}/v1/audio/asr/stream?model=${encodeURIComponent(selectedModel)}`;

        // 创建 RealtimeAudioRecorder 实例
        const recorder = new RealtimeAudioRecorder({
            deviceId: selectedSource,
            sampleRate: 16000,
            bufferSize: 3200,
            webSocketConfig: {
                url: wsUrl,
                reconnectAttempts: 3,
                reconnectInterval: 2000,
                timeoutMs: 10000,
            },
            mode: 'transcription'
        })

        // 设置事件监听
        recorder.on(RealtimeAudioRecorderEventType.TRANSCRIPTION_UPDATE, (text: string) => {
            setTranscriptText(text)
        })

        recorder.on(RealtimeAudioRecorderEventType.ERROR, (errorMsg: string) => {
            setError(errorMsg)
            setIsRecording(false)
            setIsConnected(false)
        })

        recorder.on(RealtimeAudioRecorderEventType.ASR_CLOSED, () => {
            setIsConnected(false)
            setIsRecording(false)
        })

        realtimeAudioRecorderRef.current = recorder

        // 清理函数
        return () => {
            if (realtimeAudioRecorderRef.current) {
                realtimeAudioRecorderRef.current.destroy()
                realtimeAudioRecorderRef.current = null
            }
        }
    }, [selectedSource, selectedModel])

    // 开始录音
    const handleStartRecording = useCallback(async () => {
        if (!selectedSource) {
            setError('请先选择音频设备')
            return
        }

        if (!selectedModel) {
            setError('请先选择模型')
            return
        }

        if (!realtimeAudioRecorderRef.current) {
            setError('录音器未初始化')
            return
        }

        try {
            setTranscriptText('')
            setError("")
            const success = await realtimeAudioRecorderRef.current.start()
            if (success) {
                setIsRecording(true)
                setIsConnected(true)
            }
        } catch (err) {
            logger.error('启动录音失败:', err)
            setError(`启动录音失败: ${err}`)
        }
    }, [selectedSource, selectedModel])

    // 停止录音
    const handleStopRecording = useCallback(async () => {
        if (!realtimeAudioRecorderRef.current || !isRecording) return

        try {
            await realtimeAudioRecorderRef.current.stop()
        } catch (err) {
            logger.error('停止录音失败:', err)
            setError(`停止录音失败: ${err}`)
        }
    }, [isRecording])

    // 清除转录内容
    const handleClear = useCallback(() => {
        setTranscriptText("")
        setError("")
    }, [])

    // 处理音频源选择
    const handleSourceChange = useCallback((deviceId: string) => {
        setSelectedSource(deviceId)
    }, [setSelectedSource])

    return (
        <div className="flex h-screen flex-col">
            <TopBar
                title="实时语音识别 Playground"
                description="实时录制音频并流式转录为文字"
            />
            <main className="flex flex-1 overflow-hidden">
                {/* Left main content area */}
                <div className="flex-1 overflow-auto p-6">
                    <div className="mx-auto max-w-6xl space-y-6">
                        {/* 错误提示 */}
                        {error && (
                            <div className="rounded-lg border border-destructive bg-destructive/10 p-4">
                                <p className="text-sm text-destructive">{error}</p>
                            </div>
                        )}

                        {/* 转录区域 */}
                        <Card className="p-6">
                            {/* 标题栏和控制栏 */}
                            <div className="mb-4 flex items-center justify-between">
                                {/* 左侧标题 */}
                                <div className="flex items-center gap-2">
                                    <AudioLines className="h-5 w-5 text-primary" />
                                    <h3 className="font-semibold">实时转录</h3>
                                    {isConnected && (
                                        <span className="inline-flex items-center gap-1.5 rounded-full bg-green-100 dark:bg-green-900/30 px-2.5 py-0.5 text-xs font-medium text-green-700 dark:text-green-400">
                                            <span className="h-1.5 w-1.5 rounded-full bg-green-600 dark:bg-green-400 animate-pulse"></span>
                                            已连接
                                        </span>
                                    )}
                                </div>

                                {/* 右侧控制栏 */}
                                <div className="flex items-center gap-2">
                                    <AudioDeviceSelector
                                        audioSources={audioSources}
                                        selectedSource={selectedSource}
                                        onSourceChange={handleSourceChange}
                                        disabled={isRecording}
                                    />

                                    {!isRecording ? (
                                        <Button
                                            size="icon"
                                            onClick={handleStartRecording}
                                            disabled={!selectedSource || !selectedModel}
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
                                        disabled={isRecording || (!transcriptText && !error)}
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
                                            {isRecording && (
                                                <span className="inline-block w-0.5 h-5 bg-primary ml-1 animate-pulse align-text-bottom" />
                                            )}
                                        </p>
                                    </div>
                                ) : (
                                    <div className="flex flex-col items-center justify-center h-full min-h-[240px] gap-4">
                                        {isRecording ? (
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
                                                    点击录音按钮开始实时转录
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
                                <li>选择一个麦克风设备和识别模型</li>
                                <li>点击录音按钮开始录制</li>
                                <li>说出您想要转录的内容</li>
                                <li>系统会实时处理您的录音并显示转录结果</li>
                                <li>点击停止按钮结束录制</li>
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
                                        disabled={modelsLoading || !!modelsError || models.length === 0 || isRecording}
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
