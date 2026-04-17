import { useState, useEffect, useCallback, useRef } from 'react';
import { logger } from '@/lib/utils/logger';

/**
 * 音频设备选择钩子，负责加载和管理音频设备
 */
export function useAudioDevices(onError: (error: string) => void) {
  const [audioSources, setAudioSources] = useState<MediaDeviceInfo[]>([]);
  const [selectedSource, setSelectedSource] = useState('');
  const [permissionGranted, setPermissionGranted] = useState(false);
  const hasSetDefaultSourceRef = useRef(false);
  const onErrorRef = useRef(onError);

  // 保持 onError 引用最新
  useEffect(() => {
    onErrorRef.current = onError;
  }, [onError]);

  // 使用 useCallback 确保函数引用稳定，避免依赖 onError
  const loadAudioSources = useCallback(async () => {
    try {
      const devices = await navigator.mediaDevices.enumerateDevices();
      const audioInputs = devices.filter((device) => device.kind === "audioinput");
      setAudioSources(audioInputs);

      // 默认选择第一个设备
      const validDevices = audioInputs.filter(d => d.deviceId && d.deviceId !== '');
      if (validDevices.length > 0 && !hasSetDefaultSourceRef.current) {
        hasSetDefaultSourceRef.current = true;
        setSelectedSource(validDevices[0].deviceId);
      }
    } catch (error) {
      logger.error("加载音频设备错误:", error);
      onErrorRef.current(`无法加载音频设备: ${error instanceof Error ? error.message : String(error)}`);
    }
  }, []);

  // 加载音频设备
  useEffect(() => {
    // 检查是否在浏览器环境，以及是否支持 Media Devices API
    if (typeof window === 'undefined' || !navigator.mediaDevices) {
      logger.warn('Media Devices API 不可用（可能在服务端渲染或浏览器不支持）');
      onErrorRef.current('您的浏览器不支持音频设备访问功能，请使用现代浏览器（Chrome、Firefox、Edge等）');
      return;
    }

    async function requestMicrophonePermission() {
      try {
        // 这一步会触发浏览器请求麦克风权限
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        stream.getTracks().forEach(track => track.stop());

        setPermissionGranted(true);
        return true;
      } catch (error) {
        logger.error("麦克风权限请求失败:", error);
        onErrorRef.current(`请授予麦克风访问权限: ${error instanceof Error ? error.message : String(error)}`);
        return false;
      }
    }

    // 初始化：先请求权限，再加载设备
    async function init() {
      // 1. 先请求权限
      const hasPermission = await requestMicrophonePermission();

      // 2. 权限获取成功后，加载设备
      if (hasPermission) {
        await loadAudioSources();
      }
    }

    // 3. 添加设备变化监听（例如插拔设备）
    const handleDeviceChange = () => {
      // 重新加载设备列表
      loadAudioSources();
    };

    // 注册设备变化监听器
    navigator.mediaDevices.addEventListener('devicechange', handleDeviceChange);

    // 启动初始化
    init();

    // 清理函数：移除事件监听器
    return () => {
      if (navigator.mediaDevices) {
        navigator.mediaDevices.removeEventListener('devicechange', handleDeviceChange);
      }
    };
  }, [loadAudioSources]);

  // 暴露重新加载设备的方法，供外部调用（如重新请求权限后）
  const reloadDevices = useCallback(async () => {
    await loadAudioSources();
  }, [loadAudioSources]);

  return {
    audioSources,
    selectedSource,
    setSelectedSource,
    permissionGranted,
    reloadDevices
  };
}
