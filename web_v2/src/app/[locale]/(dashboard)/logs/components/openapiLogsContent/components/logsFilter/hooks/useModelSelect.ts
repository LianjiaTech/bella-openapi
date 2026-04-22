import { ComboboxOption } from "@/components/ui/combobox"
import * as React from "react"
import { Model } from "@/lib/types/openapi"
import { getModelsForSelection } from "@/lib/api/status"

export function useModelSelect(endpoint: string): {
  models: Model[]
  modelsLoading: boolean
  modelsError: string | null
  modelOptions: ComboboxOption[]
} {
  const [models, setModels] = React.useState<Model[]>([])
  const [modelsLoading, setModelsLoading] = React.useState(false)
  const [modelsError, setModelsError] = React.useState<string | null>(null)

  React.useEffect(() => {
    fetchModels()
  }, [endpoint])

const fetchModels = async () => {
  setModelsLoading(true)
  setModelsError(null)
  try {
    const data = await getModelsForSelection(endpoint, "active")
    setModels(data)
  } catch (err) {
    console.error("Failed to fetch models:", err)
    setModelsError("获取模型列表失败")
    setModels([])
  } finally {
    setModelsLoading(false)
  }
}

// 转换为 Combobox 选项格式
const modelOptions: ComboboxOption[] = React.useMemo(() => {
  return models.map((model) => ({
    value: model.modelName,
    label: model.modelName,
  }))
}, [models])

return {
  models,
  modelsLoading,
  modelsError,
  modelOptions
}
}
