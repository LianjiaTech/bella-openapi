import React from 'react';
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

interface AudioControlsProps {
  isRecording: boolean;
  audioSources: MediaDeviceInfo[];
  selectedSource: string;
  voiceTypes: Record<string, any>;
  voiceName: string;
  showMoreVoices: boolean;
  onSourceChange: (value: string) => void;
  onVoiceChange: (value: string) => void;
  onToggleMoreVoices: () => void;
  onToggleRecording: () => void;
}

/**
 * 音频控制组件，负责处理音频设备选择和录制控制
 */
export const AudioControls: React.FC<AudioControlsProps> = ({
  isRecording,
  audioSources,
  selectedSource,
  voiceTypes,
  voiceName,
  showMoreVoices,
  onSourceChange,
  onVoiceChange,
  onToggleMoreVoices,
  onToggleRecording
}) => {
  // 过滤显示的声音类型
  const filteredVoiceTypes = Object.entries(voiceTypes).filter(([_, value]) => {
    if (showMoreVoices) return true;
    return (value as string).includes("zh");
  });

  return (
    <div className="flex flex-col gap-4 p-4">
      <div className="flex flex-col sm:flex-row gap-4">
        {/* 音频设备选择 */}
        <div className="flex-1">
          <label className="block text-sm font-medium mb-2">选择麦克风</label>
          <Select value={selectedSource} onValueChange={onSourceChange}>
            <SelectTrigger>
              <SelectValue placeholder="选择麦克风" />
            </SelectTrigger>
            <SelectContent>
              {audioSources.map((source) => (
                <SelectItem key={source.deviceId} value={source.deviceId}>
                  {source.label || `麦克风 ${source.deviceId.substring(0, 5)}`}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* 声音类型选择 */}
        <div className="flex-1">
          <div className="flex justify-between items-center mb-2">
            <label className="block text-sm font-medium">选择声音</label>
            <Button
              variant="ghost"
              size="sm"
              onClick={onToggleMoreVoices}
              className="text-xs"
            >
              {showMoreVoices ? "只显示中文" : "显示全部"}
            </Button>
          </div>
          <Select value={voiceName} onValueChange={onVoiceChange}>
            <SelectTrigger>
              <SelectValue placeholder="选择声音" />
            </SelectTrigger>
            <SelectContent>
              {filteredVoiceTypes.map(([key, value]) => (
                <SelectItem key={key} value={key}>
                  {key}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* 录制按钮 */}
      <Button
        onClick={onToggleRecording}
        variant={isRecording ? "destructive" : "default"}
        className="w-full"
      >
        {isRecording ? "停止" : "开始录音"}
      </Button>
    </div>
  );
};
