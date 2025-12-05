import { AudioRecorderBase, AudioRecorderConfig, AudioRecorderEventType } from "./AudioRecorderBase";

/**
 * 一句话转录录音器配置
 */
export interface FlashAudioRecorderConfig extends AudioRecorderConfig {
  url: string;
  /** 模型名称 */
  model: string;
  /** 录音最大时长(ms)，默认60000ms (60秒) */
  maxDuration?: number;
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
 * 负责处理一句话转录的录音和通信
 */
export class FlashAudioRecorder {
  private audioRecorder: AudioRecorderBase;
  private config: FlashAudioRecorderConfig;
  private eventListeners: Map<FlashAudioRecorderEventType, Function[]> = new Map();
  private audioBlob: Blob | null = null;
  private isActive = false;
  private recordingTimeout: NodeJS.Timeout | null = null;
  private audioChunks: Blob[] = []; // 用于存储录音片段

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
    this.audioRecorder = new AudioRecorderBase({
      ...this.config,
      bufferSize: 16384, // 设置较大的缓冲区以确保数据完整性
    });

    // 清空音频块数组
    this.audioChunks = [];
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
          console.error(`执行事件 ${event} 的回调函数时出错:`, error);
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
      // 清空音频块数组
      this.audioChunks = [];
      this.audioBlob = null;
      this.emit(FlashAudioRecorderEventType.RECORDING_START, null);
    });

    // 录制错误
    this.audioRecorder.on(AudioRecorderEventType.ERROR, (error: string) => {
      console.error('录音错误:', error);
      this.emit(FlashAudioRecorderEventType.ERROR, error);
    });

    // 音频数据可用时收集数据
    this.audioRecorder.on(AudioRecorderEventType.DATA, (data: Uint8Array) => {
      if (data && data.length > 0) {
        // 将Uint8Array转换为Blob并存储
        const blob = new Blob([data], { type: 'audio/pcm' });
        this.audioChunks.push(blob);}
    });

    // 录制停止
    this.audioRecorder.on(AudioRecorderEventType.STOP, async () => {

      // 在停止后等待一小段时间确保所有数据已收集
      await new Promise(resolve => setTimeout(resolve, 500));

      // 创建最终的音频Blob
      if (this.audioChunks.length > 0) {
        this.audioBlob = new Blob(this.audioChunks, { type: 'audio/pcm' });
        this.emit(FlashAudioRecorderEventType.RECORDING_COMPLETE, this.audioBlob);

        try {
          await this.transcribe();
        } catch (error) {
          this.emit(FlashAudioRecorderEventType.ERROR, `转录请求失败: ${error}`);
        }

      } else {
        console.error('没有收集到音频数据');
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

    // 重新清空数据数组以备安全
    this.audioChunks = [];
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
        return false;
      }


      return true;
    } catch (error) {
      console.error('开始录音失败:', error);
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
      console.log('正在停止录音...');
      // 清除超时定时器
      if (this.recordingTimeout) {
        clearTimeout(this.recordingTimeout);
        this.recordingTimeout = null;
      }

      // 停止录音
      await this.audioRecorder.stop();

      // 停止后等待一小段时间，确保所有数据处理完成
      await new Promise(resolve => setTimeout(resolve, 500));

      this.isActive = false;
    } catch (error) {
      console.error('停止录音失败:', error);
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

    // 在发送转录请求前，先测试音频能否正常播放
    console.log('[DEBUG] 准备测试音频能否正常播放...');

    // 打印音频数据信息，包括所有Blobs的大小和类型
    console.log(`[音频调试] 录音完成，总块数: ${this.audioChunks.length}`);
    this.audioChunks.forEach((chunk, index) => {
      console.log(`  - 块 #${index+1}: 大小=${chunk.size} 字节, 类型=${chunk.type}`);
    });

    try {
      // 获取音频数据的二进制表示
      const audioArrayBuffer = await this.audioBlob.arrayBuffer();
      console.log(`[音频调试] 合并后的音频大小: ${audioArrayBuffer.byteLength} 字节`);

      // 检查前100个字节，查看数据模式
      const sampleView = new Uint8Array(audioArrayBuffer.slice(0, Math.min(100, audioArrayBuffer.byteLength)));
      console.log(`[音频调试] 前几个字节样本: `, Array.from(sampleView).slice(0, 20));

      // 参考日志显示已收集到数据，只需要正确格式化
      console.log(`[关键处理] PCM数据已收集: 大小=${audioArrayBuffer.byteLength} 字节`);

      // 创建新的PCM blob，保持原有格式
      const pcmBlob = new Blob([audioArrayBuffer], {
        type: 'audio/pcm'
      });

      // 使用原始URL，不需要修改

      console.log(`[音频调试] 准备发送的PCM数据: 大小=${pcmBlob.size} 字节`);

      // 发送请求
      const response = await fetch(this.config.url, {
        method: 'POST',
        headers: {
          'Content-Type': 'audio/pcm',  // PCM MIME类型
          'model': this.config.model,   // 模型指定
          'format': 'pcm',             // 指定是PCM格式
          'sample-rate': '16000',      // 指定采样率为16kHz
        },
        body: pcmBlob,
        credentials: 'include' // 确保cookie被发送
      });

      // 打印响应状态
      console.log(`[音频调试] API响应状态: ${response.status} ${response.statusText}`);

      if (!response.ok) {
        const errorText = await response.text();
        console.error(`服务器响应错误 ${response.status}:`, errorText);
        throw new Error(`HTTP 错误 ${response.status}: ${errorText}`);
      }

      // 解析响应
      const result: FlashTranscriptionResponse = await response.json();

      this.emit(FlashAudioRecorderEventType.TRANSCRIPTION_COMPLETE, result);
      return result;
    } catch (error) {
      const errorMessage = `转录请求失败: ${error instanceof Error ? error.message : String(error)}`;
      console.error(errorMessage);
      this.emit(FlashAudioRecorderEventType.ERROR, errorMessage);
      throw error;
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
