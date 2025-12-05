'use client';

import React, { useState, useEffect } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { api_host } from '@/config';
import { useUser } from "@/lib/context/user-context";
import { ClipboardIcon, CheckIcon } from 'lucide-react';

interface EmbeddingResponse {
  object: string;
  data: {
    object: string;
    embedding: number[];
    index: number;
  }[];
}

export default function EmbeddingsPlayground() {
  // 状态管理
  const [model, setModel] = useState('');
  const [input, setInput] = useState('Hello world!\n今天的天气真不错......');
  const [response, setResponse] = useState<EmbeddingResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [expandedEmbeddings, setExpandedEmbeddings] = useState<number[]>([]);
  const [expandedInputs, setExpandedInputs] = useState<number[]>([]);
  const [inputArray, setInputArray] = useState<string[]>([]);
  const [copiedIndex, setCopiedIndex] = useState<number | null>(null);
  const { userInfo } = useUser();

  // 从 URL 中获取 model 参数
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const modelParam = params.get('model');
    if (modelParam) {
      setModel(modelParam);
    }
  }, []);

  // 发送请求获取embedding
  const getEmbeddings = async () => {
    setIsLoading(true);
    setError('');
    setResponse(null);

    try {
      const protocol = typeof window !== 'undefined' ? window.location.protocol : 'http:';
      const host = api_host || window.location.host;

      // 将输入文本按行分割为数组
      const inputs = input.split('\n').filter(line => line.trim() !== '');
      setInputArray(inputs);

      const response = await fetch(`${protocol}//${host}/v1/embeddings`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          input: inputs,
          model: model,
          encoding_format: 'float',
          user: userInfo?.userId
        }),
        credentials: 'include'
      });

      if (!response.ok) {
        throw new Error(`请求失败: ${response.status} ${await response.json().then(data => data.error.message).catch(() => '未知错误')}`);
      }

      const data = await response.json();
      setResponse(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '未知错误');
    } finally {
      setIsLoading(false);
    }
  };

  // 切换embedding的展开/收缩状态
  const toggleEmbedding = (index: number) => {
    setExpandedEmbeddings(prev => {
      if (prev.includes(index)) {
        return prev.filter(i => i !== index);
      } else {
        return [...prev, index];
      }
    });
  };

  // 切换输入文本的展开/收缩状态
  const toggleInput = (index: number) => {
    setExpandedInputs(prev => {
      if (prev.includes(index)) {
        return prev.filter(i => i !== index);
      } else {
        return [...prev, index];
      }
    });
  };

  // 格式化JSON显示
  const formatJSON = (obj: any) => {
    if (!obj) return '';
    return JSON.stringify(obj, null, 2);
  };

  // 截断文本并添加展开/收缩功能
  const truncateText = (text: string, index: number, maxLength: number = 50) => {
    if (!text) return '';

    const isExpanded = expandedInputs.includes(index);
    if (isExpanded || text.length <= maxLength) {
      return (
        <span>
          {text}
          {text.length > maxLength && (
            <span
              className="text-blue-600 hover:text-blue-800 cursor-pointer ml-2"
              onClick={(e) => {
                e.stopPropagation();
                toggleInput(index);
              }}
            >
              [收起]
            </span>
          )}
        </span>
      );
    }

    return (
      <span>
        {text.substring(0, maxLength)}...
        <span
          className="text-blue-600 hover:text-blue-800 cursor-pointer ml-2"
          onClick={(e) => {
            e.stopPropagation();
            toggleInput(index);
          }}
        >
          [展开]
        </span>
      </span>
    );
  };

  // 复制向量化结果
  const copyEmbedding = (index: number) => {
    if (!response || !response.data || index >= response.data.length) return;

    const embedding = response.data[index].embedding;
    navigator.clipboard.writeText(JSON.stringify(embedding))
      .then(() => {
        setCopiedIndex(index);
        setTimeout(() => {
          setCopiedIndex(null);
        }, 2000);
      })
      .catch(err => {
        console.error('Failed to copy: ', err);
      });
  };

  // 复制原始JSON响应
  const copyFullResponse = () => {
    if (!response) return;

    navigator.clipboard.writeText(JSON.stringify(response, null, 2))
      .then(() => {
        setCopiedIndex(-1); // 使用-1表示复制了完整响应
        setTimeout(() => {
          setCopiedIndex(null);
        }, 2000);
      })
      .catch(err => {
        console.error('Failed to copy full response: ', err);
      });
  };

  // 渲染结果，处理embedding的展开/收缩
  const renderResponse = () => {
    if (!response || !response.data || inputArray.length === 0) return null;

    return (
      <div className="space-y-4">
        <div className="text-sm font-medium mb-2">结果：</div>
        {response.data.map((item, index) => {
          const inputText = index < inputArray.length ? inputArray[index] : '未知输入';
          return (
            <div key={index} className="border rounded p-3 bg-white">
              <div className="flex items-center mb-2">
                <span className="font-medium mr-2">输入 {index + 1}:</span>
                <span className="text-gray-700">
                  {truncateText(inputText, index)}
                </span>
              </div>

              <div className="mt-2">
                <div className="flex items-start">
                  <span className="font-medium mr-2">向量:</span>
                  <div className="flex-1">
                    <div className="flex items-center">
                      <div
                        className="cursor-pointer text-blue-600 hover:text-blue-800 flex-1"
                        onClick={() => toggleEmbedding(index)}
                      >
                        [
                        {item.embedding[0].toFixed(8)},
                        {item.embedding[1].toFixed(8)},
                        {expandedEmbeddings.includes(index) ?
                          <span className="text-blue-600">[收起]</span> :
                          <span className="text-blue-600">... (点击展开查看全部)</span>
                        }
                        ]
                      </div>
                      <button
                        className="ml-2 p-1 rounded-md hover:bg-gray-100 focus:outline-none"
                        onClick={() => copyEmbedding(index)}
                        title="复制向量"
                      >
                        {copiedIndex === index ? (
                          <CheckIcon className="h-4 w-4 text-green-500" />
                        ) : (
                          <ClipboardIcon className="h-4 w-4 text-gray-500" />
                        )}
                      </button>
                    </div>

                    {expandedEmbeddings.includes(index) && (
                      <div className="mt-2 pl-2 max-h-60 overflow-y-auto border-l-2 border-gray-200">
                        <pre className="text-xs">
                          {JSON.stringify(item.embedding, null, 2)}
                        </pre>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          );
        })}

        <div className="mt-4 text-xs text-gray-500">
          <div className="flex items-center">
            <div className="font-medium">原始JSON响应:</div>
            <button
              className="ml-2 p-1 rounded-md hover:bg-gray-100 focus:outline-none"
              onClick={copyFullResponse}
              title="复制完整响应"
            >
              {copiedIndex === -1 ? (
                <CheckIcon className="h-4 w-4 text-green-500" />
              ) : (
                <ClipboardIcon className="h-4 w-4 text-gray-500" />
              )}
            </button>
          </div>
          <pre className="mt-1 p-2 bg-gray-100 rounded text-xs overflow-x-auto">
            {JSON.stringify({
              object: response.object,
              data: response.data.map(item => ({
                object: item.object,
                embedding: expandedEmbeddings.includes(item.index) ?
                  item.embedding :
                  [item.embedding[0], item.embedding[1], "..."],
                index: item.index
              }))
            }, null, 2)}
          </pre>
        </div>
      </div>
    );
  };

  return (
    <div className="container mx-auto px-4 py-3 max-w-5xl h-screen flex flex-col">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-4">
        <h1 className="text-2xl font-bold">向量化</h1>

        <div className="mt-2 md:mt-0 flex items-center">
          <span className="text-sm font-medium mr-2">模型:</span>
          <div className="p-2 rounded">
            {model || 'ke-embedding'}
          </div>
        </div>
      </div>

      <div className="mb-3 flex-shrink-0">
        <div className="mt-2">
          <div className="flex flex-col">
            <Textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              className="min-h-[80px] bg-white border border-gray-300 focus:border-gray-400 focus:ring-gray-300"
              placeholder="输入文本，每行一个..."
              disabled={isLoading}
            />
            <div className="flex justify-between items-center mt-2">
              <div className="text-xs text-gray-500">
                <p>提示：每行输入一个文本，点击响应中的 <ClipboardIcon className="inline h-3 w-3 text-gray-500" /> 图标可复制结果</p>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={getEmbeddings}
                className="text-gray-600 hover:bg-gray-100"
                disabled={isLoading}
              >
                {isLoading ? '处理中...' : '获取向量'}
              </Button>
            </div>
          </div>
        </div>
      </div>

      {error && (
        <div className="mb-4 p-3 bg-red-100 border border-red-300 text-red-700 rounded">
          {error}
        </div>
      )}

      <Card className="shadow-sm flex-grow overflow-hidden flex flex-col">
        <CardContent className="p-3 flex-grow overflow-hidden">
          <div className="h-full overflow-y-auto p-3 bg-white rounded">
            {isLoading ? (
              <div className="flex items-center justify-center h-full text-gray-500">
                <p>正在获取向量数据...</p>
              </div>
            ) : response ? (
              <div className="space-y-2">
                {renderResponse()}
              </div>
            ) : (
              <div className="flex items-center justify-center h-full text-gray-500">
                <p>点击"获取向量"按钮开始</p>
              </div>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
