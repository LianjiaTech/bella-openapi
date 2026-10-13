class PCMStreamProcessor extends AudioWorkletProcessor {
  constructor(options) {
    super()
    this.channels = options.processorOptions.channels
    this.minimumBufferSamples = options.processorOptions.minimumBufferFrames * this.channels
    this.chunks = new Map()
    this.readIndex = 0
    this.writeIndex = 0
    this.chunkOffset = 0
    this.bufferedSamples = 0
    this.started = false
    this.forceStart = false
    this.port.onmessage = event => this.handleMessage(event.data)
  }

  handleMessage(message) {
    if (message.type === "append" && message.samples?.length) {
      this.chunks.set(this.writeIndex, message.samples)
      this.writeIndex += 1
      this.bufferedSamples += message.samples.length
      return
    }
    if (message.type === "flush") {
      this.forceStart = true
      return
    }
    if (message.type === "clear") {
      this.chunks.clear()
      this.readIndex = 0
      this.writeIndex = 0
      this.chunkOffset = 0
      this.bufferedSamples = 0
      this.started = false
      this.forceStart = false
    }
  }

  process(inputs, outputs) {
    const output = outputs[0]
    output.forEach(channel => channel.fill(0))
    if (!this.started) {
      if (!this.forceStart && this.bufferedSamples < this.minimumBufferSamples) return true
      if (this.bufferedSamples === 0) return true
      this.started = true
      this.forceStart = false
    }

    const frameCount = output[0]?.length ?? 0
    for (let frame = 0; frame < frameCount; frame += 1) {
      if (this.bufferedSamples < this.channels) {
        this.started = false
        this.port.postMessage({ type: "empty" })
        break
      }
      for (let channel = 0; channel < this.channels; channel += 1) {
        let chunk = this.chunks.get(this.readIndex)
        while (chunk && this.chunkOffset >= chunk.length) {
          this.chunks.delete(this.readIndex)
          this.readIndex += 1
          this.chunkOffset = 0
          chunk = this.chunks.get(this.readIndex)
        }
        if (!chunk) {
          this.started = false
          this.bufferedSamples = 0
          this.port.postMessage({ type: "empty" })
          return true
        }
        output[channel][frame] = chunk[this.chunkOffset]
        this.chunkOffset += 1
        this.bufferedSamples -= 1
      }
    }
    return true
  }
}

registerProcessor("pcm-stream-player", PCMStreamProcessor)
