/**
 * 复制文本到剪贴板的通用工具函数
 * 支持现代 Clipboard API 和降级方案
 */

/**
 * 复制文本到剪贴板
 * @param text 要复制的文本
 * @returns Promise<boolean> 复制是否成功
 */
export async function copyToClipboard(text: string): Promise<boolean> {
  console.log('---text ====', text)
  // 方案1: 优先使用现代 Clipboard API
  if (navigator.clipboard && window.isSecureContext) {
    
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch (err) {
      console.error('Clipboard API 复制失败:', err);
      // 如果 Clipboard API 失败,尝试降级方案
    }
  }

  // 方案2: 降级使用 execCommand (兼容旧浏览器和非安全上下文)
  try {
    return fallbackCopyToClipboard(text);
  } catch (err) {
    console.error('降级复制方案也失败:', err);
    return false;
  }
}

/**
 * 降级复制方案 - 使用 document.execCommand
 * @param text 要复制的文本
 * @returns boolean 复制是否成功
 */
function fallbackCopyToClipboard(text: string): boolean {
  // 创建临时 textarea 元素
  const textArea = document.createElement('textarea');
  textArea.value = text;

  // 设置样式,使其不可见且不影响布局
  textArea.style.position = 'fixed';
  textArea.style.top = '-9999px';
  textArea.style.left = '-9999px';
  textArea.style.opacity = '0';
  textArea.setAttribute('readonly', '');

  document.body.appendChild(textArea);

  try {
    // 选中文本
    textArea.focus();
    textArea.select();

    // 对于 iOS Safari
    if (navigator.userAgent.match(/ipad|iphone/i)) {
      const range = document.createRange();
      range.selectNodeContents(textArea);
      const selection = window.getSelection();
      if (selection) {
        selection.removeAllRanges();
        selection.addRange(range);
      }
      textArea.setSelectionRange(0, text.length);
    }

    // 执行复制命令
    const successful = document.execCommand('copy');
    return successful;
  } catch (err) {
    console.error('execCommand 复制失败:', err);
    return false;
  } finally {
    // 清理临时元素
    document.body.removeChild(textArea);
  }
}

/**
 * 检查剪贴板 API 是否可用
 * @returns boolean
 */
export function isClipboardSupported(): boolean {
  return !!(navigator.clipboard && window.isSecureContext);
}
