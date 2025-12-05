"use client"

import * as React from "react"
import { format as formatDate, subMonths, addMinutes, differenceInMinutes } from "date-fns"
import { Calendar as CalendarIcon, Clock } from "lucide-react"
import { DateRange } from "react-day-picker"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { Calendar } from "@/components/ui/calendar"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"

interface DateRangePickerProps {
  className?: string
  startDate: Date
  endDate: Date
  onChange: (startDate: Date, endDate: Date) => void
  maxDate?: Date
  minDate?: Date
}

interface TimePickerProps {
  value: string
  onChange: (value: string) => void
}

const TimePicker: React.FC<TimePickerProps> = ({ value, onChange }) => {
  const [selectedHour, setSelectedHour] = React.useState(() => {
    return value.split(":")[0]
  })

  const handleHourSelect = (hour: number) => {
    setSelectedHour(hour.toString().padStart(2, "0"))
  }

  const handleMinuteSelect = (minute: number) => {
    const timeString = `${selectedHour}:${minute.toString().padStart(2, "0")}`
    onChange(timeString)
  }

  return (
    <div className="p-2">
      {selectedHour ? (
        <div>
          <div className="flex items-center justify-between mb-2">
            <Button
              variant="ghost"
              className="h-8"
              onClick={() => setSelectedHour("")}
            >
              返回选择小时
            </Button>
            <div className="font-medium">{selectedHour}时</div>
          </div>
          <div className="grid grid-cols-4 gap-1">
            {Array.from({ length: 60 }).map((_, minute) => (
              <Button
                key={minute}
                variant="ghost"
                className="h-8 text-xs"
                onClick={() => handleMinuteSelect(minute)}
              >
                {minute.toString().padStart(2, "0")}
              </Button>
            ))}
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-4 gap-1">
          {Array.from({ length: 24 }).map((_, hour) => (
            <Button
              key={hour}
              variant="ghost"
              className="h-8"
              onClick={() => handleHourSelect(hour)}
            >
              {hour.toString().padStart(2, "0")}
            </Button>
          ))}
        </div>
      )}
    </div>
  )
}

export function DateRangePicker({
  className,
  startDate,
  endDate,
  onChange,
  maxDate = new Date(),
  minDate = subMonths(new Date(), 1),
}: DateRangePickerProps) {
  const [date, setDate] = React.useState<DateRange>({
    from: startDate,
    to: endDate,
  })
  const [startTime, setStartTime] = React.useState(
    formatDate(startDate, "HH:mm")
  )
  const [endTime, setEndTime] = React.useState(
    formatDate(endDate, "HH:mm")
  )

  const handleTimeChange = (newStartTime?: string, newEndTime?: string) => {
    if (!date.from || !date.to) return

    const updatedStartDate = new Date(date.from)
    const updatedEndDate = new Date(date.to)

    if (newStartTime) {
      const [hours, minutes] = newStartTime.split(":").map(Number)
      updatedStartDate.setHours(hours, minutes)
      setStartTime(newStartTime)
    }

    if (newEndTime) {
      const [hours, minutes] = newEndTime.split(":").map(Number)
      updatedEndDate.setHours(hours, minutes)
      setEndTime(newEndTime)
    }

    onChange(updatedStartDate, updatedEndDate)
  }

  return (
    <div className={cn("grid gap-4", className)}>
      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <Popover>
              <PopoverTrigger asChild>
                <Button
                  variant={"outline"}
                  className={cn(
                    "w-[180px] justify-start text-left font-normal",
                    !date.from && "text-muted-foreground"
                  )}
                >
                  <CalendarIcon className="mr-2 h-4 w-4" />
                  {date.from ? (
                    formatDate(date.from, "yyyy-MM-dd")
                  ) : (
                    <span>开始日期</span>
                  )}
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="start">
                <Calendar
                  initialFocus
                  mode="single"
                  selected={date.from}
                  fromDate={minDate}
                  toDate={maxDate}
                  onSelect={(newDate) => {
                    if (newDate) {
                      const updatedStartDate = new Date(newDate)
                      const [hours, minutes] = startTime.split(":").map(Number)
                      updatedStartDate.setHours(hours, minutes)
                      setDate({ ...date, from: newDate })
                      onChange(updatedStartDate, date.to || updatedStartDate)
                    }
                  }}
                />
              </PopoverContent>
            </Popover>
            <Popover>
              <PopoverTrigger asChild>
                <Button
                  variant={"outline"}
                  className="w-[100px] justify-start text-left font-normal"
                >
                  <Clock className="mr-2 h-4 w-4" />
                  {startTime}
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="start">
                <TimePicker
                  value={startTime}
                  onChange={(value) => handleTimeChange(value, undefined)}
                />
              </PopoverContent>
            </Popover>
          </div>

          <span>至</span>

          <div className="flex items-center gap-2">
            <Popover>
              <PopoverTrigger asChild>
                <Button
                  variant={"outline"}
                  className={cn(
                    "w-[180px] justify-start text-left font-normal",
                    !date.to && "text-muted-foreground"
                  )}
                >
                  <CalendarIcon className="mr-2 h-4 w-4" />
                  {date.to ? (
                    formatDate(date.to, "yyyy-MM-dd")
                  ) : (
                    <span>结束日期</span>
                  )}
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="start">
                <Calendar
                  initialFocus
                  mode="single"
                  selected={date.to}
                  fromDate={date.from || minDate}
                  toDate={maxDate}
                  onSelect={(newDate) => {
                    if (newDate) {
                      const updatedEndDate = new Date(newDate)
                      const [hours, minutes] = endTime.split(":").map(Number)
                      updatedEndDate.setHours(hours, minutes)
                      setDate({ ...date, to: newDate })
                      onChange(date.from || updatedEndDate, updatedEndDate)
                    }
                  }}
                />
              </PopoverContent>
            </Popover>
            <Popover>
              <PopoverTrigger asChild>
                <Button
                  variant={"outline"}
                  className="w-[100px] justify-start text-left font-normal"
                >
                  <Clock className="mr-2 h-4 w-4" />
                  {endTime}
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="start">
                <TimePicker
                  value={endTime}
                  onChange={(value) => handleTimeChange(undefined, value)}
                />
              </PopoverContent>
            </Popover>
          </div>
        </div>
      </div>
    </div>
  )
}
