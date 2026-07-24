"use client"

import { TopBar } from "@/components/layout/top-bar"
import { Volume2, Download, Play, Trash2, Loader2, Send, Square, XCircle } from "lucide-react"
import { Button } from "@/components/common/button"
import { Textarea } from "@/components/common/textarea"
import { Card } from "@/components/common/card"
import { Label } from "@/components/common/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/common/select"
import { Slider } from "@/components/common/slider"
import { Combobox, ComboboxOption } from "@/components/ui/combobox"
import { useState, useRef, useEffect, useMemo } from "react"
import { usePathname, useRouter, useSearchParams } from "next/navigation"
import { useModelList } from "../../hooks/useModelList"
import { useAuth } from "@/components/providers/auth-provider"
import { generateSpeech } from "@/lib/api/tts"
import { getBaseURL } from "@/lib/api/client"
import { useVoiceSelector } from "@/hooks/useVoiceSelector"

// TTS 输入文本长度限制（字符数）
const MAX_INPUT_LENGTH = 4000;
const HTTP_TTS_ENDPOINT = "/v1/audio/speech";
const REALTIME_TTS_ENDPOINT = "/v1/audio/tts/stream";
const HTTP_TTS_FORMATS = ["mp3", "opus", "aac", "flac", "wav", "pcm"];
const REALTIME_TTS_FORMATS = ["mp3", "opus", "pcm"];

type TtsEndpoint = typeof HTTP_TTS_ENDPOINT | typeof REALTIME_TTS_ENDPOINT;
type RealtimeSessionState = "idle" | "connecting" | "started" | "finishing" | "cancelling" | "completed" | "failed";

type RealtimeEventLog = {
    id: string;
    direction: "client" | "server" | "audio" | "system";
    label: string;
    detail?: string;
    timestamp: number;
};

type RealtimeTtsMessage = {
    header: {
        name: string;
        message_id: string;
        task_id?: string;
        sequence?: number;
    };
    payload: Record<string, unknown>;
};

const TTS_ENDPOINT_OPTIONS: Array<{ value: TtsEndpoint; label: string }> = [
    { value: HTTP_TTS_ENDPOINT, label: "语音合成" },
    { value: REALTIME_TTS_ENDPOINT, label: "流式语音合成" },
];

const endpointFromSearchParams = (searchParams: { get: (name: string) => string | null }): TtsEndpoint => {
    const endpoint = searchParams.get("endpoint") || searchParams.get("ocrType")
    return endpoint === REALTIME_TTS_ENDPOINT ? REALTIME_TTS_ENDPOINT : HTTP_TTS_ENDPOINT
}

const createMessageId = () => `msg_${crypto.randomUUID().replace(/-/g, "")}`;
const createTaskId = () => `task_${crypto.randomUUID().replace(/-/g, "")}`;

const toWebSocketUrl = () => {
    const baseUrl = getBaseURL().replace(/\/$/, "")
    const origin = !baseUrl || baseUrl === "/"
        ? window.location.origin
        : baseUrl.startsWith("//")
            ? `${window.location.protocol}${baseUrl}`
            : baseUrl.startsWith("/")
                ? `${window.location.origin}${baseUrl}`
            : baseUrl

    return `${origin.replace(/^http/, "ws")}${REALTIME_TTS_ENDPOINT}`
}

const audioMimeType = (format: string) => {
    switch (format) {
        case "mp3":
            return "audio/mpeg"
        case "wav":
            return "audio/wav"
        case "aac":
            return "audio/aac"
        case "flac":
            return "audio/flac"
        case "opus":
            return "audio/ogg; codecs=opus"
        default:
            return "application/octet-stream"
    }
}

export default function TTSPlaygroundPage() {
    const router = useRouter()
    const pathname = usePathname()
    const searchParams = useSearchParams()
    const [selectedEndpoint, setSelectedEndpoint] = useState<TtsEndpoint>(() => endpointFromSearchParams(searchParams))
    const isRealtimeTts = selectedEndpoint === REALTIME_TTS_ENDPOINT
    const { models, loading: modelsLoading, error: modelsError } = useModelList(selectedEndpoint)
    const { user: authUser } = useAuth()
    const formatOptions = isRealtimeTts ? REALTIME_TTS_FORMATS : HTTP_TTS_FORMATS

    const [inputText, setInputText] = useState("")
    const [isGenerating, setIsGenerating] = useState(false)
    const [audioUrl, setAudioUrl] = useState<string | null>(null)
    const [isPlaying, setIsPlaying] = useState(false)
    const [generateError, setGenerateError] = useState<string | null>(null)
    const [streamStatus, setStreamStatus] = useState<string | null>(null)
    const [streamBytes, setStreamBytes] = useState(0)
    const audioRef = useRef<HTMLAudioElement | null>(null)
    const realtimeWsRef = useRef<WebSocket | null>(null)
    const realtimeSequenceRef = useRef(1)
    const realtimeAudioChunksRef = useRef<BlobPart[]>([])
    const realtimeAudioBytesRef = useRef(0)
    const [realtimeSessionState, setRealtimeSessionState] = useState<RealtimeSessionState>("idle")
    const [realtimeTaskId, setRealtimeTaskId] = useState("")
    const [realtimeSentChunks, setRealtimeSentChunks] = useState(0)
    const [realtimeEvents, setRealtimeEvents] = useState<RealtimeEventLog[]>([])

    // 右侧配置参数
    const [selectedModel, setSelectedModel] = useState("")
    const [responseFormat, setResponseFormat] = useState("mp3")
    const [speed, setSpeed] = useState([1.0])
    const [sampleRate, setSampleRate] = useState("24000")

    // 使用声音选择器钩子
    const { voiceTypes, voiceName, setVoiceName, voiceLoading } = useVoiceSelector(true, selectedModel)
    const realtimeSessionActive = ["connecting", "started", "finishing", "cancelling"].includes(realtimeSessionState)

    useEffect(() => {
        const endpoint = endpointFromSearchParams(searchParams)
        setSelectedEndpoint(endpoint)
    }, [searchParams])

    const handleEndpointChange = (endpoint: string) => {
        const nextEndpoint = endpoint === REALTIME_TTS_ENDPOINT ? REALTIME_TTS_ENDPOINT : HTTP_TTS_ENDPOINT
        setSelectedEndpoint(nextEndpoint)
        setSelectedModel("")
        setVoiceName("")
        setStreamStatus(null)
        setStreamBytes(0)
        setGenerateError(null)

        const params = new URLSearchParams(searchParams.toString())
        params.set("endpoint", nextEndpoint)
        params.delete("ocrType")
        params.delete("model")
        router.replace(`${pathname}?${params.toString()}`, { scroll: false })
    }

    useEffect(() => {
        if (formatOptions.includes(responseFormat)) return
        setResponseFormat(formatOptions[0])
    }, [formatOptions, responseFormat])

    // 初始化选中模型：URL 参数优先，否则使用第一个模型
    // 职责：统一处理模型初始化逻辑，避免多个 effect 相互触发造成 re-render
    // 优先级：URL 参数 > 默认第一个模型
    useEffect(() => {
        if (models.length === 0) return

        const modelFromUrl = searchParams.get("model")

        // 如果 URL 有指定模型且属于当前 endpoint，使用 URL 的值
        if (modelFromUrl && models.some(model => model.modelName === modelFromUrl)) {
            setSelectedModel(modelFromUrl)
            return
        }

        // 如果没有选中模型，或当前模型不属于当前 endpoint，使用第一个模型作为默认值
        if (!selectedModel || !models.some(model => model.modelName === selectedModel)) {
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

    const sendRealtimeMessage = (ws: WebSocket, message: RealtimeTtsMessage) => {
        ws.send(JSON.stringify(message))
    }

    const sendRealtimeInput = (ws: WebSocket, taskId: string, text: string) => {
        const sequence = realtimeSequenceRef.current++
        sendRealtimeMessage(ws, {
            header: {
                name: "InputText",
                message_id: createMessageId(),
                task_id: taskId,
                sequence,
            },
            payload: {
                text,
            },
        })
        setRealtimeSentChunks((count) => count + 1)
        setGenerateError(null)
        setStreamStatus(`已发送第 ${sequence} 段文本`)
        appendRealtimeEvent("client", `InputText #${sequence}`, text)
        return sequence
    }

    const appendRealtimeEvent = (direction: RealtimeEventLog["direction"], label: string, detail?: string) => {
        setRealtimeEvents((events) => [
            ...events.slice(-79),
            {
                id: `evt_${crypto.randomUUID().replace(/-/g, "")}`,
                direction,
                label,
                detail,
                timestamp: Date.now(),
            },
        ])
    }

    const publishRealtimeAudio = () => {
        const chunkCount = realtimeAudioChunksRef.current.length
        const mimeType = audioMimeType(responseFormat)
        if (chunkCount === 0) {
            appendRealtimeEvent("system", "NoAudioChunks", "服务端完成但前端未收到二进制音频帧")
            setStreamStatus("合成完成，但未收到音频二进制帧")
            return
        }

        const audioBlob = new Blob(realtimeAudioChunksRef.current, { type: mimeType })
        const url = URL.createObjectURL(audioBlob)

        setAudioUrl((previousUrl) => {
            if (previousUrl) {
                URL.revokeObjectURL(previousUrl)
            }
            return url
        })

        if (audioRef.current) {
            audioRef.current.pause()
            audioRef.current = null
        }

        const audio = new Audio(url)
        audioRef.current = audio
        audio.play().catch(() => {
            setGenerateError("音频自动播放被浏览器阻止，请点击下载按钮手动播放")
            setIsPlaying(false)
        })
        setIsPlaying(true)
    }

    const closeRealtimeSocket = (code = 1000, reason = "client_close") => {
        const ws = realtimeWsRef.current
        realtimeWsRef.current = null
        if (!ws) return
        if (ws.readyState === WebSocket.CONNECTING || ws.readyState === WebSocket.OPEN) {
            ws.close(code, reason)
        }
    }

    const startRealtimeSession = () => {
        if (typeof window === "undefined") {
            setGenerateError("当前环境不支持 WebSocket")
            return
        }

        if (!selectedModel) {
            setGenerateError("请选择模型")
            return
        }

        const initialText = inputText.trim()
        if (!initialText) {
            setGenerateError("请输入首段文本后再启动流式任务")
            return
        }

        if (inputText.length > MAX_INPUT_LENGTH) {
            setGenerateError(`文本长度不能超过 ${MAX_INPUT_LENGTH} 个字符（当前 ${inputText.length} 字符）`)
            return
        }

        if (realtimeWsRef.current && realtimeSessionActive) {
            setGenerateError("当前已有流式任务，请先结束或取消")
            return
        }

        const taskId = createTaskId()
        const ws = new WebSocket(toWebSocketUrl())
        ws.binaryType = "arraybuffer"
        realtimeWsRef.current = ws
        realtimeSequenceRef.current = 1
        realtimeAudioChunksRef.current = []
        realtimeAudioBytesRef.current = 0

        setRealtimeTaskId(taskId)
        setRealtimeSentChunks(0)
        setRealtimeEvents([])
        setRealtimeSessionState("connecting")
        setGenerateError(null)
        setStreamStatus("正在连接")
        setStreamBytes(0)
        setIsGenerating(true)

        const cleanup = () => {
            if (realtimeWsRef.current === ws) {
                realtimeWsRef.current = null
            }
            ws.onopen = null
            ws.onmessage = null
            ws.onerror = null
            ws.onclose = null
        }

        const finish = () => {
            cleanup()
            setRealtimeSessionState("completed")
            setStreamStatus("合成完成")
            setIsGenerating(false)
            publishRealtimeAudio()
            try {
                ws.close(1000, "completed")
            } catch {
                // ignore close failures after terminal provider events
            }
        }

        const fail = (message: string) => {
            cleanup()
            setRealtimeSessionState("failed")
            setStreamStatus("任务失败")
            setIsGenerating(false)
            setGenerateError(`流式语音合成失败：${message}`)
            try {
                ws.close(1000, "client_error")
            } catch {
                // ignore close failures after terminal provider events
            }
        }

        ws.onopen = () => {
            appendRealtimeEvent("client", "StartSpeech", selectedModel)
            setStreamStatus("已连接，正在启动任务")
            sendRealtimeMessage(ws, {
                header: {
                    name: "StartSpeech",
                    message_id: createMessageId(),
                    task_id: taskId,
                },
                payload: {
                    model: selectedModel,
                    ...(voiceName && { voice: voiceTypes[voiceName] || voiceName }),
                    format: responseFormat,
                    sample_rate: parseInt(sampleRate),
                    channels: 1,
                    encoding: responseFormat === "pcm" ? "s16le" : undefined,
                    speed: speed[0],
                    text_type: "plain",
                    enable_timestamp: false,
                },
            })
        }

        ws.onmessage = (event) => {
            if (typeof event.data !== "string") {
                const data = event.data as ArrayBuffer | Blob
                realtimeAudioChunksRef.current.push(data)
                const byteLength = data instanceof Blob ? data.size : data.byteLength
                realtimeAudioBytesRef.current += byteLength
                setStreamBytes(realtimeAudioBytesRef.current)
                appendRealtimeEvent("audio", "BinaryAudio", `${byteLength} bytes`)
                return
            }

            let message: any
            try {
                message = JSON.parse(event.data)
            } catch {
                fail("服务端返回了无法解析的控制消息")
                return
            }

            const eventName = message?.header?.name
            appendRealtimeEvent("server", eventName || "UnknownEvent", message?.header?.status_message)

            switch (eventName) {
                case "SpeechStarted":
                    setRealtimeSessionState("started")
                    {
                        const sequence = sendRealtimeInput(ws, taskId, initialText)
                        setInputText((current) => current.trim() === initialText ? "" : current)
                        setStreamStatus(`已发送首段文本，可以继续输入下一段 · sequence=${sequence}`)
                    }
                    break
                case "SpeechAudioDelta":
                    setStreamStatus("正在接收音频")
                    break
                case "SpeechCompleted":
                    finish()
                    break
                case "SpeechCancelled":
                    cleanup()
                    setRealtimeSessionState("completed")
                    setStreamStatus("任务已取消")
                    setIsGenerating(false)
                    break
                case "SpeechFailed":
                    fail(message?.payload?.error?.message || message?.header?.status_message || "流式语音合成失败")
                    break
                default:
                    break
            }
        }

        ws.onerror = () => {
            appendRealtimeEvent("system", "WebSocketError")
            fail("WebSocket 连接错误")
        }

        ws.onclose = (event) => {
            if (realtimeWsRef.current === ws) {
                appendRealtimeEvent("system", "WebSocketClosed", `${event.code} ${event.reason || ""}`.trim())
                fail(`WebSocket 连接关闭: ${event.code} ${event.reason || ""}`.trim())
            }
        }
    }

    const sendRealtimeTextChunk = () => {
        const ws = realtimeWsRef.current
        if (!ws || realtimeSessionState !== "started") {
            setGenerateError("请先启动流式任务")
            return
        }

        const text = inputText.trim()
        if (!text) {
            setGenerateError("请输入要发送的文本片段")
            return
        }

        const sequence = sendRealtimeInput(ws, realtimeTaskId, text)
        setInputText("")
    }

    const finishRealtimeInput = () => {
        const ws = realtimeWsRef.current
        if (!ws || realtimeSessionState !== "started") {
            setGenerateError("当前没有可结束的流式任务")
            return
        }
        const sequence = realtimeSequenceRef.current++
        sendRealtimeMessage(ws, {
            header: {
                name: "FinishSpeech",
                message_id: createMessageId(),
                task_id: realtimeTaskId,
                sequence,
            },
            payload: {},
        })
        setRealtimeSessionState("finishing")
        setStreamStatus("文本输入已结束，等待音频完成")
        appendRealtimeEvent("client", "FinishSpeech", `sequence=${sequence}`)
    }

    const cancelRealtimeSession = () => {
        const ws = realtimeWsRef.current
        if (!ws || !["connecting", "started", "finishing"].includes(realtimeSessionState)) {
            return
        }
        if (realtimeSessionState === "connecting" || ws.readyState !== WebSocket.OPEN) {
            appendRealtimeEvent("system", "CancelBeforeStarted")
            setRealtimeSessionState("idle")
            setStreamStatus("任务已取消")
            setIsGenerating(false)
            closeRealtimeSocket(1000, "cancel_before_started")
            return
        }
        sendRealtimeMessage(ws, {
            header: {
                name: "CancelSpeech",
                message_id: createMessageId(),
                task_id: realtimeTaskId,
            },
            payload: {
                reason: "user_cancel",
            },
        })
        setRealtimeSessionState("cancelling")
        setStreamStatus("正在取消任务")
        appendRealtimeEvent("client", "CancelSpeech", "user_cancel")
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
            setStreamStatus(null)
            setStreamBytes(0)

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
        setStreamStatus(null)
        setStreamBytes(0)
        setRealtimeSessionState("idle")
        setRealtimeTaskId("")
        setRealtimeSentChunks(0)
        setRealtimeEvents([])
        realtimeSequenceRef.current = 1
        realtimeAudioChunksRef.current = []
        realtimeAudioBytesRef.current = 0
        closeRealtimeSocket(1000, "clear")

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
            closeRealtimeSocket(1000, "unmount")
            if (audioUrl) {
                URL.revokeObjectURL(audioUrl)
            }
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    return (
        <div className="flex h-screen flex-col">
            <TopBar title="语音合成 Playground" description={isRealtimeTts ? "流式文本转语音" : "文本转语音"} />
            <main className="flex flex-1 overflow-hidden">
                {/* 左侧主要内容区域 */}
                <div className="flex-1 overflow-auto p-6">
                    <div className="mx-auto max-w-6xl space-y-6">
                        {/* 输入区域 */}
                        <Card className="p-6">
                            <div className="mb-4 flex items-center gap-2">
                                <Volume2 className="h-5 w-5 text-primary" />
                                <h3 className="font-semibold">{isRealtimeTts ? "流式语音合成" : "语音合成"}</h3>
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
                                {isRealtimeTts ? (
                                    <>
                                        <Button
                                            onClick={startRealtimeSession}
                                            size="lg"
                                            disabled={!selectedModel || !inputText.trim() || inputText.length > MAX_INPUT_LENGTH || isPlaying || realtimeSessionActive}
                                            className="gap-2 px-6"
                                        >
                                            {realtimeSessionState === "connecting" ? (
                                                <Loader2 className="h-4 w-4 animate-spin" />
                                            ) : (
                                                <Play className="h-4 w-4" />
                                            )}
                                            发送首段并启动
                                        </Button>
                                        <Button
                                            onClick={sendRealtimeTextChunk}
                                            size="lg"
                                            disabled={realtimeSessionState !== "started" || !inputText.trim() || inputText.length > MAX_INPUT_LENGTH || isPlaying}
                                            className="gap-2 px-6"
                                        >
                                            <Send className="h-4 w-4" />
                                            发送片段
                                        </Button>
                                        <Button
                                            onClick={finishRealtimeInput}
                                            variant="outline"
                                            size="lg"
                                            disabled={realtimeSessionState !== "started" || realtimeSentChunks === 0 || isPlaying}
                                            className="gap-2 px-6"
                                        >
                                            <Square className="h-4 w-4" />
                                            结束输入
                                        </Button>
                                        <Button
                                            onClick={cancelRealtimeSession}
                                            variant="outline"
                                            size="lg"
                                            disabled={!["connecting", "started", "finishing"].includes(realtimeSessionState) || isPlaying}
                                            className="gap-2 px-6"
                                        >
                                            <XCircle className="h-4 w-4" />
                                            取消
                                        </Button>
                                    </>
                                ) : (
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
                                )}
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
                                    disabled={(!isRealtimeTts && isGenerating) || isPlaying}
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
                            {isRealtimeTts && streamStatus && !generateError && (
                                <div className="mt-4 rounded-lg border bg-muted/40 p-4">
                                    <p className="text-sm text-muted-foreground">
                                        {streamStatus}
                                        {streamBytes > 0 ? ` · ${streamBytes} bytes` : ""}
                                        {realtimeSentChunks > 0 ? ` · 已发送 ${realtimeSentChunks} 段` : ""}
                                    </p>
                                </div>
                            )}
                            {isRealtimeTts && realtimeEvents.length > 0 && (
                                <div className="mt-4 rounded-lg border bg-background">
                                    <div className="border-b px-4 py-2 text-sm font-medium">WebSocket 事件流</div>
                                    <div className="max-h-64 space-y-1 overflow-auto p-3 font-mono text-xs">
                                        {realtimeEvents.map((event) => (
                                            <div key={event.id} className="grid grid-cols-[72px_1fr] gap-3 rounded px-2 py-1 hover:bg-muted/50">
                                                <span
                                                    className={
                                                        event.direction === "client"
                                                            ? "text-blue-600"
                                                            : event.direction === "server"
                                                                ? "text-emerald-600"
                                                                : event.direction === "audio"
                                                                    ? "text-amber-600"
                                                                    : "text-muted-foreground"
                                                    }
                                                >
                                                    {event.direction === "client"
                                                        ? "Client ->"
                                                        : event.direction === "server"
                                                            ? "Server <-"
                                                            : event.direction === "audio"
                                                                ? "Audio <-"
                                                                : "System"}
                                                </span>
                                                <span className="min-w-0 truncate">
                                                    {event.label}
                                                    {event.detail ? ` · ${event.detail}` : ""}
                                                </span>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </Card>

                        {/* 使用说明 */}
                        <Card className="p-6">
                            <h4 className="mb-3 text-sm font-semibold">使用说明：</h4>
                            <ol className="space-y-1.5 text-sm text-muted-foreground list-decimal list-inside leading-relaxed">
                                {isRealtimeTts ? (
                                    <>
                                        <li>选择流式语音合成端点和模型</li>
                                        <li>先输入首段文本，点击"发送首段并启动"建立 WebSocket</li>
                                        <li>继续输入文本后可多次点击"发送片段"</li>
                                        <li>点击"结束输入"后合并已收到音频并播放</li>
                                    </>
                                ) : (
                                    <>
                                        <li>输入要转换为语音的文本</li>
                                        <li>选择模型和声音类型（如果可用）</li>
                                        <li>点击"生成语音"按钮</li>
                                        <li>生成完成后会自动播放音频</li>
                                    </>
                                )}
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
                                <div>
                                    <Label className="mb-2 block">端点</Label>
                                    <Select
                                        value={selectedEndpoint}
                                        onValueChange={handleEndpointChange}
                                        disabled={realtimeSessionActive || isPlaying}
                                    >
                                        <SelectTrigger>
                                            <SelectValue />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {TTS_ENDPOINT_OPTIONS.map((endpoint) => (
                                                <SelectItem key={endpoint.value} value={endpoint.value}>
                                                    <span>{endpoint.label}</span>
                                                    <span className="ml-2 font-mono text-xs text-muted-foreground">{endpoint.value}</span>
                                                </SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                </div>

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
                                        disabled={modelsLoading || !!modelsError || models.length === 0 || realtimeSessionActive}
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
                                            disabled={voiceLoading || Object.keys(voiceTypes).length === 0 || realtimeSessionActive}
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
                                    <Label className="mb-2 block">输出格式 ({isRealtimeTts ? "format" : "response_format"})</Label>
                                    <Select value={responseFormat} onValueChange={setResponseFormat} disabled={realtimeSessionActive}>
                                        <SelectTrigger>
                                            <SelectValue />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {formatOptions.map((format) => (
                                                <SelectItem key={format} value={format}>
                                                    {format}
                                                </SelectItem>
                                            ))}
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
                                        disabled={realtimeSessionActive}
                                    />
                                    <p className="mt-2 text-xs text-muted-foreground">范围: 0.25 - 4.0</p>
                                </div>

                                {/* 采样率 */}
                                <div>
                                    <Label className="mb-2 block">采样率 (sample_rate)</Label>
                                    <Select value={sampleRate} onValueChange={setSampleRate} disabled={realtimeSessionActive}>
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
