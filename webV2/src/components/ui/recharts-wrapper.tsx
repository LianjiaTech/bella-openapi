'use client';

import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';

interface DynamicLineChartProps {
  data: Array<{
    time: string;
    channels: {
      [key: string]: {
        value: number;
        status: number;
      };
    };
  }>;
  formatTime: (time: string) => string;
  valueFormatter: (value: number) => string;
  title: string;
  channels: string[];
  channelColors: { [key: string]: string };
  aggregationType: 'sum' | 'average';
}

export function DynamicLineChart({
  data,
  formatTime,
  valueFormatter,
  title,
  channels,
  channelColors,
  aggregationType,
}: DynamicLineChartProps) {
  // 使用传入的 channels
  const displayChannels = channels;
  // 展平数据结构
  const flattenedData = data.map(item => {
    const flatItem: any = { time: item.time };
    Object.entries(item.channels).forEach(([channel, data]) => {
      flatItem[channel] = data.value;
    });
    return flatItem;
  });

  if (!data.length || !channels.length) {
    return null;
  }

  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart
        data={flattenedData}
        margin={{
          top: 10,
          right: 30,
          left: 20,
          bottom: 10,
        }}
        style={{
          backgroundColor: 'white',
        }}
      >
        <CartesianGrid
          strokeDasharray="3 3"
          stroke="rgba(231, 234, 245, 0.7)"
          horizontal={true}
          vertical={true}
        />
        <XAxis
          dataKey="time"
          tickFormatter={formatTime}
          interval="preserveStartEnd"
          stroke="#d1d5db"
          tick={{ fill: '#6b7280', fontSize: 12 }}
          axisLine={{ stroke: '#e5e7eb' }}
        />
        <YAxis
          tickFormatter={valueFormatter}
          stroke="#d1d5db"
          tick={{ fill: '#6b7280', fontSize: 12 }}
          axisLine={{ stroke: '#e5e7eb' }}
        />
        <Tooltip
          formatter={(value: any, name: string) => {
            const originalData = data.find(d => d.time === flattenedData.find(fd => fd[name] === value)?.time);
            const channelStatus = originalData?.channels[name]?.status;
            return [
              valueFormatter(value),
              `${name}${channelStatus === 0 ? ' (不可用)' : ''}`
            ];
          }}
          labelFormatter={formatTime}
          contentStyle={{
            backgroundColor: 'white',
            border: '1px solid #e5e7eb',
            borderRadius: '4px',
            boxShadow: '0 2px 4px rgba(0, 0, 0, 0.05)',
            padding: '8px 12px',
            color: '#374151',
            fontSize: 12,
          }}
          itemStyle={{ color: '#6b7280', fontSize: 12, padding: '2px 0' }}
          labelStyle={{ color: '#374151', marginBottom: '4px', fontSize: 12, fontWeight: 500 }}
        />
        {displayChannels.map(channel => (
          <Line
            key={channel}
            type="monotone"
            dataKey={channel}
            name={channel}
            stroke={channelColors[channel]}
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 4, strokeWidth: 1 }}
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
}
