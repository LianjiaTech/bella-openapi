"use client"

import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Loader2, Wand2, Download } from "lucide-react"

export function ImagePlayground() {
  const [prompt, setPrompt] = useState("")
  const [isGenerating, setIsGenerating] = useState(false)
  const [generatedImage, setGeneratedImage] = useState<string | null>(null)
  const [size, setSize] = useState("1024x1024")
  const [style, setStyle] = useState("realistic")

  const handleGenerate = async () => {
    if (!prompt.trim()) return

    setIsGenerating(true)
    // 模拟图像生成
    setTimeout(() => {
      setGeneratedImage("/ai-generated-art.png")
      setIsGenerating(false)
    }, 2000)
  }

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
      <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">生成配置</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label>提示词</Label>
              <Textarea
                placeholder="描述您想要生成的图像..."
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                className="min-h-[120px]"
              />
            </div>

            <div className="space-y-2">
              <Label>图像尺寸</Label>
              <Select value={size} onValueChange={setSize}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="512x512">512 x 512</SelectItem>
                  <SelectItem value="1024x1024">1024 x 1024</SelectItem>
                  <SelectItem value="1024x1792">1024 x 1792</SelectItem>
                  <SelectItem value="1792x1024">1792 x 1024</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label>风格</Label>
              <Select value={style} onValueChange={setStyle}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="realistic">写实</SelectItem>
                  <SelectItem value="artistic">艺术</SelectItem>
                  <SelectItem value="anime">动漫</SelectItem>
                  <SelectItem value="3d">3D 渲染</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <Button onClick={handleGenerate} disabled={isGenerating || !prompt.trim()} className="w-full">
              {isGenerating ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  生成中...
                </>
              ) : (
                <>
                  <Wand2 className="mr-2 h-4 w-4" />
                  生成图像
                </>
              )}
            </Button>
          </CardContent>
        </Card>
      </div>

      <div className="lg:col-span-2">
        <Card className="h-[600px]">
          <CardHeader className="border-b">
            <CardTitle className="flex items-center justify-between">
              <span>生成结果</span>
              {generatedImage && (
                <Button variant="outline" size="sm">
                  <Download className="mr-2 h-4 w-4" />
                  下载
                </Button>
              )}
            </CardTitle>
          </CardHeader>
          <CardContent className="flex h-[calc(600px-5rem)] items-center justify-center p-6">
            {isGenerating ? (
              <div className="text-center">
                <Loader2 className="mx-auto mb-4 h-12 w-12 animate-spin text-primary" />
                <p className="text-muted-foreground">正在生成图像...</p>
              </div>
            ) : generatedImage ? (
              <img
                src={generatedImage || "/placeholder.svg"}
                alt="Generated"
                className="max-h-full max-w-full rounded-lg"
              />
            ) : (
              <div className="text-center text-muted-foreground">
                <Wand2 className="mx-auto mb-4 h-12 w-12 opacity-50" />
                <p>输入提示词并点击生成</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
