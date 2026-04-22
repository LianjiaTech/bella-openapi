/**
 * Mock FileReader
 *
 * 职责:
 * - 模拟 FileReader API
 * - 提供可控的 base64 转换结果
 */

/**
 * Mock FileReader 实现
 *
 * 使用方法:
 * ```typescript
 * const cleanup = mockFileReader('data:image/png;base64,mockBase64');
 * // ... 测试代码
 * cleanup(); // 恢复原始 FileReader
 * ```
 */
export const mockFileReader = (mockResult: string = 'data:image/png;base64,mockBase64String') => {
  const originalFileReader = global.FileReader;

  // Mock FileReader 构造函数
  (global as any).FileReader = jest.fn(function(this: any) {
    this.result = null;
    this.onloadend = null;
    this.onerror = null;

    // Mock readAsDataURL 方法
    this.readAsDataURL = jest.fn(function(this: any) {
      // 异步触发 onloadend
      setTimeout(() => {
        if (this.onloadend) {
          this.result = mockResult;
          this.onloadend({ target: this } as ProgressEvent<FileReader>);
        }
      }, 0);
    });
  });

  // 返回清理函数
  return () => {
    global.FileReader = originalFileReader;
  };
};

/**
 * Mock FileReader 读取失败
 */
export const mockFileReaderError = (errorMessage: string = 'Failed to read file') => {
  const originalFileReader = global.FileReader;

  (global as any).FileReader = jest.fn(function(this: any) {
    this.result = null;
    this.onloadend = null;
    this.onerror = null;

    this.readAsDataURL = jest.fn(function(this: any) {
      setTimeout(() => {
        if (this.onerror) {
          this.onerror(new Error(errorMessage) as any);
        }
      }, 0);
    });
  });

  return () => {
    global.FileReader = originalFileReader;
  };
};
