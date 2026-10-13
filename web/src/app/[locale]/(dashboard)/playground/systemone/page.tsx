"use client"

import { useEffect, useState } from "react"
import { useSearchParams } from "next/navigation"
import { TopBar } from "@/components/layout"
import { Button } from "@/components/common/button"
import { Card } from "@/components/common/card"
import { Label } from "@/components/common/label"
import { Textarea } from "@/components/common/textarea"
import { Combobox, ComboboxOption } from "@/components/ui/combobox"
import { useModelList } from "../hooks/useModelList"
import { evaluateSystemOne, SystemOneResponse } from "@/lib/api/systemone"

export default function SystemOnePlaygroundPage() {
  const { models, loading } = useModelList("/v1/systemone")
  const searchParams = useSearchParams()
  const [model, setModel] = useState("")
  const [state, setState] = useState("")
  const [questions, setQuestions] = useState("{}")
  const [result, setResult] = useState<SystemOneResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (models.length === 0) return
    setModel(searchParams.get("model") || model || models[0].modelName)
  }, [models, searchParams, model])

  const options: ComboboxOption[] = models.map(item => ({ value: item.modelName, label: item.modelName }))
  const submit = async () => {
    setSubmitting(true); setError(null)
    try {
      const parsed = JSON.parse(questions || "{}")
      setResult(await evaluateSystemOne({ model, state, questions: parsed }))
    } catch (err: any) {
      setResult(null); setError(err?.message || "请求失败，请检查 questions JSON")
    } finally { setSubmitting(false) }
  }

  return <div className="flex h-screen flex-col">
    <TopBar title="状态评估 Playground" description="使用 SystemOne 进行状态评估" />
    <main className="flex-1 overflow-auto p-6"><div className="mx-auto max-w-4xl space-y-6">
      <Card className="space-y-4 p-6">
        <div><Label className="mb-2 block">模型</Label><Combobox value={model} onValueChange={setModel} options={options} placeholder={loading ? "加载中..." : "选择模型"} /></div>
        <div><Label className="mb-2 block">状态描述</Label><Textarea value={state} onChange={e => setState(e.target.value)} placeholder="输入需要评估的状态" className="min-h-[140px]" /></div>
        <div><Label className="mb-2 block">问题（JSON）</Label><Textarea value={questions} onChange={e => setQuestions(e.target.value)} className="min-h-[120px] font-mono" /></div>
        <Button className="w-full" size="lg" disabled={!model || !state.trim() || submitting} onClick={submit}>{submitting ? "评估中..." : "开始评估"}</Button>
        {error && <p className="text-sm text-destructive">{error}</p>}
      </Card>
      {result && <Card className="p-6"><pre className="overflow-auto whitespace-pre-wrap text-sm">{JSON.stringify(result, null, 2)}</pre></Card>}
    </div></main>
  </div>
}
