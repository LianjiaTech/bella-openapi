import { logger } from "@/lib/utils/logger"

interface PCMPlayerOptions {
  inputCodec?: "Int8" | "Int16" | "Int32" | "Float32"
  channels?: number
  sampleRate?: number
  bufferTime?: number
  flushTime?: number
  fftSize?: number
}

export class PCMPlayer {
  private audioContext: AudioContext | null = null
  private gainNode: GainNode | null = null
  private analyserNode: AnalyserNode | null = null
  private workletNode: AudioWorkletNode | null = null
  private pendingSamples: Float32Array[] = []
  private activeBufferSources = new Set<AudioBufferSourceNode>()
  private fallbackStartTime = 0
  private outputReady = false
  private buffered = false
  private destroyed = false
  private idleFlushTimer: ReturnType<typeof setTimeout> | null = null
  private readonly options: Required<PCMPlayerOptions>
  private readonly convertValue: number

  constructor(options: PCMPlayerOptions = {}) {
    this.options = {
      inputCodec: options.inputCodec ?? "Int16",
      channels: options.channels ?? 1,
      sampleRate: options.sampleRate ?? 24000,
      bufferTime: options.bufferTime ?? 0.2,
      flushTime: options.flushTime ?? 250,
      fftSize: options.fftSize ?? 2048,
    }
    this.convertValue = this.getConvertValue()
    this.createAudioGraph()
    void this.initializeOutput()
  }

  private getConvertValue(): number {
    const inputCodecs: Record<string, number> = {
      Int8: 128,
      Int16: 32768,
      Int32: 2147483648,
      Float32: 1,
    }
    const value = inputCodecs[this.options.inputCodec]
    if (!value) {
      throw new Error("wrong codec. please input one of these codecs: Int8, Int16, Int32, Float32")
    }
    return value
  }

  private createAudioGraph(): void {
    const AudioContextConstructor = window.AudioContext || (window as any).webkitAudioContext
    this.audioContext = new AudioContextConstructor({
      sampleRate: this.options.sampleRate,
      latencyHint: "interactive",
    })
    this.gainNode = this.audioContext.createGain()
    this.gainNode.gain.value = 0.5
    this.analyserNode = this.audioContext.createAnalyser()
    this.analyserNode.fftSize = this.options.fftSize
    this.analyserNode.connect(this.gainNode)
    this.gainNode.connect(this.audioContext.destination)
    this.fallbackStartTime = this.audioContext.currentTime + this.options.bufferTime
  }

  private async initializeOutput(): Promise<void> {
    const context = this.audioContext
    if (!context) return

    try {
      await context.audioWorklet.addModule("/audio/pcm-stream-processor.js")
      if (this.destroyed || !this.audioContext || !this.analyserNode) return
      this.workletNode = new AudioWorkletNode(context, "pcm-stream-player", {
        numberOfInputs: 0,
        numberOfOutputs: 1,
        outputChannelCount: [this.options.channels],
        processorOptions: {
          channels: this.options.channels,
          minimumBufferFrames: Math.round(context.sampleRate * this.options.bufferTime),
        },
      })
      this.workletNode.port.onmessage = event => {
        if (event.data?.type === "empty") this.buffered = false
      }
      this.workletNode.connect(this.analyserNode)
    } catch (error) {
      logger.warn("AudioWorklet 初始化失败，降级到 AudioBuffer 调度", error)
    }

    this.outputReady = true
    const samples = this.pendingSamples
    this.pendingSamples = []
    samples.forEach(chunk => this.deliver(chunk))
    if (samples.length > 0) this.scheduleIdleFlush()
  }

  feed(data: Uint8Array): void {
    const samples = this.resample(this.getFormattedValue(data))
    this.buffered = true
    if (this.outputReady) {
      this.deliver(samples)
    } else {
      this.pendingSamples.push(samples)
    }
    if (this.audioContext?.state === "suspended") {
      void this.audioContext.resume()
    }
    this.scheduleIdleFlush()
  }

  private deliver(samples: Float32Array): void {
    if (this.workletNode) {
      this.workletNode.port.postMessage({ type: "append", samples }, [samples.buffer])
      return
    }
    this.scheduleFallback(samples)
  }

  private getFormattedValue(data: Uint8Array): Float32Array {
    const float32 = new Float32Array(Math.floor(data.length / 2))
    for (let index = 0, outputIndex = 0; index + 1 < data.length; index += 2, outputIndex += 1) {
      const value = (data[index] & 0xff) | ((data[index + 1] & 0xff) << 8)
      float32[outputIndex] = value >= 0x8000 ? (value - 0x10000) / this.convertValue : value / this.convertValue
    }
    return float32
  }

  private resample(samples: Float32Array): Float32Array {
    const targetRate = this.audioContext?.sampleRate ?? this.options.sampleRate
    if (targetRate === this.options.sampleRate) return samples

    const channels = this.options.channels
    const sourceFrames = Math.floor(samples.length / channels)
    const targetFrames = Math.max(1, Math.round(sourceFrames * targetRate / this.options.sampleRate))
    const result = new Float32Array(targetFrames * channels)
    const ratio = this.options.sampleRate / targetRate
    for (let targetFrame = 0; targetFrame < targetFrames; targetFrame += 1) {
      const sourcePosition = targetFrame * ratio
      const lowerFrame = Math.min(sourceFrames - 1, Math.floor(sourcePosition))
      const upperFrame = Math.min(sourceFrames - 1, lowerFrame + 1)
      const fraction = sourcePosition - lowerFrame
      for (let channel = 0; channel < channels; channel += 1) {
        const lower = samples[lowerFrame * channels + channel]
        const upper = samples[upperFrame * channels + channel]
        result[targetFrame * channels + channel] = lower + (upper - lower) * fraction
      }
    }
    return result
  }

  private scheduleFallback(samples: Float32Array): void {
    const context = this.audioContext
    if (!context || !this.analyserNode || samples.length === 0) return
    const length = Math.floor(samples.length / this.options.channels)
    const audioBuffer = context.createBuffer(this.options.channels, length, context.sampleRate)
    for (let channel = 0; channel < this.options.channels; channel += 1) {
      const audioData = audioBuffer.getChannelData(channel)
      for (let frame = 0; frame < length; frame += 1) {
        audioData[frame] = samples[frame * this.options.channels + channel]
      }
    }
    if (this.fallbackStartTime < context.currentTime) {
      this.fallbackStartTime = context.currentTime + this.options.bufferTime
    }
    const source = context.createBufferSource()
    this.activeBufferSources.add(source)
    source.onended = () => {
      this.activeBufferSources.delete(source)
      if (this.activeBufferSources.size === 0) this.buffered = false
    }
    source.buffer = audioBuffer
    source.connect(this.analyserNode)
    source.start(this.fallbackStartTime)
    this.fallbackStartTime += audioBuffer.duration
  }

  private scheduleIdleFlush(): void {
    if (this.idleFlushTimer) clearTimeout(this.idleFlushTimer)
    this.idleFlushTimer = setTimeout(() => this.flush(), this.options.flushTime)
  }

  play(): void {
    if (this.audioContext?.state === "suspended") {
      void this.audioContext.resume()
    }
  }

  flush(): void {
    if (this.idleFlushTimer) {
      clearTimeout(this.idleFlushTimer)
      this.idleFlushTimer = null
    }
    this.workletNode?.port.postMessage({ type: "flush" })
  }

  stop(): void {
    if (this.idleFlushTimer) {
      clearTimeout(this.idleFlushTimer)
      this.idleFlushTimer = null
    }
    this.pendingSamples = []
    this.buffered = false
    this.workletNode?.port.postMessage({ type: "clear" })
    this.activeBufferSources.forEach(source => {
      try {
        source.stop()
        source.disconnect()
      } catch (error) {
        logger.error(error)
      }
    })
    this.activeBufferSources.clear()
    if (this.audioContext) {
      this.fallbackStartTime = this.audioContext.currentTime + this.options.bufferTime
    }
  }

  isPlaybackEnded(): boolean {
    return !this.buffered && this.pendingSamples.length === 0 && this.activeBufferSources.size === 0
  }

  destroy(): void {
    this.destroyed = true
    this.stop()
    this.workletNode?.disconnect()
    this.gainNode?.disconnect()
    this.analyserNode?.disconnect()
    if (this.audioContext && this.audioContext.state !== "closed") {
      void this.audioContext.close()
    }
    this.workletNode = null
    this.audioContext = null
    this.gainNode = null
    this.analyserNode = null
  }
}
