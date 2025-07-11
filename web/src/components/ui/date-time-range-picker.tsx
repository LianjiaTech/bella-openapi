'use client';

import * as React from "react";
import { format, subDays, startOfDay, endOfDay, startOfWeek, endOfWeek, startOfMonth, endOfMonth } from "date-fns";
import { Calendar as CalendarIcon, Clock } from "lucide-react";
import { DateRange } from "react-day-picker";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Input } from "./input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";

interface DateTimeRangePickerProps {
  onChange: (getTimeRange: () => { startDate: Date; endDate: Date }, isRelative: boolean) => void;
  maxDate?: Date;
  minDate?: Date;
}

export interface DateTimeRangePickerRef {
  getCurrentTimeRange: () => { startDate: Date; endDate: Date };
  isUsingRelativeTime: () => boolean;
}

export const DateTimeRangePicker = React.forwardRef<DateTimeRangePickerRef, DateTimeRangePickerProps>(({
  onChange,
  maxDate,
  minDate,
}, ref) => {
  const [selectedRange, setSelectedRange] = React.useState<DateRange>({
    from: undefined,
    to: undefined,
  });

  // 时间范围预设选项
  const presets = React.useMemo(() => [
    { label: "过去5分钟", isRelative: true, getValue: () => {
      const end = new Date();
      const start = new Date();
      start.setMinutes(start.getMinutes() - 5);
      return { from: start, to: end };
    }},
    { label: "过去10分钟", isRelative: true, getValue: () => {
      const end = new Date();
      const start = new Date();
      start.setMinutes(start.getMinutes() - 10);
      return { from: start, to: end };
    }},
    { label: "过去20分钟", isRelative: true, getValue: () => {
      const end = new Date();
      const start = new Date();
      start.setMinutes(start.getMinutes() - 20);
      return { from: start, to: end };
    }},
    { label: "过去30分钟", isRelative: true, getValue: () => {
      const end = new Date();
      const start = new Date();
      start.setMinutes(start.getMinutes() - 30);
      return { from: start, to: end };
    }},
    { label: "过去1小时", isRelative: true, getValue: () => {
      const end = new Date();
      const start = new Date();
      start.setHours(start.getHours() - 1);
      return { from: start, to: end };
    }},
    { label: "过去2小时", isRelative: true, getValue: () => {
      const end = new Date();
      const start = new Date();
      start.setHours(start.getHours() - 2);
      return { from: start, to: end };
    }},
    { label: "过去6小时", isRelative: true, getValue: () => {
      const end = new Date();
      const start = new Date();
      start.setHours(start.getHours() - 6);
      return { from: start, to: end };
    }},
    { label: "过去12小时", isRelative: true, getValue: () => {
      const end = new Date();
      const start = new Date();
      start.setHours(start.getHours() - 12);
      return { from: start, to: end };
    }},
    { label: "今天", isRelative: false, getValue: () => {
      const today = new Date();
      return {
        from: new Date(today.setHours(0, 0, 0, 0)),
        to: new Date(new Date().setHours(23, 59, 59, 999))
      };
    }},
    { label: "昨天", isRelative: false, getValue: () => {
      const yesterday = subDays(new Date(), 1);
      return {
        from: new Date(yesterday.setHours(0, 0, 0, 0)),
        to: new Date(yesterday.setHours(23, 59, 59, 999))
      };
    }},
    { label: "过去7天", isRelative: false, getValue: () => {
      const end = new Date();
      end.setHours(23, 59, 59, 999);
      const start = subDays(new Date(), 6);
      start.setHours(0, 0, 0, 0);
      return { from: start, to: end };
    }},
    { label: "过去30天", isRelative: false, getValue: () => {
      const end = new Date();
      end.setHours(23, 59, 59, 999);
      const start = subDays(new Date(), 29);
      start.setHours(0, 0, 0, 0);
      return { from: start, to: end };
    }},
    { label: "本周", isRelative: false, getValue: () => {
      const start = startOfWeek(new Date(), { weekStartsOn: 1 });
      const end = endOfWeek(new Date(), { weekStartsOn: 1 });
      end.setHours(23, 59, 59, 999);
      return { from: start, to: end };
    }},
    { label: "本月", isRelative: false, getValue: () => {
      const start = startOfMonth(new Date());
      const end = endOfMonth(new Date());
      end.setHours(23, 59, 59, 999);
      return { from: start, to: end };
    }},
  ], []);

  // 追踪当前选择的时间类型：相对时间或绝对时间
  const [isRelativeTime, setIsRelativeTime] = React.useState(true);
  const [selectedRelativeType, setSelectedRelativeType] = React.useState("过去5分钟");
  const [isAbsoluteMode, setIsAbsoluteMode] = React.useState(false); // 是否强制使用绝对时间模式

  // 获取当前相对时间范围的方法 - 对外暴露
  const getCurrentRelativeRange = React.useCallback(() => {
    if (!isRelativeTime || !selectedRelativeType) return null;
    const preset = presets.find(p => p.label === selectedRelativeType);
    return preset ? preset.getValue() : null;
  }, [isRelativeTime, selectedRelativeType, presets]);

  // 对外暴露方法，供父组件调用以获取最新的相对时间范围
  React.useImperativeHandle(ref, () => ({
    getCurrentTimeRange: () => {
      if (isRelativeTime && selectedRelativeType) {
        const range = getCurrentRelativeRange();
        return range ? { startDate: range.from!, endDate: range.to! } : { startDate: new Date(), endDate: new Date() };
      }
      return { startDate: selectedRange.from || new Date(), endDate: selectedRange.to || new Date() };
    },
    isUsingRelativeTime: () => isRelativeTime
  }), [isRelativeTime, selectedRelativeType, getCurrentRelativeRange, selectedRange]);

  // 初始化时设置默认相对时间范围（过去5分钟）
  React.useEffect(() => {
    const getTimeRange = () => {
      const now = new Date();
      const startTime = new Date();
      startTime.setMinutes(startTime.getMinutes() - 5);
      return { startDate: startTime, endDate: now };
    };
    onChange(getTimeRange, true);
  }, []); // 空依赖数组，只在组件初始化时执行一次

  // 处理预设选择
  const handlePresetChange = (preset: string) => {
    const selectedPreset = presets.find(p => p.label === preset);
    if (selectedPreset) {
      const range = selectedPreset.getValue();
      setSelectedRange(range);

      // 根据是否强制使用绝对时间来决定最终的时间类型
      const finalIsRelative = !isAbsoluteMode && selectedPreset.isRelative;

      setIsRelativeTime(finalIsRelative);
      setSelectedRelativeType(finalIsRelative ? preset : "");

      if (range.from && range.to) {
        // 创建获取时间范围的函数
        const getTimeRange = finalIsRelative ? 
          () => {
            const r = selectedPreset.getValue();
            return { startDate: r.from!, endDate: r.to! };
          } :
          () => ({ startDate: range.from!, endDate: range.to! });
        
        onChange(getTimeRange, finalIsRelative);
      }
    }
  };

  // 处理绝对时间模式切换
  const handleAbsoluteModeChange = (checked: boolean) => {
    setIsAbsoluteMode(checked);

    if (checked) {
      // 切换到绝对时间模式，将当前相对时间计算的结果作为绝对时间的初始值
      setIsRelativeTime(false);
      setSelectedRelativeType("");
      
      // 如果当前是相对时间，获取实际计算的时间范围
      if (isRelativeTime && selectedRelativeType) {
        const currentRange = getCurrentRelativeRange();
        if (currentRange?.from && currentRange?.to) {
          setSelectedRange(currentRange);
          const getTimeRange = () => ({ startDate: currentRange.from!, endDate: currentRange.to! });
          onChange(getTimeRange, false);
        }
      } else if (selectedRange.from && selectedRange.to) {
        // 如果已经有选中的范围，直接使用
        const getTimeRange = () => ({ startDate: selectedRange.from!, endDate: selectedRange.to! });
        onChange(getTimeRange, false);
      }
    } else {
      // 切换回相对时间模式，重新应用当前选择的预设
      const currentPreset = presets.find(p => p.label === selectedRelativeType || p.label === "过去5分钟");
      if (currentPreset && currentPreset.isRelative) {
        const range = currentPreset.getValue();
        setSelectedRange(range);
        setIsRelativeTime(true);
        setSelectedRelativeType(currentPreset.label);
        if (range.from && range.to) {
          const getTimeRange = () => {
            const r = currentPreset.getValue();
            return { startDate: r.from!, endDate: r.to! };
          };
          onChange(getTimeRange, true);
        }
      }
    }
  };

  const handleDateRangeSelect = (range: DateRange | undefined) => {
    if (range?.from) {
      const currentRange = selectedRange.from && selectedRange.to ? selectedRange : { from: new Date(), to: new Date() };
      
      const newStartDate = new Date(range.from);
      newStartDate.setHours(currentRange.from!.getHours());
      newStartDate.setMinutes(currentRange.from!.getMinutes());

      let newEndDate = currentRange.to!;
      if (range.to) {
        newEndDate = new Date(range.to);
        newEndDate.setHours(currentRange.to!.getHours());
        newEndDate.setMinutes(currentRange.to!.getMinutes());
      }

      // 手动选择日期范围视为绝对时间
      setIsRelativeTime(false);
      setSelectedRelativeType("");
      setSelectedRange({ from: newStartDate, to: newEndDate });
      
      const getTimeRange = () => ({ startDate: newStartDate, endDate: newEndDate });
      onChange(getTimeRange, false);
    }
  };

  // 格式化时间为 HH:mm 格式
  const formatTime = (date: Date | undefined) => {
    if (!date) return "";
    const hours = date.getHours().toString().padStart(2, "0");
    const minutes = date.getMinutes().toString().padStart(2, "0");
    return `${hours}:${minutes}`;
  };

  // 处理开始时间变更
  const handleStartTimeChange = (timeString: string) => {
    if (timeString && selectedRange.from) {
      const [hours, minutes] = timeString.split(':').map(Number);
      const newStartDate = new Date(selectedRange.from);
      newStartDate.setHours(hours || 0);
      newStartDate.setMinutes(minutes || 0);
      newStartDate.setSeconds(0);

      const newEndDate = selectedRange.to || new Date();

      // 手动修改时间视为绝对时间
      setIsRelativeTime(false);
      setSelectedRelativeType("");

      // 验证开始时间不晚于结束时间
      if (selectedRange.to && newStartDate > newEndDate) {
        // 如果开始时间晚于结束时间，将结束时间设置为开始时间后1小时
        const adjustedEndDate = new Date(newStartDate);
        adjustedEndDate.setHours(adjustedEndDate.getHours() + 1);
        setSelectedRange({ from: newStartDate, to: adjustedEndDate });
        const getTimeRange = () => ({ startDate: newStartDate, endDate: adjustedEndDate });
        onChange(getTimeRange, false);
      } else {
        setSelectedRange({ from: newStartDate, to: newEndDate });
        const getTimeRange = () => ({ startDate: newStartDate, endDate: newEndDate });
        onChange(getTimeRange, false);
      }
    }
  };

  // 处理结束时间变更
  const handleEndTimeChange = (timeString: string) => {
    if (timeString && selectedRange.to) {
      const [hours, minutes] = timeString.split(':').map(Number);
      const newEndDate = new Date(selectedRange.to);
      newEndDate.setHours(hours || 0);
      newEndDate.setMinutes(minutes || 0);
      newEndDate.setSeconds(0);

      const newStartDate = selectedRange.from || new Date();

      // 手动修改时间视为绝对时间
      setIsRelativeTime(false);
      setSelectedRelativeType("");

      // 验证结束时间不早于开始时间
      if (newEndDate < newStartDate) {
        // 如果结束时间早于开始时间，将开始时间设置为结束时间前1小时
        const adjustedStartDate = new Date(newEndDate);
        adjustedStartDate.setHours(adjustedStartDate.getHours() - 1);
        setSelectedRange({ from: adjustedStartDate, to: newEndDate });
        const getTimeRange = () => ({ startDate: adjustedStartDate, endDate: newEndDate });
        onChange(getTimeRange, false);
      } else {
        setSelectedRange({ ...selectedRange, to: newEndDate });
        const getTimeRange = () => ({ startDate: newStartDate, endDate: newEndDate });
        onChange(getTimeRange, false);
      }
    }
  };

  // 显示日期范围文字
  const dateRangeText = React.useMemo(() => {
    // 如果是相对时间，只显示相对时间类型，不显示具体时间
    if (isRelativeTime && selectedRelativeType) {
      return selectedRelativeType;
    }

    // 绝对时间需要有具体的时间范围
    if (!selectedRange.from) return "选择日期范围";

    // 绝对时间显示具体时间范围
    if (selectedRange.to) {
      return `${format(selectedRange.from, "yyyy-MM-dd HH:mm")} 至 ${format(selectedRange.to, "yyyy-MM-dd HH:mm")}`;
    }

    return format(selectedRange.from, "yyyy-MM-dd HH:mm");
  }, [selectedRange, isRelativeTime, selectedRelativeType]);

  return (
    <div className="flex items-center gap-2">
      {/* 预设选择 */}
      <Select onValueChange={handlePresetChange} defaultValue="过去5分钟">
        <SelectTrigger className="h-9 w-[120px] border-gray-300">
          <SelectValue placeholder="快速选择" />
        </SelectTrigger>
        <SelectContent>
          {presets.map((preset) => (
            <SelectItem key={preset.label} value={preset.label}>
              {preset.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {/* 日期范围选择器 */}
      <Popover>
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            className="h-9 border-gray-300 justify-start text-left font-normal w-[320px] text-sm"
          >
            <CalendarIcon className="mr-2 h-4 w-4" />
            {dateRangeText}
            {isRelativeTime && (
              <span className="ml-2 text-xs text-gray-400">(相对时间)</span>
            )}
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-auto p-0" align="start">
          <Tabs defaultValue="calendar">
            <div className="flex items-center justify-between px-4 pt-3">
              <TabsList>
                <TabsTrigger value="calendar" disabled={isRelativeTime}>
                  日历{isRelativeTime && <span className="ml-1 text-xs">(已禁用)</span>}
                </TabsTrigger>
                <TabsTrigger value="time" disabled={isRelativeTime}>
                  时间{isRelativeTime && <span className="ml-1 text-xs">(已禁用)</span>}
                </TabsTrigger>
              </TabsList>
            </div>
            <TabsContent value="calendar" className="p-0">
              {isRelativeTime ? (
                <div className="p-4 text-center text-gray-500">
                  <p>相对时间模式下无法手动选择日期</p>
                  <p>请切换到绝对时间模式或选择其他预设时间范围</p>
                </div>
              ) : (
                <Calendar
                  initialFocus
                  mode="range"
                  defaultMonth={selectedRange.from || new Date()}
                  selected={selectedRange}
                  onSelect={handleDateRangeSelect}
                  numberOfMonths={2}
                  disabled={(date) => {
                    if (maxDate && date > maxDate) return true;
                    if (minDate && date < minDate) return true;
                    return false;
                  }}
                />
              )}
            </TabsContent>
            <TabsContent value="time" className="p-4 space-y-4">
              {isRelativeTime ? (
                <div className="text-center text-gray-500">
                  <p>相对时间模式下无法手动修改时间</p>
                  <p>请切换到绝对时间模式或选择其他预设时间范围</p>
                </div>
              ) : (
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <div className="text-sm font-medium">开始时间</div>
                    <div className="flex items-center">
                      <Clock className="mr-2 h-4 w-4 text-gray-500" />
                      <Input
                        type="time"
                        value={formatTime(selectedRange.from)}
                        onChange={(e) => handleStartTimeChange(e.target.value)}
                        className="h-9"
                      />
                    </div>
                  </div>
                  <div className="space-y-2">
                    <div className="text-sm font-medium">结束时间</div>
                    <div className="flex items-center">
                      <Clock className="mr-2 h-4 w-4 text-gray-500" />
                      <Input
                        type="time"
                        value={formatTime(selectedRange.to)}
                        onChange={(e) => handleEndTimeChange(e.target.value)}
                        className="h-9"
                      />
                    </div>
                  </div>
                </div>
              )}
            </TabsContent>
          </Tabs>
        </PopoverContent>
      </Popover>

      {/* 绝对时间模式切换 */}
      <div className="flex items-center space-x-2">
        <Switch
          id="absolute-mode"
          checked={isAbsoluteMode}
          onCheckedChange={handleAbsoluteModeChange}
        />
        <Label htmlFor="absolute-mode" className="text-sm text-gray-600">
          使用绝对时间
        </Label>
      </div>
    </div>
  );
});

DateTimeRangePicker.displayName = "DateTimeRangePicker";
