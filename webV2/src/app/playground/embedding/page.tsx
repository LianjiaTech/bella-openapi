"use client"

import { TopBar } from "@/components/top-bar"
import { Brain } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Card } from "@/components/ui/card"
import { useState, useEffect } from "react"
import { usePlaygroundData } from "@/hooks/use-playground-data"
import { Model } from "@/lib/types/openapi"
import { useUser } from "@/lib/context/user-context"
import { openapi } from "@/lib/api/openapi"
import { EmbeddingConfigPanel } from "@/components/playground/embedding-config-panel"

export default function EmbeddingPlaygroundPage() {
  const [inputText, setInputText] = useState("")
  const [embeddings, setEmbeddings] = useState<Array<{ text: string; vector: number[] }>>([])
  const [modelList, setModelList] = useState<Model[]>([])
  const [selectedModel, setSelectedModel] = useState("")
  const [encodingFormat, setEncodingFormat] = useState("float")
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const { endpointDetails } = usePlaygroundData()
  const { userInfo } = useUser()

  // 监听 endpointDetails.models，当有值时设置模型列表和默认值
  useEffect(() => {
    if (endpointDetails?.models && endpointDetails.models.length > 0) {
      const models = endpointDetails.models
      setModelList(models)
      setSelectedModel(models[0].modelName || "")
    }
  }, [endpointDetails?.models])

  const calculateCosineSimilarity = (vec1: number[], vec2: number[]) => {
    const dotProduct = vec1.reduce((sum, val, i) => sum + val * vec2[i], 0)
    const mag1 = Math.sqrt(vec1.reduce((sum, val) => sum + val * val, 0))
    const mag2 = Math.sqrt(vec2.reduce((sum, val) => sum + val * val, 0))
    return dotProduct / (mag1 * mag2)
  }

  const calculateEuclideanDistance = (vec1: number[], vec2: number[]) => {
    return Math.sqrt(vec1.reduce((sum, val, i) => sum + Math.pow(val - vec2[i], 2), 0))
  }

  const handleGenerate = async () => {
    const lines = inputText.split("\n").filter((line) => line.trim())

    if (!selectedModel || lines.length === 0) {
      return
    }

    setIsLoading(true)
    setError(null)

    try {
      const response = await openapi.post('/v1/embeddings', {
        encoding_format: encodingFormat,
        input: lines,
        model: selectedModel,
        user: userInfo?.userId || " 1000000030873314"
      })

      // 处理 API 响应，兼容不同的响应结构
      // API 直接返回 {data: [...], usage: ...} 格式
      const embeddingData = response.data?.data || response.data

      if (embeddingData && Array.isArray(embeddingData)) {
        const newEmbeddings = embeddingData.map((item: any, index: number) => ({
          text: lines[index],
          vector: item.embedding
        }))
        console.log('生成的 embeddings:', newEmbeddings)
        setEmbeddings(newEmbeddings)
      } else {
        console.error('响应数据格式不正确:', response.data)
        setError('响应数据格式错误，请检查 API 返回结果')
      }
    } catch (err: any) {
      console.error('生成 embedding 失败:', err)
      setError(err?.response?.data?.message || err?.message || '生成失败，请重试')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="flex h-screen flex-col">
      <TopBar title="Embedding Playground" description="生成文本向量表示并计算相似度" />
      <main className="flex flex-1 overflow-hidden">
        {/* 左侧主要内容区域 */}
        <div className="flex-1 overflow-auto p-6">
          <div className="mx-auto max-w-4xl space-y-6">
            <Card className="p-6">
              <div className="mb-4 flex items-center gap-2">
                <Brain className="h-5 w-5 text-primary" />
                <h3 className="font-semibold">输入文本（每行一条）</h3>
              </div>
              <Textarea
                placeholder="输入多行文本，每行将生成独立的 embedding 向量...&#10;例如：&#10;什么是人工智能？&#10;AI 的应用场景有哪些？&#10;机器学习的基本原理"
                className="min-h-[200px] font-mono"
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
              />
              {error && (
                <div className="mt-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-600 dark:border-red-900 dark:bg-red-950 dark:text-red-400">
                  {error}
                </div>
              )}
              <Button
                onClick={handleGenerate}
                className="mt-4 w-full"
                size="lg"
                disabled={!inputText.trim() || !selectedModel || isLoading}
              >
                {isLoading ? "生成中..." : "生成 Embedding 向量"}
              </Button>
            </Card>

            {embeddings.length > 0 && (
              <>
                <Card className="p-6">
                  <h3 className="mb-4 font-semibold">生成的向量</h3>
                  <div className="space-y-3">
                    {embeddings.map((item, idx) => (
                      <div key={idx} className="rounded-lg border bg-card p-4">
                        <div className="mb-2 flex items-center justify-between">
                          <span className="text-sm font-medium">文本 {idx + 1}</span>
                        </div>
                        <p className="mb-2 text-sm text-muted-foreground">{item.text}</p>
                        <p className="truncate text-xs font-mono text-muted-foreground">
                          [
                          {item.vector
                            .slice(0, 5)
                            .map((v) => v.toFixed(4))
                            .join(", ")}
                          ...]
                        </p>
                      </div>
                    ))}
                  </div>
                </Card>

                {embeddings.length > 1 && (
                  <Card className="p-6">
                    <h3 className="mb-4 font-semibold">相似度距离矩阵</h3>
                    <div className="overflow-auto">
                      <div className="grid gap-4">
                        {/* 余弦相似度矩阵 */}
                        <div>
                          <h4 className="mb-3 text-sm font-medium">余弦相似度矩阵</h4>
                          <div className="inline-block min-w-full">
                            <table className="border-collapse">
                              <thead>
                                <tr>
                                  <th className="border border-border bg-muted p-2 text-xs font-medium"></th>
                                  {embeddings.map((_, idx) => (
                                    <th key={idx} className="border border-border bg-muted p-2 text-xs font-medium">
                                      文本 {idx + 1}
                                    </th>
                                  ))}
                                </tr>
                              </thead>
                              <tbody>
                                {embeddings.map((item1, i) => (
                                  <tr key={i}>
                                    <th className="border border-border bg-muted p-2 text-xs font-medium">
                                      文本 {i + 1}
                                    </th>
                                    {embeddings.map((item2, j) => {
                                      const similarity =
                                        i === j ? 1.0 : calculateCosineSimilarity(item1.vector, item2.vector)
                                      const colorClass =
                                        i === j
                                          ? "bg-muted"
                                          : similarity > 0.8
                                            ? "bg-green-100 dark:bg-green-950"
                                            : similarity > 0.5
                                              ? "bg-yellow-100 dark:bg-yellow-950"
                                              : "bg-red-100 dark:bg-red-950"
                                      return (
                                        <td
                                          key={j}
                                          className={`border border-border p-2 text-center text-xs font-mono ${colorClass}`}
                                        >
                                          {similarity.toFixed(4)}
                                        </td>
                                      )
                                    })}
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </div>

                        {/* 欧式距离矩阵 */}
                        <div>
                          <h4 className="mb-3 text-sm font-medium">欧式距离矩阵</h4>
                          <div className="inline-block min-w-full">
                            <table className="border-collapse">
                              <thead>
                                <tr>
                                  <th className="border border-border bg-muted p-2 text-xs font-medium"></th>
                                  {embeddings.map((_, idx) => (
                                    <th key={idx} className="border border-border bg-muted p-2 text-xs font-medium">
                                      文本 {idx + 1}
                                    </th>
                                  ))}
                                </tr>
                              </thead>
                              <tbody>
                                {embeddings.map((item1, i) => (
                                  <tr key={i}>
                                    <th className="border border-border bg-muted p-2 text-xs font-medium">
                                      文本 {i + 1}
                                    </th>
                                    {embeddings.map((item2, j) => {
                                      const distance =
                                        i === j ? 0.0 : calculateEuclideanDistance(item1.vector, item2.vector)
                                      return (
                                        <td
                                          key={j}
                                          className="border border-border bg-muted p-2 text-center text-xs font-mono"
                                        >
                                          {distance.toFixed(4)}
                                        </td>
                                      )
                                    })}
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="mt-4 rounded-lg border bg-card p-3 text-xs text-muted-foreground">
                      <p className="mb-2 font-medium">矩阵说明：</p>
                      <ul className="space-y-1 list-disc list-inside">
                        <li>余弦相似度：1.0表示完全相同，越接近1越相似，越接近0越不相似</li>
                        <li>欧式距离：0表示完全相同，数值越小表示越相似</li>
                        <li>对角线元素表示文本与自身的相似度</li>
                      </ul>
                    </div>
                  </Card>
                )}
              </>
            )}
          </div>
        </div>

        {/* 右侧配置面板 */}
        <EmbeddingConfigPanel
          modelList={modelList}
          selectedModel={selectedModel}
          onModelChange={setSelectedModel}
          encodingFormat={encodingFormat}
          onEncodingFormatChange={setEncodingFormat}
        />
      </main>
    </div>
  )
}
