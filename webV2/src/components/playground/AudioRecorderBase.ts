
/**
 * 音频录制配置
 */
export interface AudioRecorderConfig {
  /** 音频设备ID */
  deviceId: string;
  /** 目标采样率，默认16000 */
  sampleRate?: number;
  /** 每次数据块大小，默认3200 */
  bufferSize?: number;
  /** 自动增益控制，默认true */
  autoGainControl?: boolean;
  /** 回声消除，默认true */
  echoCancellation?: boolean;
  /** 噪声抑制，默认true */
  noiseSuppression?: boolean;
}

/**
 * 音频录制事件类型
 */
export enum AudioRecorderEventType {
  /** 录音器就绪 */
  READY = 'ready',
  /** 录音开始 */
  START = 'start',
  /** 有新的音频数据 */
  DATA = 'data',
  /** 录音停止 */
  STOP = 'stop',
  /** 发生错误 */
  ERROR = 'error',
}

/**
 * 音频录制器基础类
 * 提供通用的音频录制功能，不包含特定业务逻辑
 */
export class AudioRecorderBase {
  // ScriptProcessor相关
  protected audioContext: AudioContext | null = null;
  protected stream: MediaStream | null = null;
  protected scriptProcessor: ScriptProcessorNode | null = null;
  protected sourceNode: MediaStreamAudioSourceNode | null = null;

  // 存储录音数据
  protected audioChunks: Int16Array[] = [];
  protected isRecordingActive = false;
  protected eventListeners: Map<AudioRecorderEventType, Function[]> = new Map();

  // 创建一个标志，用于跟踪是否已停止和释放过音频资源
  protected hasReleasedResources = false;

  protected config: AudioRecorderConfig;

  /**
   * 构造函数
   * @param config 音频录制配置
   */
  constructor(config: AudioRecorderConfig) {
    // 设置默认配置
    this.config = {
      deviceId: config.deviceId,
      sampleRate: config.sampleRate || 16000,
      bufferSize: config.bufferSize || 4096,
      autoGainControl: config.autoGainControl !== false,
      echoCancellation: config.echoCancellation !== false,
      noiseSuppression: config.noiseSuppression !== false
    };
  }

  /**
   * 初始化音频录制器
   * 获取媒体流并设置音频上下文
   */
  public async initialize(): Promise<boolean> {
    if (!this.config.deviceId) {
      this.emit(AudioRecorderEventType.ERROR, '请先选择音频设备');
      return false;
    }

    // 首先释放之前可能存在的资源
    this.releaseResources();

    try {
      // 设置音频约束
      const constraints = {
        audio: {
          deviceId: { exact: this.config.deviceId },
          echoCancellation: this.config.echoCancellation,
          noiseSuppression: this.config.noiseSuppression,
          autoGainControl: this.config.autoGainControl,
          sampleRate: this.config.sampleRate,
          channelCount: 1, // 强制单声道
        },
      };

      // 获取音频流
      this.stream = await navigator.mediaDevices.getUserMedia(constraints);
      console.log('获取到音频流');

      // 创建并初始化音频上下文
      this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)({
        sampleRate: this.config.sampleRate,
        latencyHint: "interactive"
      });

      // 获取实际采样率
      const actualSampleRate = this.audioContext.sampleRate;
      console.log('音频上下文已创建, 实际采样率:', actualSampleRate, 'Hz');

      // 创建ScriptProcessor节点 - 缓冲区大小调整为512，提供更平滑的录音体验
      // 单声道输入，单声道输出
      this.scriptProcessor = this.audioContext.createScriptProcessor(512, 1, 1);

      // 创建媒体流源节点
      this.sourceNode = this.audioContext.createMediaStreamSource(this.stream);

      // 设置处理函数
      this.scriptProcessor.onaudioprocess = this.handleAudioProcess.bind(this);

      // 还没有连接节点，录音还没有开始

      console.log('音频处理节点已初始化');

      // 重置标志和状态
      this.hasReleasedResources = false;
      this.audioChunks = [];

      // 触发录音器就绪事件
      this.emit(AudioRecorderEventType.READY, null);
      return true;
    } catch (error) {
      console.error("初始化音频录制器错误:", error);
      this.emit(AudioRecorderEventType.ERROR, `无法访问音频流: ${error instanceof Error ? error.message : String(error)}`);
      return false;
    }
  }

  /**
   * 释放资源
   */
  protected releaseResources(): void {
    console.log('释放音频资源...');

    // 断开ScriptProcessor连接
    if (this.scriptProcessor) {
      this.scriptProcessor.disconnect();
      this.scriptProcessor.onaudioprocess = null;
      this.scriptProcessor = null;
    }

    // 断开音频源连接
    if (this.sourceNode) {
      this.sourceNode.disconnect();
      this.sourceNode = null;
    }

    // 停止并释放音频流
    if (this.stream) {
      const tracks = this.stream.getTracks();
      tracks.forEach(track => {
        track.stop();
      });
      this.stream = null;
    }

    // 关闭音频上下文
    if (this.audioContext && this.audioContext.state !== 'closed') {
      // 不真正关闭上下文，只挂起，因为重复创建可能会导致问题
      try {
        if (this.audioContext.state === 'running') {
          this.audioContext.suspend();
        }
      } catch (error) {
        console.warn('暂停音频上下文失败:', error);
      }
    }

    // 重置音频块缓存
    this.audioChunks = [];
    this.isRecordingActive = false;
    this.hasReleasedResources = true;

    console.log('音频资源释放完成');
  }

  /**
   * 处理音频数据 - ScriptProcessor的audioprocess事件处理函数
   */
  protected handleAudioProcess(event: AudioProcessingEvent): void {
    if (!this.isRecordingActive) {
      return;
    }

    // 获取单声道浮点数据
    const audioData = event.inputBuffer.getChannelData(0);

    // 转换为Int16格式
    const pcmData = this.convertFloat32ToInt16(audioData);

    // 存储到音频块数组
    this.audioChunks.push(pcmData);

    // 创建一个Uint8Array来存储PCM数据，作为事件数据发送
    const pcmBytes = new Uint8Array(pcmData.buffer);

    // 触发数据事件
    this.emit(AudioRecorderEventType.DATA, pcmBytes);
  }

  /**
   * 将Float32Array转换为Int16Array (用于PCM数据)
   */
  protected convertFloat32ToInt16(float32Array: Float32Array): Int16Array {
    const int16Array = new Int16Array(float32Array.length);
    for (let i = 0; i < float32Array.length; i++) {
      // 将浮点数[-1,1]转换为16位整数[-32768,32767]
      // 限制范围并缩放到目标范围
      const s = Math.max(-1, Math.min(1, float32Array[i]));
      int16Array[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
    }
    return int16Array;
  }

  /**
   * 检查是否正在录音
   */
  public isRecording(): boolean {
    return this.isRecordingActive;
  }

  /**
   * 开始录音
   */
  public async start(): Promise<boolean> {
    if (this.isRecording()) {
      console.warn('录音已经在进行中');
      return false;
    }

    // 检查是否需要重新初始化
    if (!this.audioContext || !this.scriptProcessor || !this.sourceNode || !this.stream || this.hasReleasedResources) {
      try {
        await this.initialize();
      } catch (error) {
        console.error('初始化录音器失败:', error);
        this.emit(AudioRecorderEventType.ERROR, `初始化录音器失败: ${error}`);
        return false;
      }
    }

    try {
      // 连接音频处理节点
      if (this.sourceNode && this.scriptProcessor && this.audioContext) {
        // 在开始录音前，确保节点已连接
        this.sourceNode.connect(this.scriptProcessor);
        // 连接到输出，使录音生效（这是ScriptProcessor的要求）
        this.scriptProcessor.connect(this.audioContext.destination);

        // 确保音频上下文处于运行状态
        if (this.audioContext.state !== 'running') {
          await this.audioContext.resume();
        }

        // 设置录音标志并清空之前的数据
        this.isRecordingActive = true;
        this.audioChunks = [];

        // 触发录音开始事件
        this.emit(AudioRecorderEventType.START, null);
        console.log('录音已开始');
        return true;
      } else {
        console.error('无法启动录音：音频处理节点不完整');
        this.emit(AudioRecorderEventType.ERROR, '无法启动录音：音频处理节点不完整');
        return false;
      }
    } catch (error) {
      console.error('开始录音失败:', error);
      this.emit(AudioRecorderEventType.ERROR, `开始录音失败: ${error instanceof Error ? error.message : String(error)}`);
      return false;
    }
  }

  /**
   * 停止录音
   */
  public async stop(): Promise<void> {
    if (!this.isRecording()) {
      return;
    }

    try {
      // 停止录音过程
      this.isRecordingActive = false;

      // 断开连接，停止数据收集
      if (this.scriptProcessor && this.sourceNode) {
        this.sourceNode.disconnect(this.scriptProcessor);
        this.scriptProcessor.disconnect();
      }

      // 组合所有已收集的音频块
      const totalLength = this.audioChunks.reduce((length, chunk) => length + chunk.length, 0);
      const combinedPcmData = new Int16Array(totalLength);

      let offset = 0;
      for (const chunk of this.audioChunks) {
        combinedPcmData.set(chunk, offset);
        offset += chunk.length;
      }

      // 转换为Uint8Array用于传输
      const pcmByteData = new Uint8Array(combinedPcmData.buffer);

      console.log(`[音频调试] 录音完成，总块数: ${this.audioChunks.length}`);
      console.log(`[音频调试] 合并后的音频大小: ${pcmByteData.byteLength} 字节`);

      // 触发停止事件
      this.emit(AudioRecorderEventType.STOP, pcmByteData);
    } catch (error) {
      console.error('停止录音失败:', error);
      this.emit(AudioRecorderEventType.ERROR, `停止录音失败: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  /**
   * 注册事件监听器
   * @param event 事件类型
   * @param callback 回调函数
   */
  public on(event: AudioRecorderEventType, callback: Function): void {
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, []);
    }

    const callbacks = this.eventListeners.get(event)!;
    if (!callbacks.includes(callback)) {
      callbacks.push(callback);
    }
  }

  /**
   * 移除事件监听器
   * @param event 事件类型
   * @param callback 回调函数
   */
  public off(event: AudioRecorderEventType, callback: Function): void {
    if (!this.eventListeners.has(event)) {
      return;
    }

    const callbacks = this.eventListeners.get(event)!;
    const index = callbacks.indexOf(callback);
    if (index !== -1) {
      callbacks.splice(index, 1);
    }

    if (callbacks.length === 0) {
      this.eventListeners.delete(event);
    }
  }

  /**
   * 触发事件
   * @param event 事件类型
   * @param data 事件数据
   */
  protected emit(event: AudioRecorderEventType, data: any): void {
    if (this.eventListeners.has(event)) {
      const callbacks = this.eventListeners.get(event)!;
      for (const callback of callbacks) {
        try {
          callback(data);
        } catch (error) {
          console.error(`执行事件回调出错: ${error}`);
        }
      }
    }
  }
}
