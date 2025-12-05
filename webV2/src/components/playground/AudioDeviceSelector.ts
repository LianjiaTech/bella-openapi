import { useState, useEffect } from 'react';

/**
 * 音频设备选择钩子，负责加载和管理音频设备
 */
export function useAudioDevices(onError: (error: string) => void) {
  const [audioSources, setAudioSources] = useState<MediaDeviceInfo[]>([]);
  const [selectedSource, setSelectedSource] = useState('');
  const [permissionGranted, setPermissionGranted] = useState(false);

  // 加载音频设备
  useEffect(() => {
    async function requestMicrophonePermission() {
      try {
        // 这一步会触发浏览器请求麦克风权限
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        console.log("麦克风权限已获取");
        
        // 关闭流，我们只是为了获取权限
        stream.getTracks().forEach(track => track.stop());
        
        setPermissionGranted(true);
        return true;
      } catch (error) {
        console.error("麦克风权限请求失败:", error);
        onError(`请授予麦克风访问权限: ${error instanceof Error ? error.message : String(error)}`);
        return false;
      }
    }
    async function loadAudioSources() {
      try {
        console.log("加载音频设备...");
        const devices = await navigator.mediaDevices.enumerateDevices();
        const audioInputs = devices.filter((device) => device.kind === "audioinput");
        console.log("找到音频输入设备:", audioInputs);
        setAudioSources(audioInputs);

        // 简单地默认选择第一个设备
        if (audioInputs.length > 0 && !selectedSource) {
          setSelectedSource(audioInputs[0].deviceId);
          console.log("默认选择第一个设备:", audioInputs[0].label);
        }
      } catch (error) {
        console.error("加载音频设备错误:", error);
        onError(`无法加载音频设备: ${error instanceof Error ? error.message : String(error)}`);
      }
    }
    requestMicrophonePermission();
    loadAudioSources();
  }, [selectedSource, onError]);

  return {
    audioSources,
    selectedSource,
    setSelectedSource
  };
}
