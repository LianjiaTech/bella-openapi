import {AudioRecorderBase, AudioRecorderConfig, AudioRecorderEventType} from "./AudioRecorderBase";
import {WebSocketConfig, WebSocketManager} from "./WebSocketManager";
import {v4 as uuidv4} from 'uuid';

/**
 * 实时对话录音器配置
 */
export interface RealtimeAudioRecorderConfig extends AudioRecorderConfig {
  /** WebSocket配置 */
  webSocketConfig: {
    /** WebSocket URL */
    url: string;
    /** WebSocket重连尝试次数 */
    reconnectAttempts?: number;
    /** WebSocket重连间隔(ms) */
    reconnectInterval?: number;
    /** 连接超时时间(ms) */
    timeoutMs?: number;
  };
  /** 语音类型 */
  voiceType?: string;
  /** 模式：transcription - 转录、 chat - 对话 */
  mode: 'transcription' | 'chat';
  /** 提示词 */
  system_prompt?: string;
}

/**
 * 实时对话录音器事件类型
 */
export enum RealtimeAudioRecorderEventType {
  /** ASR开始 */
  ASR_START = 'asr_start',
  /** ASR已连接 */
  ASR_CONNECTED = 'asr_connected',
  /** ASR转写更新 */
  TRANSCRIPTION_UPDATE = 'transcription_update',
  /** ASR说话开始 */
  SPEECH_START = 'speech_start',
  /** ASR说话结束 */
  SPEECH_END = 'speech_end',
  /** LLM回复更新 */
  LLM_RESPONSE_UPDATE = 'llm_response_update',
  /** TTS开始 */
  TTS_START = 'tts_start',
  /** TTS首次音频数据 */
  TTS_TTFT = 'tts_ttft',
  /** TTS音频数据 */
  TTS_AUDIO_DATA = 'tts_audio_data',
  /** TTS结束 */
  TTS_END = 'tts_end',
  /** ASR关闭 */
  ASR_CLOSED = 'asr_closed',
  /** 发生错误 */
  ERROR = 'error',
}

interface Sentence {
  content: string;
  isCompleted: boolean;
}

/**
 * 实时对话录音器类
 * 负责处理实时语音对话的录音和通信
 */
export class RealtimeAudioRecorder {
  private audioRecorder: AudioRecorderBase;
  private webSocketManager: WebSocketManager;
  private eventListeners: Map<RealtimeAudioRecorderEventType, Function[]> = new Map();
  private config: RealtimeAudioRecorderConfig;
  private isActive = false;

  // 转录句子相关
  private sentences: Sentence[] = [];
  private hasActiveSentence: boolean = false;
  private completedText: string = '';

  // LLM回复缓存
  private llmResponse: string = '';

  // TTS状态
  private hasTTSStarted: boolean = false;

  // 静音检测帧
  private lastVoiceQuietStartFrame = -1;

  /**
   * 更新配置
   * @param deviceId 设备ID
   * @param voiceType 语音类型
   * @param system_prompt 系统提示词
   */
  public updateConfig(deviceId?: string, voiceType?: string, system_prompt?: string): void {
    if (deviceId) {
      this.config.deviceId = deviceId;
    }
    if (voiceType !== undefined) {
      this.config.voiceType = voiceType;
    }
    this.config.system_prompt = system_prompt;
  }

  /**
   * 构造函数
   * @param config 实时对话录音器配置
   */
  constructor(config: RealtimeAudioRecorderConfig) {
    this.config = config;

    // 创建音频录制器
    this.audioRecorder = new AudioRecorderBase(config);

    // 设置音频录制器事件监听
    this.setupAudioRecorderListeners();

    // 创建WebSocket管理器
    const wsConfig: WebSocketConfig = {
      url: config.webSocketConfig.url,
      appkey: 'default',
      reconnectAttempts: config.webSocketConfig.reconnectAttempts,
      reconnectInterval: config.webSocketConfig.reconnectInterval,
      timeoutMs: config.webSocketConfig.timeoutMs
    };
    this.webSocketManager = new WebSocketManager(wsConfig);

    // 设置WebSocket事件监听
    this.setupWebSocketListeners();
  }

  /**
   * 开始实时对话
   */
  public async start(): Promise<boolean> {
    try {
      if (this.isActive) {
        console.warn('实时对话已经开始');
        return true;
      }

      // 初始化音频录制器
      const started = await this.audioRecorder.initialize();
      if (!started) {
        throw new Error('无法初始化录音设备');
      }

      // 准备转写任务参数
      const params: Record<string, any> = {
        format: "pcm",
        sample_rate: this.config.sampleRate || 16000,
        enable_intermediate_result: true,
        enable_punctuation_prediction: true,
        enable_inverse_text_normalization: true
      };
      if(this.config.mode === 'chat') {
        if(this.config.system_prompt) {
          params.llm_option = {
            main: {
              sys_prompt: this.config.system_prompt
            }
          };
          if (this.config.voiceType) {
            params.tts_option = {
              sample_rate: 24000,
              voice: this.config.voiceType
            };
          }
        }
      }

      // 连接WebSocket
      const connected = await this.webSocketManager.connect();
      if (!connected) {
        throw new Error('无法连接到WebSocket服务器');
      }

      // 发送启动转写任务
      const startSuccess = this.webSocketManager.sendJson({
        header: {
          message_id: uuidv4(),
          task_id: uuidv4(),
          namespace: "SpeechTranscriber",
          name: "StartTranscription",
          appkey: "default"
        },
        payload: params,
        context: {
          sdk: {
            name: "bella-openapi-web",
            version: "1.0.0",
            language: "javascript"
          }
        }
      });

      if (!startSuccess) {
        await this.webSocketManager.close();
        throw new Error('无法发送启动转写任务');
      }

      // 开始录音
      const recordingStarted = await this.audioRecorder.start();
      if (!recordingStarted) {
        await this.webSocketManager.close();
        throw new Error('无法启动录音');
      }

      this.isActive = true;
      this.emit(RealtimeAudioRecorderEventType.ASR_START, null);

      // 在录制开始后，发送一些静音帧以触发服务器的VOICE_QUIET事件
      setTimeout(() => {
        this.sendSilentFrames();
      }, 2000);

      return true;
    } catch (error) {
      console.error('开始实时对话错误:', error);
      this.emit(RealtimeAudioRecorderEventType.ERROR, `开始实时对话错误: ${error instanceof Error ? error.message : String(error)}`);
      return false;
    }
  }

  /**
   * 停止实时对话
   */
  public async stop(): Promise<void> {
    try {
      if (!this.isActive) {
        return;
      }

      // 重置TTS状态
      this.hasTTSStarted = false;

      // 关闭WebSocket连接
      await this.webSocketManager.close();

      // 停止录音
      await this.audioRecorder.stop();

      // 重置状态
      this.sentences = [];
      this.hasActiveSentence = false;
      this.completedText = '';
      this.llmResponse = '';

      this.isActive = false;
      this.emit(RealtimeAudioRecorderEventType.ASR_CLOSED, null);
    } catch (error) {
      console.error('停止实时对话错误:', error);
      this.emit(RealtimeAudioRecorderEventType.ERROR, `停止实时对话错误: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  /**
   * 检查是否正在进行实时对话
   */
  public isRecording(): boolean {
    return this.isActive && this.audioRecorder.isRecording();
  }

  /**
   * 设置音频录制器事件监听
   */
  private setupAudioRecorderListeners(): void {
    // 处理音频数据
    this.audioRecorder.on(AudioRecorderEventType.DATA, (data: Uint8Array) => {
      if (this.webSocketManager.isConnected()) {
        this.webSocketManager.sendBinary(data);
      }
    });

    // 处理错误
    this.audioRecorder.on(AudioRecorderEventType.ERROR, (error: string) => {
      this.emit(RealtimeAudioRecorderEventType.ERROR, error);
    });

    // 处理停止事件
    this.audioRecorder.on(AudioRecorderEventType.STOP, () => {
      this.isActive = false;
    });
  }

  /**
   * 设置WebSocket事件监听
   */
  private setupWebSocketListeners(): void {
    // 连接打开
    this.webSocketManager.on('open', (msg: string) => {
      console.log('WebSocket连接已建立');
      this.emit(RealtimeAudioRecorderEventType.ASR_CONNECTED, msg);
    });

    // 处理服务器消息
    this.webSocketManager.on('TranscriptionStarted', (msg: any) => {
      console.log('ASR服务已启动');
    });

    // 转写结果更新
    this.webSocketManager.on('TranscriptionResultChanged', (msg: any) => {
      try {
        const message = typeof msg === 'string' ? JSON.parse(msg) : msg;
        const result = message.payload.result || '';

        // 只有当有当前活动句子时才更新
        if (this.hasActiveSentence && this.sentences.length > 0) {
          // 当前活动句子总是最后一个
          const lastIndex = this.sentences.length - 1;
          if (!this.sentences[lastIndex].isCompleted) {
            // 更新当前句子的内容
            this.sentences[lastIndex].content = result;

            // 发送更新的转录文本
            this.emitTranscriptionUpdate();
          }
        }
      } catch (e) {
        console.error('解析转写结果失败', e);
      }
    });

    // 句子开始
    this.webSocketManager.on('SentenceBegin', (msg: any) => {

      console.log('检测到开始说话');

      this.hasTTSStarted = false;

      // 添加新句子到句子列表末尾
      this.sentences.push({
        content: '',
        isCompleted: false
      });

      // 标记有活动句子
      this.hasActiveSentence = true;

      this.emit(RealtimeAudioRecorderEventType.SPEECH_START, msg);
    });

    // 句子结束
    this.webSocketManager.on('SentenceEnd', (msg: any) => {
      try {
        const message = typeof msg === 'string' ? JSON.parse(msg) : msg;
        const finalText = message.payload.result || '';

        // 如果有活动句子，将最后一个句子标记为已完成
        if (this.hasActiveSentence && this.sentences.length > 0) {
          const lastIndex = this.sentences.length - 1;

          // 更新最终内容并标记为已完成
          this.sentences[lastIndex].content = finalText;
          this.sentences[lastIndex].isCompleted = true;

          // 更新已完成文本（所有已完成句子的累积）
          this.completedText = this.sentences
            .filter(s => s.isCompleted)
            .map(s => s.content)
            .join(' ');
        }

        // 标记没有活动句子
        this.hasActiveSentence = false;

        // 发送完整的累积转录结果
        this.emitTranscriptionUpdate();

        // 发送句子结束事件
        this.emit(RealtimeAudioRecorderEventType.SPEECH_END, finalText);
      } catch (e) {
        console.error('解析句子结束结果失败', e);
      }
    });

    //VAD
    this.webSocketManager.on('VOICE_QUIET', (msg: any) => {
      try {
        const message = typeof msg === 'string' ? JSON.parse(msg) : msg;
        const frame = message.payload.start_frame;
        if(frame === this.lastVoiceQuietStartFrame) {
          return;
        }
        this.lastVoiceQuietStartFrame = frame;
        console.log("检测到静音");
      } catch (e) {
        console.error('解析静音检测失败', e);
      }
    });

    // LLM聊天开始
    this.webSocketManager.on('LLM_CHAT_BEGIN', (msg: any) => {
      console.log('LLM开始回复');

      // 添加新的LLM回复到回复列表
      this.llmResponse = '';
    });

    // LLM聊天回复内容
    this.webSocketManager.on('LLM_CHAT_DELTA', (msg: any) => {

      const message = typeof msg === 'string' ? JSON.parse(msg) : msg;
      const content = message.payload.data || '';
      if (this.hasTTSStarted) {
        this.emit(RealtimeAudioRecorderEventType.LLM_RESPONSE_UPDATE, content);
      } else {
        this.llmResponse = content;
      }
    });

    // LLM聊天回复结束
    this.webSocketManager.on('LLM_CHAT_END', (msg: any) => {
      console.log('LLM回复结束');
    });

    // LLM聊天回复取消
    this.webSocketManager.on('LLM_CHAT_CANCELLED', (msg: any) => {
      console.log('LLM回复取消');
      this.hasTTSStarted = false;
      // 清除所有未刷新的回复
      this.llmResponse = '';
    });

    // TTS开始
    this.webSocketManager.on('TTS_BEGIN', (msg: any) => {
      console.log('TTS开始');
      this.emit(RealtimeAudioRecorderEventType.TTS_START, msg);
    });

    // TTS首次音频数据就绪时间
    this.webSocketManager.on('TTS_TTFT', (msg: any) => {
      console.log('TTS首次音频就绪');

      // 重置转录状态
      this.resetTranscription();

      // 发送TTFT事件
      this.emit(RealtimeAudioRecorderEventType.TTS_TTFT, msg);

      // 如果有LLM响应，现在发送
      if (this.llmResponse.length > 0) {
        this.emitLLMResponseUpdate();
      }

      // 标记TTS开始
      this.hasTTSStarted = true;

    });

    // TTS音频数据
    this.webSocketManager.on('binary', async (blob: Blob) => {
      if (!this.hasTTSStarted) return;
      try {
        const arrayBuffer = await blob.arrayBuffer();
        const audioArray = new Uint8Array(arrayBuffer);

        this.emit(RealtimeAudioRecorderEventType.TTS_AUDIO_DATA, audioArray);
      } catch (e) {
        console.error('处理TTS音频数据失败', e);
      }
    });

    // TTS结束
    this.webSocketManager.on('TTS_END', (msg: any) => {
      console.log('TTS结束');
      // 重置音频和LLM响应状态，为下一轮做准备
      this.emit(RealtimeAudioRecorderEventType.TTS_END, msg);
    });

    // 重置TTS状态
    this.hasTTSStarted = false;

  }

  /**
   * 发送累积的转录更新
   */
  private emitTranscriptionUpdate(): void {
    // 构建完整的转录文本：已完成的句子 + 当前未完成的句子
    let fullTranscription = this.completedText;

    // 如果有当前未完成的句子，添加它
    if (this.hasActiveSentence && this.sentences.length > 0) {
      const lastIndex = this.sentences.length - 1;
      const currentSentence = this.sentences[lastIndex];

      if (!currentSentence.isCompleted && currentSentence.content) {
        if (fullTranscription) {
          fullTranscription += ' ' + currentSentence.content;
        } else {
          fullTranscription = currentSentence.content;
        }
      }
    }

    // 发送更新的转录文本
    this.emit(RealtimeAudioRecorderEventType.TRANSCRIPTION_UPDATE, fullTranscription);
  }

  /**
   * 发送累积的LLM回复更新
   */
  private emitLLMResponseUpdate(): void {
    // 发送累积的回复
    this.emit(RealtimeAudioRecorderEventType.LLM_RESPONSE_UPDATE, this.llmResponse);
    this.llmResponse = '';
  }

  /**
   * 重置转录状态
   */
  private resetTranscription(): void {
    this.sentences = [];
    this.completedText = '';
  }

  /**
   * 发送静音帧以触发服务器的VOICE_QUIET事件
   */
  private async sendSilentFrames(): Promise<void> {
    if (!this.webSocketManager.isConnected()) return;

    // 创建20帧静音数据（全0的字节数组）
    const bufferSize = this.config.bufferSize || 3200;
    const silentFrame = new Uint8Array(bufferSize);

    for (let i = 0; i < 20; i++) {
      await new Promise(resolve => setTimeout(resolve, 100)); // 每帧间隔100ms
      this.webSocketManager.sendBinary(silentFrame);
    }
  }

  /**
   * 注册事件监听器
   * @param event 事件类型
   * @param callback 回调函数
   */
  public on(event: RealtimeAudioRecorderEventType, callback: Function): void {
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
  public off(event: RealtimeAudioRecorderEventType, callback: Function): void {
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
  private emit(event: RealtimeAudioRecorderEventType, data: any): void {
    if (this.eventListeners.has(event)) {
      const callbacks = this.eventListeners.get(event)!;
      for (const callback of callbacks) {
        try {
          callback(data);
        } catch (error) {
          console.error(`执行 ${event} 事件处理器错误:`, error);
        }
      }
    }
  }

  /**
   * 销毁资源
   */
  public destroy(): void {
    // 停止录音
    this.stop();
    // 清除所有事件监听
    this.eventListeners.clear();
  }
}
