"use client"

import { TopBar } from "@/components/top-bar"
import { Brain } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Card } from "@/components/ui/card"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Badge } from "@/components/ui/badge"
import { useState, useEffect } from "react"
import { usePlaygroundData } from "@/hooks/use-playground-data"
import { Model } from "@/lib/types/openapi"

export default function EmbeddingPlaygroundPage() {
  const [inputText, setInputText] = useState("")
  const [embeddings, setEmbeddings] = useState<Array<{ text: string; vector: number[] }>>([])
  const [modelList, setModelList] = useState<Model[]>([])
  const [selectedModel, setSelectedModel] = useState("")

  const { endpointDetails } = usePlaygroundData()

  // 监听 endpointDetails.models，当有值时设置模型列表和默认值
  useEffect(() => {
    if (endpointDetails?.models && endpointDetails.models.length > 0) {
      const models = endpointDetails.models
      setModelList(models)
      setSelectedModel(models[0].modelName || "")
    }
  }, [endpointDetails?.models])

  // Mock embedding generation
  const generateEmbedding = (text: string) => {
    return Array.from({ length: 1536 }, () => Math.random() * 2 - 1)
  }

  const calculateCosineSimilarity = (vec1: number[], vec2: number[]) => {
    const dotProduct = vec1.reduce((sum, val, i) => sum + val * vec2[i], 0)
    const mag1 = Math.sqrt(vec1.reduce((sum, val) => sum + val * val, 0))
    const mag2 = Math.sqrt(vec2.reduce((sum, val) => sum + val * val, 0))
    return dotProduct / (mag1 * mag2)
  }

  const calculateEuclideanDistance = (vec1: number[], vec2: number[]) => {
    return Math.sqrt(vec1.reduce((sum, val, i) => sum + Math.pow(val - vec2[i], 2), 0))
  }

  const handleGenerate = () => {
    const lines = inputText.split("\n").filter((line) => line.trim())
    const newEmbeddings = lines.map((line) => ({
      text: line,
      vector: generateEmbedding(line),
    }))
    setEmbeddings(newEmbeddings)
  }

  // 获取当前选中的模型对象
  const currentModel = modelList.find((m) => m.modelName === selectedModel)
  console.log(currentModel, ">>>currentModel")
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
              <Button onClick={handleGenerate} className="mt-4 w-full" size="lg" disabled={!inputText.trim()}>
                生成 Embedding 向量
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
        <div className="w-80 border-l bg-muted/30 p-6 overflow-auto">
          <div className="space-y-6">
            <div>
              <h3 className="mb-4 font-semibold">模型配置</h3>

              <div className="space-y-4">
                <div>
                  <Label className="mb-2 block">模型选择</Label>
                  <Select value={selectedModel} onValueChange={setSelectedModel}>
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="请选择模型">
                        {selectedModel || "请选择模型"}
                      </SelectValue>
                    </SelectTrigger>
                    <SelectContent className="max-h-[60vh]">
                      {modelList.map((model) => (
                        <SelectItem key={model.modelName} value={model.modelName || ""}>
                          <div className="flex flex-col gap-1 py-1">
                            <div className="font-medium">{model.modelName}</div>
                            {model.properties && (
                              <div className="text-xs text-muted-foreground space-y-0.5">
                                {model.properties.embedding_dimensions && (
                                  <div>维度: {model.properties.embedding_dimensions} 维</div>
                                )}
                                {model.properties.max_input_context && (
                                  <div>上下文: {model.properties.max_input_context} tokens</div>
                                )}
                              </div>
                            )}
                            {model.features && (
                              <div className="flex flex-wrap gap-1 mt-1">
                                {(Array.isArray(model.features) ? model.features : [model.features]).map(
                                  (feature, idx) => (
                                    <Badge key={idx} variant="secondary" className="text-[10px] px-1.5 py-0">
                                      {feature}
                                    </Badge>
                                  )
                                )}
                              </div>
                            )}
                          </div>
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>

                  {/* 选择后的模型信息显示在下方 */}
                  {currentModel && (
                    <div className="mt-3 rounded-lg border bg-muted/50 p-3 space-y-2">
                      {/* {currentModel.documentUrl && (
                        <a
                          href={currentModel.documentUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-xs text-primary hover:underline"
                        >
                          查看文档
                        </a>
                      )} */}
                      <div className="flex flex-wrap gap-1">
                        {currentModel.features &&
                          (Array.isArray(currentModel.features)
                            ? currentModel.features
                            : [currentModel.features]
                          ).map((feature, idx) => (
                            <Badge key={idx} variant="secondary" className="text-[10px] px-1.5 py-0">
                              {feature}
                            </Badge>
                          ))}
                      </div>
                    </div>
                  )}
                </div>

                <div>
                  <Label className="mb-2 block">编码格式</Label>
                  <Select defaultValue="float">
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="float">Float</SelectItem>
                      <SelectItem value="base64">Base64</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <Label className="mb-2 block">维度 (可选)</Label>
                  <Select defaultValue="1536">
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="1536">1536</SelectItem>
                      <SelectItem value="768">768</SelectItem>
                      <SelectItem value="512">512</SelectItem>
                      <SelectItem value="256">256</SelectItem>
                    </SelectContent>
                  </Select>
                  <p className="mt-2 text-xs text-muted-foreground">降低维度可以减少存储成本</p>
                </div>
              </div>
            </div>

            {currentModel && (
              <div className="border-t pt-6">
                <h4 className="mb-3 text-sm font-medium">模型信息</h4>
                <div className="space-y-2 text-xs text-muted-foreground">
                  {currentModel.properties?.embedding_dimensions && (
                    <div className="flex justify-between">
                      <span>向量维度</span>
                      <span className="font-medium">{currentModel.properties.embedding_dimensions} 维</span>
                    </div>
                  )}
                  {currentModel.properties?.max_input_context && (
                    <div className="flex justify-between">
                      <span>最大输入</span>
                      <span className="font-medium">{currentModel.properties.max_input_context} tokens</span>
                    </div>
                  )}
                  {currentModel.properties?.max_output_context && (
                    <div className="flex justify-between">
                      <span>最大输出</span>
                      <span className="font-medium">{currentModel.properties.max_output_context} tokens</span>
                    </div>
                  )}
                  {currentModel.priceDetails?.priceInfo && (
                    <div className="space-y-1">
                      <div className="font-medium">价格信息</div>
                      <div className="flex justify-between pl-2">
                        <span>输入价格</span>
                        <span className="font-medium">
                          {currentModel.priceDetails.priceInfo.input}/{currentModel.priceDetails.priceInfo.unit}
                        </span>
                      </div>
                      <div className="flex justify-between pl-2">
                        <span>输出价格</span>
                        <span className="font-medium">
                          {currentModel.priceDetails.priceInfo.output}/{currentModel.priceDetails.priceInfo.unit}
                        </span>
                      </div>
                      {currentModel.priceDetails.priceInfo.cachedRead !== undefined && (
                        <div className="flex justify-between pl-2">
                          <span>缓存读取</span>
                          <span className="font-medium">
                            {currentModel.priceDetails.priceInfo.cachedRead}/{currentModel.priceDetails.priceInfo.unit}
                          </span>
                        </div>
                      )}
                      {currentModel.priceDetails.priceInfo.cachedCreation !== undefined && (
                        <div className="flex justify-between pl-2">
                          <span>缓存创建</span>
                          <span className="font-medium">
                            {currentModel.priceDetails.priceInfo.cachedCreation}/{currentModel.priceDetails.priceInfo.unit}
                          </span>
                        </div>
                      )}
                    </div>
                  )}
                  {currentModel.ownerName && (
                    <div className="flex justify-between">
                      <span>提供商</span>
                      <span className="font-medium">{currentModel.ownerName}</span>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  )
}
