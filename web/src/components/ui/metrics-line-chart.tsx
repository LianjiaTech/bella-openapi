'use client';

import React from 'react';
import dynamic from 'next/dynamic';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

const DynamicLineChart = dynamic(
  () => import('./recharts-wrapper').then((mod) => mod.DynamicLineChart),
  { ssr: false }
);

interface MetricsData {
  time: string;
  channels: {
    [key: string]: {
      value: number;
      status: number;
      rawData?: number[]; // 用于存储聚合前的原始数据
    };
  };
}

interface MetricsLineChartProps {
  title: string;
  data: MetricsData[];
  channels: string[];
  channelColors: { [key: string]: string };
  valueFormatter?: (value: number) => string;
  aggregationType: 'sum' | 'average'; // 新增聚合类型属性
  intervalMinutes?: number; // 添加时间间隔参数
}

export function MetricsLineChart({
  title,
  data,
  channels,
  channelColors,
  valueFormatter = (value: number) => value.toString(),
  aggregationType,
  intervalMinutes = 1, // 设置默认值为 1
}: MetricsLineChartProps) {
  const formatTime = (time: string) => {
    // Format YYYYMMDDHHMM to MM-DD HH:MM
    const year = time.slice(0, 4);
    const month = time.slice(4, 6);
    const day = time.slice(6, 8);
    const hour = time.slice(8, 10);
    const minute = time.slice(10, 12);
    return `${month}-${day} ${hour}:${minute}`;
  };

  // 按时间间隔聚合数据
  const aggregateData = (data: MetricsData[]) => {
    if (data.length === 0) return [];

    // 按时间间隔对数据进行分组
    const timeGroups = new Map<string, MetricsData[]>();
    
    // 获取时间范围
    const times = data.map(item => {
      const time = item.time;
      const date = new Date(
        parseInt(time.slice(0, 4)),
        parseInt(time.slice(4, 6)) - 1,
        parseInt(time.slice(6, 8)),
        parseInt(time.slice(8, 10)),
        parseInt(time.slice(10, 12))
      );
      return Math.floor(date.getTime() / (60 * 1000));
    });
    
    const minTime = Math.min(...times);
    const maxTime = Math.max(...times);
    
    // 创建时间间隔组
    for (let time = minTime; time <= maxTime; time += intervalMinutes) {
      const date = new Date(time * 60 * 1000);
      const timeStr = 
        date.getFullYear().toString().padStart(4, '0') +
        (date.getMonth() + 1).toString().padStart(2, '0') +
        date.getDate().toString().padStart(2, '0') +
        date.getHours().toString().padStart(2, '0') +
        date.getMinutes().toString().padStart(2, '0');

      timeGroups.set(timeStr, []);
    }
    
    // 将数据分配到对应的时间组
    data.forEach(item => {
      const time = item.time;
      const date = new Date(
        parseInt(time.slice(0, 4)),
        parseInt(time.slice(4, 6)) - 1,
        parseInt(time.slice(6, 8)),
        parseInt(time.slice(8, 10)),
        parseInt(time.slice(10, 12))
      );
      const minutes = Math.floor(date.getTime() / (60 * 1000));
      const groupTime = Math.floor(minutes / intervalMinutes) * intervalMinutes;
      const groupDate = new Date(groupTime * 60 * 1000);
      
      const groupTimeStr = 
        groupDate.getFullYear().toString().padStart(4, '0') +
        (groupDate.getMonth() + 1).toString().padStart(2, '0') +
        groupDate.getDate().toString().padStart(2, '0') +
        groupDate.getHours().toString().padStart(2, '0') +
        groupDate.getMinutes().toString().padStart(2, '0');
      
      const group = timeGroups.get(groupTimeStr) || [];
      group.push(item);
      timeGroups.set(groupTimeStr, group);
    });

    // 对每个时间组进行聚合
    return Array.from(timeGroups.entries())
      .filter(([_, items]) => items.length > 0) // 只保留有数据的时间点
      .map(([time, items]) => {
        const aggregatedChannels: { [key: string]: { value: number, status: number, rawData: number[] } } = {};
        
        // 获取所有出现过的 channel
        const allChannels = new Set<string>();
        items.forEach(item => {
          Object.keys(item.channels).forEach(channel => allChannels.add(channel));
        });

        // 聚合每个 channel 的数据
        allChannels.forEach(channel => {
          const values: number[] = [];
          const statuses: number[] = [];

          items.forEach(item => {
            const channelData = item.channels[channel];
            if (channelData) {
              values.push(channelData.value);
              statuses.push(channelData.status);
            }
          });

          if (values.length > 0) {
            const value = aggregationType === 'sum'
              ? values.reduce((sum, val) => sum + val, 0)
              : values.reduce((sum, val) => sum + val, 0) / values.length;

            aggregatedChannels[channel] = {
              value,
              status: Math.min(...statuses), // 如果任何一个点是0（不可用），则整个时间段标记为不可用
              rawData: values
            };
          }
        });

        return {
          time,
          channels: aggregatedChannels
        };
      })
      .sort((a, b) => a.time.localeCompare(b.time));
  };

  const aggregatedData = aggregateData(data);

  return (
    <Card className="w-full bg-white border border-[#dce1e6] rounded-sm shadow-none">
      <CardHeader className="p-4 pb-0">
        <CardTitle className="text-sm font-medium text-gray-700">{title}</CardTitle>
      </CardHeader>
      <CardContent className="p-4">
        <div className="h-[300px] w-full">
          <DynamicLineChart
            data={aggregatedData}
            formatTime={formatTime}
            valueFormatter={valueFormatter}
            title={title}
            channels={channels}
            channelColors={channelColors}
            aggregationType={aggregationType}
          />
        </div>
      </CardContent>
    </Card>
  );
}
