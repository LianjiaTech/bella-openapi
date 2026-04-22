import { AudioRecorderBase, AudioRecorderConfig, AudioRecorderEventType } from "./AudioRecorderBase";
import { logger } from "@/lib/utils/logger";

/**
 * 一句话转录录音器配置
 */
export interface FlashAudioRecorderConfig extends AudioRecorderConfig {
  url: string;
  /** 模型名称 */
  model: string;
  /** 录音最大时长(ms)，默认60000ms (60秒) */
  maxDuration?: number;
  /** 最大句子静默时间(ms)，默认3000ms */
  maxSentenceSilence?: number;
}

/**
 * 一句话转录录音器事件类型
 */
export enum FlashAudioRecorderEventType {
  /** 录音开始 */
  RECORDING_START = 'recording_start',
  /** 录音完成 */
  RECORDING_COMPLETE = 'recording_complete',
  /** 转录开始 */
  TRANSCRIPTION_START = 'transcription_start',
  /** 转录完成 */
  TRANSCRIPTION_COMPLETE = 'transcription_complete',
  /** 出现错误 */
  ERROR = 'error',
}

/**
 * 句子转录结果类型
 */
export interface FlashTranscriptionSentence {
  text: string;
  begin_time: number;
  end_time: number;
}

/**
 * 转录响应类型
 */
export interface FlashTranscriptionResponse {
  user: string | null;
  task_id: string;
  flash_result: {
    duration: number;
    sentences: FlashTranscriptionSentence[];
  };
}

/**
 * 一句话转录录音器类
 */
export class FlashAudioRecorder {
  private audioRecorder: AudioRecorderBase;
  private config: FlashAudioRecorderConfig;
  private eventListeners: Map<FlashAudioRecorderEventType, Function[]> = new Map();
  private audioBlob: Blob | null = null;
  private isActive = false;
  private recordingTimeout: NodeJS.Timeout | null = null;

  /**
   * 构造函数
   * @param config 一句话转录录音器配置
   */
  constructor(config: FlashAudioRecorderConfig) {
    this.config = {
      ...config,
      maxDuration: config.maxDuration || 60000, // 默认最大录音时长60秒
    };

    // 初始化音频录制器
    // bufferSize 使用较小值以减少延迟，适合实时场景
    this.audioRecorder = new AudioRecorderBase({
      ...this.config,
      bufferSize: 2048,
    });

    this.audioBlob = null;
    this.setupAudioRecorderListeners();
  }

  /**
   * 设置事件监听
   * @param event 事件类型
   * @param callback 回调函数
   */
  public on(event: FlashAudioRecorderEventType, callback: Function): void {
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, []);
    }
    this.eventListeners.get(event)?.push(callback);
  }

  /**
   * 触发事件
   * @param event 事件类型
   * @param data 事件数据
   */
  private emit(event: FlashAudioRecorderEventType, data: any): void {
    if (this.eventListeners.has(event)) {
      this.eventListeners.get(event)?.forEach(callback => {
        try {
          callback(data);
        } catch (error) {
          logger.error(`执行事件 ${event} 的回调函数时出错:`, error);
        }
      });
    }
  }

  /**
   * 设置音频录制器事件监听
   */
  private setupAudioRecorderListeners(): void {
    // 录制开始
    this.audioRecorder.on(AudioRecorderEventType.START, () => {
      // 清空音频数据
      this.audioBlob = null;
      this.emit(FlashAudioRecorderEventType.RECORDING_START, null);
    });

    // 录制错误
    this.audioRecorder.on(AudioRecorderEventType.ERROR, (error: string) => {
      logger.error('录音错误:', error);
      this.emit(FlashAudioRecorderEventType.ERROR, error);
    });

    // 录制停止 - 使用 AudioRecorderBase 返回的完整数据
    this.audioRecorder.on(AudioRecorderEventType.STOP, async (pcmData: Uint8Array) => {
      // 直接使用 AudioRecorderBase 返回的合并后的 PCM 数据
      if (pcmData && pcmData.length > 0) {
        this.audioBlob = new Blob([pcmData.buffer as ArrayBuffer], { type: 'audio/pcm' });
        this.emit(FlashAudioRecorderEventType.RECORDING_COMPLETE, this.audioBlob);

        try {
          await this.transcribe();
        } catch (error) {
          this.emit(FlashAudioRecorderEventType.ERROR, `转录请求失败: ${error}`);
        }
      } else {
        logger.error('没有收集到音频数据');
        this.emit(FlashAudioRecorderEventType.ERROR, '没有收集到音频数据');
      }
    });
  }

  /**
   * 开始录音
   */
  public async start(): Promise<boolean> {
    if (!this.audioRecorder) {
      throw new Error('音频录制器未初始化');
    }

    if (this.isActive) {
      throw new Error('已经在录制中');
    }

    this.audioBlob = null;
    this.isActive = true;

    // 设置最大录音时长
    this.recordingTimeout = setTimeout(() => {
      this.stop();
    }, this.config.maxDuration);

    try {
      // 启动录音
      const success = await this.audioRecorder.start();
      if (!success) {
        this.isActive = false;
        return false;
      }

      return true;
    } catch (error) {
      logger.error('开始录音失败:', error);
      this.isActive = false;
      this.emit(FlashAudioRecorderEventType.ERROR, `开始录音失败: ${error instanceof Error ? error.message : String(error)}`);
      return false;
    }
  }

  /**
   * 停止录音
   */
  public async stop(): Promise<void> {
    if (!this.isActive) {
      return;
    }

    try {
      // 清除超时定时器
      if (this.recordingTimeout) {
        clearTimeout(this.recordingTimeout);
        this.recordingTimeout = null;
      }

      this.isActive = false;

      // 停止录音 - AudioRecorderBase 会在 STOP 事件中返回完整数据
      await this.audioRecorder.stop();
    } catch (error) {
      logger.error('停止录音失败:', error);
      this.emit(FlashAudioRecorderEventType.ERROR, `停止录音失败: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  /**
   * 发送转录请求
   */
  public async transcribe(): Promise<FlashTranscriptionResponse> {
    // 如果没有音频数据，无法转录
    if (!this.audioBlob) {
      const error = '没有可用的音频数据用于转录';
      this.emit(FlashAudioRecorderEventType.ERROR, error);
      throw new Error(error);
    }

    try {
      // 构建请求头
      const headers: Record<string, string> = {
        'Content-Type': 'audio/pcm',
        'model': this.config.model,
        'format': 'pcm',
        'sample_rate': String(this.config.sampleRate || 16000),
      };

      // 添加可选参数
      if (this.config.maxSentenceSilence !== undefined) {
        headers['max_sentence_silence'] = String(this.config.maxSentenceSilence);
      }

      // 发送请求
      const response = await fetch(this.config.url, {
        method: 'POST',
        headers,
        body: this.audioBlob,
        credentials: 'include' // 确保cookie被发送
      });

      if (!response.ok) {
        const errorText = await response.text();
        logger.error(`服务器响应错误 ${response.status}:`, errorText);
        throw new Error(`HTTP 错误 ${response.status}: ${errorText}`);
      }

      // 解析响应
      const result: FlashTranscriptionResponse = await response.json();

      this.emit(FlashAudioRecorderEventType.TRANSCRIPTION_COMPLETE, result);
      return result;
    } catch (error) {
      const errorMessage = `转录请求失败: ${error instanceof Error ? error.message : String(error)}`;
      logger.error(errorMessage);
      this.emit(FlashAudioRecorderEventType.ERROR, errorMessage);
      throw error;
    }
  }


  /**
   * 销毁资源
   */
  public async destroy(): Promise<void> {
    // 停止录音（等待完成）
    await this.stop();

    // 清除所有事件监听
    this.eventListeners.clear();

    // 清除定时器
    if (this.recordingTimeout) {
      clearTimeout(this.recordingTimeout);
      this.recordingTimeout = null;
    }
  }
}
