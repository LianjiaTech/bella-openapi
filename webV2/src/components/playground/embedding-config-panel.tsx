"use client"

import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Badge } from "@/components/ui/badge"
import { Model } from "@/lib/types/openapi"

interface EmbeddingConfigPanelProps {
  modelList: Model[]
  selectedModel: string
  onModelChange: (model: string) => void
  encodingFormat: string
  onEncodingFormatChange: (format: string) => void
  dimensions?: string
  onDimensionsChange?: (dimensions: string) => void
}

export function EmbeddingConfigPanel({
  modelList,
  selectedModel,
  onModelChange,
  encodingFormat,
  onEncodingFormatChange,
  dimensions = "1536",
  onDimensionsChange,
}: EmbeddingConfigPanelProps) {
  // 获取当前选中的模型对象
  const currentModel = modelList.find((m) => m.modelName === selectedModel)

  return (
    <div className="w-80 border-l bg-muted/30 p-6 overflow-auto">
      <div className="space-y-6">
        <div>
          <h3 className="mb-4 font-semibold">模型配置</h3>

          <div className="space-y-4">
            <div>
              <Label className="mb-2 block">模型选择</Label>
              <Select value={selectedModel} onValueChange={onModelChange}>
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
              <Select value={encodingFormat} onValueChange={onEncodingFormatChange}>
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
              <Select value={dimensions} onValueChange={onDimensionsChange}>
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
  )
}
