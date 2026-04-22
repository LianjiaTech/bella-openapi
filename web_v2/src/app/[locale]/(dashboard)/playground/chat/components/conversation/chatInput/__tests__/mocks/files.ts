/**
 * Mock 文件创建工具
 *
 * 职责:
 * - 创建各种类型的测试文件
 * - 提供不同大小的文件用于测试
 */

/**
 * 创建 Mock File 对象
 */
export const createMockFile = (
  name: string,
  size: number,
  type: string
): File => {
  const content = 'x'.repeat(size);
  const blob = new Blob([content], { type });
  return new File([blob], name, { type });
};

/**
 * 创建标准图片文件 (1MB)
 */
export const mockImageFile = (): File =>
  createMockFile('test.png', 1024 * 1024, 'image/png');

/**
 * 创建标准视频文件 (5MB)
 */
export const mockVideoFile = (): File =>
  createMockFile('test.mp4', 5 * 1024 * 1024, 'video/mp4');

/**
 * 创建超大图片文件 (15MB) - 用于测试大小限制
 */
export const mockLargeImageFile = (): File =>
  createMockFile('large.png', 15 * 1024 * 1024, 'image/png');

/**
 * 创建超大视频文件 (60MB) - 用于测试大小限制
 */
export const mockLargeVideoFile = (): File =>
  createMockFile('large.mp4', 60 * 1024 * 1024, 'video/mp4');

/**
 * 创建不支持的文件类型
 */
export const mockInvalidFile = (): File =>
  createMockFile('doc.pdf', 1024, 'application/pdf');

/**
 * 创建 JPEG 图片
 */
export const mockJpegFile = (): File =>
  createMockFile('photo.jpg', 2 * 1024 * 1024, 'image/jpeg');

/**
 * 创建 WebM 视频
 */
export const mockWebmFile = (): File =>
  createMockFile('video.webm', 3 * 1024 * 1024, 'video/webm');
