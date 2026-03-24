"use client"

import { TopBar } from "@/components/layout/top-bar"
import { Mic, Trash2, MicOff, Loader2, MessageSquare, ChevronDown, User, Bot, AudioLines } from "lucide-react"
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
import { Combobox, ComboboxOption } from "@/components/ui/combobox"
import { useState, useRef, useEffect, useMemo, useCallback } from "react"
import { useSearchParams } from "next/navigation"
import { useModelList } from "../../../../hooks/useModelList"
import { useAudioDevices } from "@/hooks/useAudioDevices"
import {
    RealtimeAudioRecorder,
    RealtimeAudioRecorderEventType
} from "@/lib/audio/RealtimeAudioRecorder"
import { PCMPlayer } from "@/lib/audio/PCMPlayer"
import config from "@/config"
import { formatPriceValue } from "@/lib/utils/priceFormatter"
import { AudioDeviceSelector } from "@/components/playground/audio/AudioDeviceSelector"
import { logger } from "@/lib/utils/logger"
import { useVoiceSelector } from "@/hooks/useVoiceSelector"

export default function RealtimeChatPage() {
    // 获取模型列表
    const { models, loading: modelsLoading, error: modelsError } = useModelList("/v1/audio/realtime")
    const searchParams = useSearchParams()

    // 状态管理
    const [isRecording, setIsRecording] = useState(false)
    const [isConnected, setIsConnected] = useState(false)
    const [transcriptText, setTranscriptText] = useState("")
    const [llmResponseText, setLlmResponseText] = useState("")
    const [selectedModel, setSelectedModel] = useState("")
    const [error, setError] = useState<string>("")
    const DEFAULT_SYSTEM_PROMPT = "你是一个全能的语音助理，你的回复会转成音频给用户，所以请尽可能简洁的回复，同时首句话尽快结束以便更好的进行流式合成语音，并且你需要注意你的回复内容便于阅读，不要包含无法阅读的内容。"
    const [systemPrompt, setSystemPrompt] = useState(DEFAULT_SYSTEM_PROMPT)

    // 使用声音选择器钩子
    const { voiceTypes, voiceName, setVoiceName, voiceLoading } = useVoiceSelector(true, selectedModel)
    const [messages, setMessages] = useState<Array<{ isUser: boolean; content: string; isLoading?: boolean }>>([
        { isUser: true, content: "你好，我想了解一下今天的天气怎么样？" },
        { isUser: false, content: "你好！今天北京天气晴朗，气温22°C到28°C，空气质量良好，是个适合户外活动的好天气。" },
        { isUser: true, content: "谢谢，那我想知道有什么户外活动推荐吗？" },
        { isUser: false, content: "当然！考虑到今天的好天气，我推荐你可以去公园野餐、骑自行车、参观户外博物馆或者去植物园。如果你喜欢运动，打网球或者慢跑也是不错的选择。你对哪种活动更感兴趣呢？" }
    ])
    const [isPromptExpanded, setIsPromptExpanded] = useState(true)

    // 使用音频设备选择钩子
    const { audioSources, selectedSource, setSelectedSource, reloadDevices } = useAudioDevices((errorMsg) => {
        setError(errorMsg)
    })

    // Refs
    const realtimeAudioRecorderRef = useRef<RealtimeAudioRecorder | null>(null)
    const playerRef = useRef<PCMPlayer | null>(null)
    const messagesEndRef = useRef<HTMLDivElement>(null)
    const scrollThrottleRef = useRef<NodeJS.Timeout | null>(null)
    const updateTranscriptionRef = useRef<((text: string) => void) | null>(null)
    const updateLlmResponseRef = useRef<((text: string) => void) | null>(null)

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
        return models.find((m) => m.modelName === selectedModel)
    }, [models, selectedModel])

    // 初始化 PCMPlayer 和音频录制器
    useEffect(() => {
        if (typeof window === 'undefined') return;

        // 初始化 PCMPlayer
        const player = new PCMPlayer({
            inputCodec: "Int16",
            channels: 1,
            sampleRate: 24000,
            flushTime: 100,
            fftSize: 2048
        })
        playerRef.current = player

        // 组件卸载时清理资源
        return () => {
            if (playerRef.current) {
                playerRef.current.destroy()
            }
            if (realtimeAudioRecorderRef.current && realtimeAudioRecorderRef.current.isRecording()) {
                realtimeAudioRecorderRef.current.stop()
            }
        }
    }, [])

    // 滚动到最新消息（使用节流避免频繁滚动）
    const scrollToBottom = useCallback(() => {
        // 清除之前的定时器
        if (scrollThrottleRef.current) {
            clearTimeout(scrollThrottleRef.current)
        }

        // 使用节流，200ms内只执行一次滚动
        scrollThrottleRef.current = setTimeout(() => {
            requestAnimationFrame(() => {
                messagesEndRef.current?.scrollIntoView({ behavior: "smooth", block: "center" })
            })
        }, 200)
    }, [])

    // 更新转写文本 - 实时更新或添加用户消息
    const updateTranscription = useCallback((text: string) => {
        if (!text) return

        setMessages(prev => {
            const newMessages = [...prev]
            let isNewMessage = false

            // 检查最后一条消息是否是用户消息
            if (newMessages.length > 0 && newMessages[newMessages.length - 1].isUser) {
                newMessages[newMessages.length - 1] = {
                    ...newMessages[newMessages.length - 1],
                    content: text
                }
            } else {
                isNewMessage = true
                newMessages.push({
                    isUser: true,
                    content: text
                })
            }

            // 只在添加新消息时滚动到新消息位置
            if (isNewMessage) {
                setTimeout(() => scrollToBottom(), 0)
            }

            return newMessages
        })
    }, [scrollToBottom])

    // 更新 LLM 响应 - 实时更新或添加 AI 消息
    const updateLlmResponse = useCallback((text: string) => {
        if (!text) return

        setMessages(prev => {
            const newMessages = [...prev]
            let isNewMessage = false

            if (newMessages.length > 0 && !newMessages[newMessages.length - 1].isUser) {
                newMessages[newMessages.length - 1] = {
                    ...newMessages[newMessages.length - 1],
                    content: text
                }
            } else {
                isNewMessage = true
                newMessages.push({
                    isUser: false,
                    content: text
                })
            }

            if (isNewMessage) {
                setTimeout(() => scrollToBottom(), 0)
            }

            return newMessages
        })
    }, [scrollToBottom])

    // 保持函数引用最新
    useEffect(() => {
        updateTranscriptionRef.current = updateTranscription
        updateLlmResponseRef.current = updateLlmResponse
    }, [updateTranscription, updateLlmResponse])

    // 监听转写文本变化
    useEffect(() => {
        if (transcriptText && updateTranscriptionRef.current) {
            updateTranscriptionRef.current(transcriptText)
        }
    }, [transcriptText])

    // 监听 LLM 响应变化
    useEffect(() => {
        if (llmResponseText && updateLlmResponseRef.current) {
            updateLlmResponseRef.current(llmResponseText)
        }
    }, [llmResponseText])

    // 构建 WebSocket URL
    const getWebSocketUrl = useCallback(() => {
        const protocol = window.location.protocol;
        const wsProtocol = protocol === 'https:' ? 'wss:' : 'ws:';
        // 优先使用配置的 baseUrl，否则使用当前域名
        const host = config.api.baseUrl || window.location.host;
        return `${wsProtocol}//${host}/v1/audio/realtime?model=${encodeURIComponent(selectedModel)}`;
    }, [selectedModel])

    // 开始/停止录音
    const toggleRecording = useCallback(async () => {
        if (isRecording) {
            // 停止录音
            if (realtimeAudioRecorderRef.current) {
                await realtimeAudioRecorderRef.current.stop()
                setIsRecording(false)

                // 停止音频播放
                if (playerRef.current) {
                    await playerRef.current.stop()
                }
            }
        } else {
            // 开始录音
            if (!selectedSource) {
                setError('请先选择音频设备')
                return
            }

            if (!selectedModel) {
                setError('请先选择模型')
                return
            }

            // 清空所有历史对话
            setTranscriptText('')
            setLlmResponseText('')
            setMessages([])
            setError("")

            // 如果之前已经创建过录音器，需要先释放资源
            if (realtimeAudioRecorderRef.current) {
                await realtimeAudioRecorderRef.current.stop()
                realtimeAudioRecorderRef.current = null
            }

            // 每次都重新创建实时录音器实例，确保配置（模型、设备、提示词等）生效
            const recorder = new RealtimeAudioRecorder({
                deviceId: selectedSource,
                sampleRate: 16000,
                bufferSize: 3200,
                webSocketConfig: {
                    url: getWebSocketUrl(),
                    reconnectAttempts: 3,
                    reconnectInterval: 2000,
                    timeoutMs: 10000,
                },
                voiceType: voiceName ? voiceTypes[voiceName] : undefined,
                mode: 'chat',
                system_prompt: systemPrompt
            })

            // 设置事件监听
            recorder.on(RealtimeAudioRecorderEventType.ERROR, (error: string) => {
                setError(error)
                setIsRecording(false)
                setIsConnected(false)
            })

            recorder.on(RealtimeAudioRecorderEventType.TRANSCRIPTION_UPDATE, (text: string) => {
                setTranscriptText(text)
            })

            recorder.on(RealtimeAudioRecorderEventType.LLM_RESPONSE_UPDATE, (text: string) => {
                setLlmResponseText(text)
            })

            recorder.on(RealtimeAudioRecorderEventType.TTS_AUDIO_DATA, (data: Uint8Array) => {
                playerRef.current?.feed(data)
            })

            recorder.on(RealtimeAudioRecorderEventType.SPEECH_START, async () => {
                // 当用户开始说话时停止当前音频播放
                if (playerRef.current) {
                    await playerRef.current.stop()
                }
            })

            recorder.on(RealtimeAudioRecorderEventType.ASR_CLOSED, () => {
                setIsConnected(false)
                setIsRecording(false)
            })

            realtimeAudioRecorderRef.current = recorder

            // 开始录音
            const success = await realtimeAudioRecorderRef.current.start()

            if (success) {
                setIsRecording(true)
                setIsConnected(true)
                setError("")
            }
        }
    }, [isRecording, selectedSource, selectedModel, voiceName, voiceTypes, systemPrompt, getWebSocketUrl])



    // 处理音频源选择
    const handleSourceChange = useCallback((deviceId: string) => {
        setSelectedSource(deviceId)
    }, [setSelectedSource])

    return (
        <div className="flex h-screen flex-col">
            <TopBar
                title="实时对话"
                description="实时语音对话，支持语音识别、AI 回复和语音合成"
            />
            <main className="flex flex-1 overflow-hidden">
                {/* Left main content area */}
                <div className="flex-1 flex flex-col overflow-hidden p-6">
                    <div className="mx-auto w-full max-w-6xl flex-1 flex flex-col space-y-6 overflow-auto">
                        {/* 系统提示词编辑区域 */}
                        <Card className="p-4">
                            <div
                                className="flex items-center gap-2 cursor-pointer"
                                onClick={() => {
                                    if (!isRecording) {
                                        setIsPromptExpanded(!isPromptExpanded)
                                    }
                                }}
                            >
                                <Label className="mb-0">系统提示词</Label>
                                {!isPromptExpanded && systemPrompt && (
                                    <span className="text-sm text-muted-foreground overflow-hidden text-ellipsis whitespace-nowrap flex-1">
                                        {systemPrompt.length > 60 ? systemPrompt.substring(0, 60) + '...' : systemPrompt}
                                    </span>
                                )}
                                <ChevronDown className={`h-4 w-4 transition-transform ${isPromptExpanded ? 'rotate-180' : ''}`} />
                            </div>

                            {isPromptExpanded && (
                                <div className="mt-3 space-y-3">
                                    <textarea
                                        value={systemPrompt}
                                        onChange={(e) => setSystemPrompt(e.target.value)}
                                        className="w-full min-h-[80px] px-3 py-2 rounded-md border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring"
                                        placeholder="输入系统提示词..."
                                        disabled={isRecording}
                                    />
                                    <div className="flex items-center justify-between">
                                        <Button
                                            variant="default"
                                            size="sm"
                                            onClick={() => setSystemPrompt(DEFAULT_SYSTEM_PROMPT)}
                                            disabled={isRecording || systemPrompt === DEFAULT_SYSTEM_PROMPT}
                                        >
                                            重置
                                        </Button>
                                        <p className="text-xs text-muted-foreground">
                                            💡 修改后点击"开始对话"时生效
                                        </p>
                                    </div>
                                </div>
                            )}
                        </Card>

                        {/* 错误提示 */}
                        {error && (
                            <div className="rounded-lg border border-destructive bg-destructive/10 p-4">
                                <p className="text-sm text-destructive">{error}</p>
                            </div>
                        )}

                        {/* 聊天区域 */}
                        <Card className="p-6">
                            {/* 标题栏 */}
                            <div className="mb-4 flex items-center justify-between">
                                {/* 左侧标题 */}
                                <div className="flex items-center gap-2">
                                    <MessageSquare className="h-5 w-5 text-primary" />
                                    <h3 className="font-semibold">实时对话</h3>
                                    {isConnected && (
                                        <span className="inline-flex items-center gap-1.5 rounded-full bg-green-100 dark:bg-green-900/30 px-2.5 py-0.5 text-xs font-medium text-green-700 dark:text-green-400">
                                            <span className="h-1.5 w-1.5 rounded-full bg-green-600 dark:bg-green-400 animate-pulse"></span>
                                            已连接
                                        </span>
                                    )}
                                </div>

                                {/* 右侧设备选择 */}
                                <AudioDeviceSelector
                                    audioSources={audioSources}
                                    selectedSource={selectedSource}
                                    onSourceChange={handleSourceChange}
                                    disabled={isRecording}
                                />
                            </div>

                            {/* 聊天消息显示区域 */}
                            <div className="min-h-[450px] max-h-[550px] overflow-y-auto rounded-lg border bg-muted/30 p-4">
                                {messages.length > 0 ? (
                                    <div className="space-y-3">
                                        {messages.map((msg, index) => (
                                            <div
                                                key={index}
                                                className={`flex gap-3 ${msg.isUser ? 'flex-row-reverse' : ''}`}
                                            >
                                                <div className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center ${
                                                    msg.isUser ? 'bg-primary text-primary-foreground' : 'bg-secondary text-secondary-foreground'
                                                }`}>
                                                    {msg.isUser ? <User className="h-5 w-5" /> : <Bot className="h-5 w-5" />}
                                                </div>
                                                <div className={`max-w-[70%] rounded-lg px-3 py-2 ${
                                                    msg.isUser ? 'bg-primary text-primary-foreground' : 'bg-background'
                                                }`}>
                                                    <p className="text-sm leading-relaxed whitespace-pre-wrap">{msg.content}</p>
                                                </div>
                                            </div>
                                        ))}
                                        {/* 如果正在录音但没有转写文本，显示等待输入状态 */}
                                        {isRecording && !transcriptText && (
                                            <div className="flex gap-3 flex-row-reverse">
                                                <div className="flex-shrink-0 w-8 h-8 rounded-full bg-primary text-primary-foreground flex items-center justify-center">
                                                    <User className="h-5 w-5" />
                                                </div>
                                                <div className="max-w-[70%] rounded-lg px-3 py-2 bg-primary text-primary-foreground">
                                                    <div className="flex items-center gap-2">
                                                        <Loader2 className="h-4 w-4 animate-spin" />
                                                        <span className="text-sm">...</span>
                                                    </div>
                                                </div>
                                            </div>
                                        )}
                                        {/* 如果有转写文本但没有AI回复，显示AI等待状态 */}
                                        {transcriptText && !llmResponseText && (
                                            <div className="flex gap-3">
                                                <div className="flex-shrink-0 w-8 h-8 rounded-full bg-secondary text-secondary-foreground flex items-center justify-center">
                                                    <Bot className="h-5 w-5" />
                                                </div>
                                                <div className="max-w-[70%] rounded-lg px-3 py-2 bg-background">
                                                    <div className="flex items-center gap-2">
                                                        <Loader2 className="h-4 w-4 animate-spin" />
                                                        <span className="text-sm text-muted-foreground">...</span>
                                                    </div>
                                                </div>
                                            </div>
                                        )}
                                        {/* 用于自动滚动到底部的空div */}
                                        <div ref={messagesEndRef} />
                                    </div>
                                ) : (
                                    <div className="flex flex-col items-center justify-center h-full min-h-[360px] gap-4">
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
                                                <MessageSquare className="h-12 w-12 text-muted-foreground/40" />
                                                <p className="text-muted-foreground text-center text-sm">
                                                    点击"开始对话"后，您的对话将显示在这里
                                                </p>
                                            </>
                                        )}
                                    </div>
                                )}
                            </div>

                            {/* 底部控制区域 */}
                            <div className="mt-4 flex justify-center">
                                <Button
                                    onClick={toggleRecording}
                                    disabled={!selectedSource || !selectedModel}
                                    size="lg"
                                    variant={isRecording ? "destructive" : "default"}
                                    className="rounded-full px-8"
                                >
                                    {isRecording ? (
                                        <>
                                            <MicOff className="h-4 w-4 mr-2" />
                                            停止对话
                                        </>
                                    ) : (
                                        <>
                                            <Mic className="h-4 w-4 mr-2" />
                                            开始对话
                                        </>
                                    )}
                                </Button>
                            </div>
                        </Card>

                        {/* Usage instructions */}
                        <Card className="bg-muted/50 p-4">
                            <p className="mb-2 text-sm font-medium text-muted-foreground">
                                使用说明:
                            </p>
                            <ol className="list-inside list-decimal space-y-1 text-sm text-muted-foreground">
                                <li>选择一个麦克风设备和识别模型</li>
                                <li>可选择编辑系统提示词来定制 AI 的回复风格</li>
                                <li>点击开始对话开启实时对话</li>
                                <li>说话后系统会自动进行语音识别并显示转写结果</li>
                                <li>语音识别完成后，AI 会自动回复并通过语音播放</li>
                                <li>点击停止按钮结束对话</li>
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
                                        <p className="mt-2 text-xs text-muted-foreground">当前没有可用的 Realtime 模型</p>
                                    )}
                                </div>

                                {/* 声音选项 */}
                                {(voiceLoading || Object.keys(voiceTypes).length > 0) && (
                                    <div>
                                        <Label className="mb-2 block">声音</Label>
                                        <Select
                                            value={voiceName}
                                            onValueChange={setVoiceName}
                                            disabled={voiceLoading || isRecording}
                                        >
                                            <SelectTrigger className="w-full">
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
