/**
 * PCM播放器类，用于播放TTS返回的音频
 */
export class PCMPlayer {
  private audioContext: AudioContext | null = null;
  private gainNode: GainNode | null = null;
  private analyserNode: AnalyserNode | null = null;
  private samples: Float32Array = new Float32Array();
  private interval: number = 0;
  private startTime: number = 0;
  private options: any;
  private convertValue: number = 32768;
  private activeBufferSources: AudioBufferSourceNode[] = [];

  constructor(options: any = {}) {
    const defaultOptions = {
      inputCodec: 'Int16',
      channels: 1,
      sampleRate: 24000,
      flushTime: 100,
      fftSize: 2048
    };

    this.options = Object.assign({}, defaultOptions, options);
    this.convertValue = this.getConvertValue();
    this.init();
  }

  private getConvertValue(): number {
    // 根据传入的目标编码位数选定转换数据所需要的基本值
    const inputCodecs: Record<string, number> = {
      'Int8': 128,
      'Int16': 32768,
      'Int32': 2147483648,
      'Float32': 1
    };

    if (!inputCodecs[this.options.inputCodec]) {
      throw new Error('wrong codec. please input one of these codecs: Int8, Int16, Int32, Float32');
    }

    return inputCodecs[this.options.inputCodec];
  }

  init() {
    if (this.audioContext) return;

    // 初始化音频上下文
    this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
    this.gainNode = this.audioContext.createGain();
    this.gainNode.gain.value = 0.5; // 设置音量为中等大小
    this.gainNode.connect(this.audioContext.destination);

    // 创建分析器节点
    this.analyserNode = this.audioContext.createAnalyser();
    this.analyserNode.fftSize = this.options.fftSize;

    this.startTime = this.audioContext.currentTime;

    // 设置定时刷新
    this.interval = window.setInterval(() => this.flush(), this.options.flushTime);
  }

  feed(data: Uint8Array) {
    // 获取格式化后的buffer
    const formattedData = this.getFormattedValue(data);

    // 开始拷贝buffer数据
    const tmp = new Float32Array(this.samples.length + formattedData.length);
    tmp.set(this.samples, 0);
    tmp.set(formattedData, this.samples.length);
    this.samples = tmp;

    // 如果音频上下文被挂起，则恢复并刷新
    if (this.audioContext?.state === "suspended") {
      this.audioContext.resume().then(() => {
        this.flush();
      });
    }
  }

  private getFormattedValue(data: Uint8Array): Float32Array {
    // 创建Float32Array来存储转换后的数据
    const float32 = new Float32Array(data.length / 2);

    // 将Int16数据转换为Float32
    for (let i = 0, j = 0; i < data.length; i += 2, j++) {
      // 将两个字节组合成一个16位整数
      const value = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8);
      // 转换为有符号值并归一化到[-1,1]范围
      float32[j] = value >= 0x8000 ? (value - 0x10000) / this.convertValue : value / this.convertValue;
    }

    return float32;
  }

  play() {
    if (!this.audioContext) {
      this.init();
    }

    if (this.audioContext?.state === 'suspended') {
      this.audioContext.resume();
    }
  }

  flush() {
    if (!this.samples.length || !this.audioContext) return;

    // 创建缓冲源
    const bufferSource = this.audioContext.createBufferSource();
    this.activeBufferSources.push(bufferSource);

    // 播放结束后从列表中移除
    bufferSource.onended = () => {
      const index = this.activeBufferSources.indexOf(bufferSource);
      if (index > -1) {
        this.activeBufferSources.splice(index, 1);
      }
    };

    // 计算样本长度并创建音频缓冲区
    const length = this.samples.length / this.options.channels;
    const audioBuffer = this.audioContext.createBuffer(
      this.options.channels,
      length,
      this.options.sampleRate
    );

    // 填充音频数据
    for (let channel = 0; channel < this.options.channels; channel++) {
      const audioData = audioBuffer.getChannelData(channel);
      let offset = channel;
      let decrement = 50;

      for (let i = 0; i < length; i++) {
        audioData[i] = this.samples[offset];

        // 淡入效果
        if (i < 50) {
          audioData[i] = (audioData[i] * i) / 50;
        }

        // 淡出效果
        if (i >= (length - 51)) {
          audioData[i] = (audioData[i] * decrement--) / 50;
        }

        offset += this.options.channels;
      }
    }

    // 如果开始时间小于当前时间，则更新开始时间
    if (this.startTime < this.audioContext.currentTime) {
      this.startTime = this.audioContext.currentTime;
    }

    // 设置缓冲区并连接到增益节点
    bufferSource.buffer = audioBuffer;
    bufferSource.connect(this.gainNode!);

    if (this.analyserNode) {
      bufferSource.connect(this.analyserNode);
    }

    // 开始播放
    bufferSource.start(this.startTime);
    this.startTime += audioBuffer.duration;

    // 清空样本缓冲区
    this.samples = new Float32Array();
  }

  stop() {
    // 停止所有活跃的缓冲源
    this.activeBufferSources.forEach(source => {
      try {
        source.stop();
        source.disconnect();
      } catch(e) {
        console.error(e);
      }
    });

    // 清空数组
    this.activeBufferSources = [];

    // 重置开始时间
    if (this.audioContext) {
      this.startTime = this.audioContext.currentTime;
    }

    // 清空样本
    this.samples = new Float32Array();
  }

  /**
   * 检查是否有音频正在播放
   * @returns 如果没有正在播放的音频，返回 true
   */
  isPlaybackEnded(): boolean {
    return this.activeBufferSources.length === 0 && this.samples.length === 0;
  }

  destroy() {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = 0;
    }

    this.stop();
    this.samples = new Float32Array();
  }
}
