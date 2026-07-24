import { useState, useEffect } from 'react'
import { getVoiceProperties } from '@/lib/api/metadata'
import { logger } from '@/lib/utils/logger'

/**
 * 声音选择钩子，负责加载和管理语音类型
 */
export function useVoiceSelector(isModel: boolean, modelOrEndpoint: string) {
  const [voiceTypes, setVoiceTypes] = useState<Record<string, string>>({})
  const [voiceName, setVoiceName] = useState<string>("")
  const [voiceLoading, setVoiceLoading] = useState(false)
  const [showMoreVoices, setShowMoreVoices] = useState(false)

  // 加载声音类型
  useEffect(() => {
    if (!modelOrEndpoint) {
      setVoiceTypes({})
      setVoiceName("")
      return
    }

    async function loadVoiceTypes() {
      setVoiceLoading(true)
      try {
        const voiceData = await getVoiceProperties(isModel ? 'model' : 'endpoint', modelOrEndpoint)
        if (voiceData && voiceData.voiceTypes && Object.keys(voiceData.voiceTypes).length > 0) {
          setVoiceTypes(voiceData.voiceTypes)
          // 默认选择第一个声音
          const firstVoice = Object.keys(voiceData.voiceTypes)[0]
          if (firstVoice) {
            setVoiceName(firstVoice)
          }
        } else {
          setVoiceTypes({})
          setVoiceName("")
        }
      } catch (error) {
        logger.error("加载声音类型错误:", error)
        setVoiceTypes({})
        setVoiceName("")
      } finally {
        setVoiceLoading(false)
      }
    }

    loadVoiceTypes()
  }, [modelOrEndpoint, isModel])

  // 切换显示更多声音
  const toggleMoreVoices = () => {
    setShowMoreVoices(prev => !prev)
  }

  return {
    voiceTypes,
    voiceName,
    setVoiceName,
    voiceLoading,
    showMoreVoices,
    toggleMoreVoices
  }
}
