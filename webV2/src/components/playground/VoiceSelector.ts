import { useState, useEffect } from 'react';
import { getVoiceProperties } from "@/lib/api/meta";

/**
 * 声音选择钩子，负责加载和管理语音类型
 */
export function useVoiceSelector(isModel : boolean, modelOrEndpoint: string) {
  const [voiceTypes, setVoiceTypes] = useState<Record<string, any>>({});
  const [voiceName, setVoiceName] = useState<string>("");
  const [showMoreVoices, setShowMoreVoices] = useState(false);

  // 加载声音类型
  useEffect(() => {
    async function loadVoiceTypes() {
      try {
        const voiceData = await getVoiceProperties(isModel ? 'model' : 'endpoint', modelOrEndpoint);

        if (voiceData && voiceData.voiceTypes) {
          setVoiceTypes(voiceData.voiceTypes);
          // 默认选择第一个声音
          if (Object.keys(voiceData.voiceTypes).length > 0 && !voiceName) {
            setVoiceName(Object.keys(voiceData.voiceTypes)[0]);
          }
        } else {
          setVoiceTypes({});
          setVoiceName("");
        }
      } catch (error) {
        console.error("加载声音类型错误:", error);
      }
    }

    loadVoiceTypes();
  }, [modelOrEndpoint]);

  // 切换显示更多声音
  const toggleMoreVoices = () => {
    setShowMoreVoices(prev => !prev);
  };

  return {
    voiceTypes,
    voiceName,
    setVoiceName,
    showMoreVoices,
    toggleMoreVoices
  };
}
