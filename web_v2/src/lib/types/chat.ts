/**
 * 流状态
 * idle: 空闲，没有流
 * connecting: 建立连接中
 * streaming: 正在接收流式输出
 * done: 流正常结束
 * error: 流式输出错误
 * abort: 流式输出中止
 */
export type StreamStatus =
  | 'idle'
  | 'connecting'
  | 'streaming'
  | 'done'
  | 'error'
  | 'abort';
