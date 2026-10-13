"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useSearchParams } from "next/navigation"
import { AudioLines, Bot, Loader2, Mic, MicOff, User } from "lucide-react"
import { Button } from "@/components/common/button"
import { Card } from "@/components/common/card"
import { Label } from "@/components/common/label"
import { Textarea } from "@/components/common/textarea"
import { AudioDeviceSelector } from "@/components/playground/audio/AudioDeviceSelector"
import { TopBar } from "@/components/layout/top-bar"
import { Combobox, type ComboboxOption } from "@/components/ui/combobox"
import { useAudioDevices } from "@/hooks/useAudioDevices"
import { getBaseURL } from "@/lib/api/client"
import { AudioRecorderBase, AudioRecorderEventType } from "@/lib/audio/AudioRecorderBase"
import { PCMPlayer } from "@/lib/audio/PCMPlayer"
import { useModelList } from "../hooks/useModelList"

const ENDPOINT = "/v1/live/sessions"
const DEFAULT_INSTRUCTIONS = "你是一个实时语音助手。请自然、简洁地回答用户，并优先使用中文。"

type SessionStatus = "idle" | "connecting" | "recording" | "closing"

interface LiveEvent {
    type?: string
    delta?: unknown
    audio?: string
    usage?: Record<string, unknown>
    error?: { code?: string; message?: string }
}

function bytesToBase64(bytes: Uint8Array): string {
    let binary = ""
    for (let index = 0; index < bytes.length; index += 1) {
        binary += String.fromCharCode(bytes[index])
    }
    return window.btoa(binary)
}

function base64ToBytes(value: string): Uint8Array {
    const binary = window.atob(value)
    const bytes = new Uint8Array(binary.length)
    for (let index = 0; index < binary.length; index += 1) {
        bytes[index] = binary.charCodeAt(index)
    }
    return bytes
}

function deltaText(delta: unknown): string {
    if (typeof delta === "string") return delta
    if (delta && typeof delta === "object" && "text" in delta) {
        return String((delta as { text?: unknown }).text ?? "")
    }
    return ""
}

export default function LivePlaygroundPage() {
    const { models, loading: modelsLoading, error: modelsError } = useModelList(ENDPOINT)
    const searchParams = useSearchParams()
    const [selectedModel, setSelectedModel] = useState("")
    const [instructions, setInstructions] = useState(DEFAULT_INSTRUCTIONS)
    const [status, setStatus] = useState<SessionStatus>("idle")
    const [error, setError] = useState("")
    const [inputTranscripts, setInputTranscripts] = useState<string[]>([])
    const [outputTranscripts, setOutputTranscripts] = useState<string[]>([])
    const [usage, setUsage] = useState<Record<string, unknown> | null>(null)

    const socketRef = useRef<WebSocket | null>(null)
    const recorderRef = useRef<AudioRecorderBase | null>(null)
    const playerRef = useRef<PCMPlayer | null>(null)
    const transcriptBufferRef = useRef<{ input: string[]; output: string[]; active: "input" | "output" | null }>({
        input: [],
        output: [],
        active: null,
    })
    const transcriptRenderTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

    const { audioSources, selectedSource, setSelectedSource } = useAudioDevices(setError)

    useEffect(() => {
        const modelFromUrl = searchParams.get("model")
        if (modelFromUrl) {
            setSelectedModel(modelFromUrl)
        } else if (!selectedModel && models.length > 0) {
            setSelectedModel(models[0].modelName)
        }
    }, [models, searchParams, selectedModel])

    const modelOptions: ComboboxOption[] = useMemo(() => models.map(model => ({
        value: model.modelName,
        label: model.modelName,
    })), [models])

    const displayedUsage = useMemo(() => {
        if (!usage) return null
        const visibleUsage = { ...usage }
        delete visibleUsage.final_usage_confirmed
        return visibleUsage
    }, [usage])

    const appendTranscript = useCallback((role: "input" | "output", text: string) => {
        if (!text) return
        const buffer = transcriptBufferRef.current
        const transcripts = buffer[role]
        if (buffer.active !== role || transcripts.length === 0) {
            transcripts.push(text)
        } else {
            transcripts[transcripts.length - 1] += text
        }
        buffer.active = role
        if (!transcriptRenderTimerRef.current) {
            transcriptRenderTimerRef.current = setTimeout(() => {
                transcriptRenderTimerRef.current = null
                setInputTranscripts([...transcriptBufferRef.current.input])
                setOutputTranscripts([...transcriptBufferRef.current.output])
            }, 50)
        }
    }, [])

    const cleanupMedia = useCallback(() => {
        recorderRef.current?.destroy()
        recorderRef.current = null
        playerRef.current?.destroy()
        playerRef.current = null
    }, [])

    const cleanupConnection = useCallback(() => {
        cleanupMedia()
        socketRef.current = null
        setStatus("idle")
    }, [cleanupMedia])

    useEffect(() => () => {
        if (transcriptRenderTimerRef.current) clearTimeout(transcriptRenderTimerRef.current)
        cleanupMedia()
        socketRef.current?.close()
    }, [cleanupMedia])

    const startRecorder = useCallback(async () => {
        if (!selectedSource) {
            throw new Error("请先选择麦克风设备")
        }

        const recorder = new AudioRecorderBase({
            deviceId: selectedSource,
            sampleRate: 24000,
            bufferSize: 4096,
            autoGainControl: true,
            echoCancellation: true,
            noiseSuppression: true,
        })
        recorder.on(AudioRecorderEventType.DATA, (data: Uint8Array) => {
            const socket = socketRef.current
            if (socket?.readyState === WebSocket.OPEN) {
                socket.send(JSON.stringify({
                    type: "session.input_audio.append",
                    audio: bytesToBase64(data),
                    mime_type: "audio/pcm;rate=24000",
                }))
            }
        })
        recorder.on(AudioRecorderEventType.ERROR, (message: string) => setError(message))
        recorderRef.current = recorder

        if (!await recorder.start()) {
            throw new Error("麦克风启动失败")
        }
        setStatus("recording")
    }, [selectedSource])

    const handleMessage = useCallback(async (raw: string) => {
        let event: LiveEvent
        try {
            event = JSON.parse(raw) as LiveEvent
        } catch {
            setError("服务端返回了无法解析的 Live 事件")
            return
        }

        switch (event.type) {
        case "session.started":
            try {
                await startRecorder()
            } catch (cause) {
                setError(cause instanceof Error ? cause.message : String(cause))
                socketRef.current?.send(JSON.stringify({ type: "session.close" }))
                setStatus("closing")
            }
            break
        case "session.input_transcript.delta":
            appendTranscript("input", deltaText(event.delta))
            break
        case "session.output_transcript.delta":
        case "session.output_text.delta":
            appendTranscript("output", deltaText(event.delta))
            break
        case "session.output_audio.delta":
            if (event.audio) playerRef.current?.feed(base64ToBytes(event.audio))
            break
        case "response.interrupted":
            playerRef.current?.stop()
            break
        case "response.done":
            transcriptBufferRef.current.active = null
            playerRef.current?.flush()
            break
        case "usage.updated":
            if (event.usage) setUsage(event.usage)
            break
        case "session.closed":
            if (event.usage) setUsage(event.usage)
            socketRef.current?.close()
            break
        case "error":
            setError(event.error?.message || event.error?.code || "Live 会话发生错误")
            break
        }
    }, [appendTranscript, startRecorder])

    const connect = useCallback(() => {
        if (!selectedModel) {
            setError("请先选择模型")
            return
        }
        if (!selectedSource) {
            setError("请先选择麦克风设备")
            return
        }

        setError("")
        setInputTranscripts([])
        setOutputTranscripts([])
        if (transcriptRenderTimerRef.current) {
            clearTimeout(transcriptRenderTimerRef.current)
            transcriptRenderTimerRef.current = null
        }
        transcriptBufferRef.current = { input: [], output: [], active: null }
        setUsage(null)
        setStatus("connecting")

        playerRef.current?.destroy()
        playerRef.current = new PCMPlayer({ inputCodec: "Int16", channels: 1, sampleRate: 24000 })
        playerRef.current.play()

        const base = new URL(getBaseURL(), window.location.origin)
        const url = new URL(ENDPOINT, base).toString().replace(/^http/, "ws")
        const socket = new WebSocket(url, "bella-live-v1")
        socketRef.current = socket

        socket.onopen = () => socket.send(JSON.stringify({
            type: "session.start",
            model: selectedModel,
            session: {
                model: selectedModel,
                instructions,
                audio: { output: { voice: "marin" } },
                delegation: { type: "client" },
                capabilities: {
                    websocket: true,
                    audio_input: true,
                    audio_output: true,
                    full_duplex: true,
                    interruption: true,
                },
            },
        }))
        socket.onmessage = event => void handleMessage(String(event.data))
        socket.onerror = () => {
            setError("Live WebSocket 连接失败")
            socket.close()
        }
        socket.onclose = cleanupConnection
    }, [cleanupConnection, handleMessage, instructions, selectedModel, selectedSource])

    const close = useCallback(async () => {
        if (status === "closing") return
        setStatus("closing")
        await recorderRef.current?.stop()
        recorderRef.current?.destroy()
        recorderRef.current = null

        const socket = socketRef.current
        if (socket?.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify({ type: "session.close" }))
        } else {
            socket?.close()
        }
    }, [status])

    const isActive = status !== "idle"

    return (
        <div className="flex h-screen flex-col">
            <TopBar title="Live 实时语音 Playground" />
            <main className="flex-1 overflow-auto p-6">
                <div className="mx-auto grid max-w-6xl gap-6 lg:grid-cols-[1fr_320px]">
                    <div className="space-y-6">
                        {error && <div className="rounded-lg border border-destructive bg-destructive/10 p-4 text-sm text-destructive">{error}</div>}

                        <Card className="p-6">
                            <div className="mb-4 flex items-center justify-between gap-4">
                                <div className="flex items-center gap-2">
                                    <AudioLines className={isActive ? "h-5 w-5 animate-pulse text-primary" : "h-5 w-5 text-muted-foreground"} />
                                    <h2 className="font-semibold">实时对话</h2>
                                    <span className="text-xs text-muted-foreground">
                                        {status === "idle" && "未连接"}
                                        {status === "connecting" && "连接中"}
                                        {status === "recording" && "正在收音"}
                                        {status === "closing" && "正在结算用量"}
                                    </span>
                                </div>
                                <Button onClick={isActive ? close : connect} disabled={status === "closing"}>
                                    {status === "connecting" || status === "closing" ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : isActive ? <MicOff className="mr-2 h-4 w-4" /> : <Mic className="mr-2 h-4 w-4" />}
                                    {status === "connecting" ? "取消连接" : status === "closing" ? "正在结束" : status === "recording" ? "结束会话" : "开始对话"}
                                </Button>
                            </div>

                            <div className="grid min-h-[360px] gap-4 md:grid-cols-2">
                                <div className="rounded-lg border bg-muted/30 p-4">
                                    <div className="mb-3 flex items-center gap-2 text-sm font-medium"><User className="h-4 w-4" />你的转写</div>
                                    {inputTranscripts.length > 0 ? (
                                        <div className="max-h-[320px] space-y-3 overflow-y-auto">
                                            {inputTranscripts.map((transcript, index) => <p key={index} className="whitespace-pre-wrap text-sm leading-6">{transcript}</p>)}
                                        </div>
                                    ) : (
                                        <p className="text-sm leading-6 text-muted-foreground">{status === "recording" ? "正在聆听…" : "开始会话后对着麦克风说话"}</p>
                                    )}
                                </div>
                                <div className="rounded-lg border bg-muted/30 p-4">
                                    <div className="mb-3 flex items-center gap-2 text-sm font-medium"><Bot className="h-4 w-4" />模型回复</div>
                                    {outputTranscripts.length > 0 ? (
                                        <div className="max-h-[320px] space-y-3 overflow-y-auto">
                                            {outputTranscripts.map((transcript, index) => <p key={index} className="whitespace-pre-wrap text-sm leading-6">{transcript}</p>)}
                                        </div>
                                    ) : (
                                        <p className="text-sm leading-6 text-muted-foreground">模型的实时转写会显示在这里，音频会自动播放</p>
                                    )}
                                </div>
                            </div>
                        </Card>

                        <Card className="p-6">
                            <h2 className="mb-3 font-semibold">会话用量</h2>
                            <pre className="overflow-auto rounded-md bg-muted p-4 text-xs">{displayedUsage ? JSON.stringify(displayedUsage, null, 2) : status === "closing" ? "正在结算会话用量" : "会话结束后展示最终用量"}</pre>
                        </Card>
                    </div>

                    <Card className="h-fit p-6">
                        <h2 className="mb-5 font-semibold">会话配置</h2>
                        <div className="space-y-5">
                            <div>
                                <Label className="mb-2 block">模型</Label>
                                <Combobox
                                    options={modelOptions}
                                    value={selectedModel}
                                    onValueChange={setSelectedModel}
                                    placeholder={modelsLoading ? "加载中…" : "选择或输入 Live 模型"}
                                    searchPlaceholder="搜索或输入模型名称"
                                    allowCustomValue
                                    disabled={isActive || modelsLoading}
                                />
                                {modelsError && <p className="mt-2 text-xs text-destructive">模型列表加载失败，仍可输入模型名称：{modelsError}</p>}
                            </div>
                            <div>
                                <Label className="mb-2 block">麦克风</Label>
                                <AudioDeviceSelector audioSources={audioSources} selectedSource={selectedSource} onSourceChange={setSelectedSource} disabled={isActive} className="w-full" />
                            </div>
                            <div>
                                <Label className="mb-2 block">系统提示词</Label>
                                <Textarea value={instructions} onChange={event => setInstructions(event.target.value)} disabled={isActive} rows={7} />
                            </div>
                            <p className="text-xs leading-5 text-muted-foreground">音频格式：PCM16、24 kHz、单声道。计费以最终会话用量为准。</p>
                        </div>
                    </Card>
                </div>
            </main>
        </div>
    )
}
