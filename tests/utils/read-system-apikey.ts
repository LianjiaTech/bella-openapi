/**
 * 读取系统密钥工具函数
 *
 * 职责:
 * - 从项目根目录读取 system-apikey.txt 文件
 * - 解析文件内容,提取 API Key
 *
 * 设计说明:
 * 1. 使用同步读取避免测试异步复杂度
 * 2. 解析 "API Key: xxx" 格式的行
 * 3. 去除空白字符并返回纯密钥字符串
 * 4. 提供清晰的错误信息便于调试
 */

import * as fs from 'fs';
import * as path from 'path';

/**
 * 获取系统 API 密钥
 * @returns 系统 API 密钥字符串
 * @throws 如果文件不存在或格式不正确
 */
export function getSystemApiKey(): string {
  // 定位到项目根目录 (从 tests/e2e/utils/ 向上两级)
  const rootDir = path.resolve(__dirname, '../..');
  const filePath = path.join(rootDir, 'system-apikey.txt');

  // 检查文件是否存在
  if (!fs.existsSync(filePath)) {
    throw new Error(`系统密钥文件不存在: ${filePath}`);
  }

  // 读取文件内容
  const content = fs.readFileSync(filePath, 'utf-8');
  const lines = content.split('\n');

  // 查找 "API Key: " 开头的行
  const apiKeyLine = lines.find(line => line.includes('API Key:'));
  if (!apiKeyLine) {
    throw new Error('system-apikey.txt 文件中未找到 "API Key:" 行');
  }

  // 提取密钥部分 (格式: "API Key: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
  const parts = apiKeyLine.split('API Key:');
  if (parts.length < 2) {
    throw new Error('API Key 行格式不正确');
  }

  const apiKey = parts[1].trim();

  if (!apiKey) {
    throw new Error('API Key 为空');
  }

  return apiKey;
}