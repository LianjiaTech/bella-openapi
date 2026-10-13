import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/common/select"

interface AudioDeviceSelectorProps {
    audioSources: MediaDeviceInfo[]
    selectedSource: string | null
    onSourceChange: (deviceId: string) => void
    disabled?: boolean
    className?: string
}

/**
 * 音频设备选择组件
 */
export function AudioDeviceSelector({
                                        audioSources,
                                        selectedSource,
                                        onSourceChange,
                                        disabled = false,
                                        className = "w-[220px]"
                                    }: AudioDeviceSelectorProps) {
    const validDevices = audioSources.filter(d => d.deviceId && d.deviceId !== '')

    return (
        <Select
            value={selectedSource || ""}
            onValueChange={onSourceChange}
            disabled={disabled}
        >
            <SelectTrigger className={className}>
                <SelectValue placeholder="选择麦克风设备" />
            </SelectTrigger>
            <SelectContent>
                {validDevices.length === 0 ? (
                    <SelectItem value="none" disabled>
                        未检测到麦克风
                    </SelectItem>
                ) : (
                    validDevices.map((device) => (
                        <SelectItem
                            key={device.deviceId}
                            value={device.deviceId}
                        >
                            {device.label || `麦克风 ${device.deviceId.slice(0, 8)}...`}
                        </SelectItem>
                    ))
                )}
            </SelectContent>
        </Select>
    )
}
