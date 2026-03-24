// chat/transport/request-dispatcher.ts

import type { IChatTransport, RequestDispatcherOptions } from './types'
import { StreamManager } from './streamManager'
import { JsonManager } from './jsonManager'


/**
 * RequestDispatcher
 *
 * 职责：
 * - 根据 mode 创建对应 transport
 * - 不自动执行 start
 * - 不处理 store
 *
 * 设计原则：
 * - 单一职责：只做分发
 * - UI 拿到 transport 后自行调用 start()
 */
export class RequestDispatcher {

  send(options: RequestDispatcherOptions): IChatTransport | StreamManager | JsonManager {
    const { mode } = options
    if (mode === 'stream') {
      return new StreamManager(options)
    }

    if (mode === 'json') {
      return new JsonManager(options)
    }

    // 理论上不会发生（类型已约束）
    throw new Error(`[RequestDispatcher] 不支持当前模式: ${mode}`)
  }
}
