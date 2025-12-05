"use client"

import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Loader2, Upload, FileText, ImageIcon } from "lucide-react"
import { Textarea } from "@/components/ui/textarea"

export function OCRPlayground() {
  const [isProcessing, setIsProcessing] = useState(false)
  const [extractedText, setExtractedText] = useState("")
  const [uploadedImage, setUploadedImage] = useState<string | null>(null)
  const [language, setLanguage] = useState("auto")

  const handleUpload = () => {
    // 模拟图片上传和 OCR 识别
    setUploadedImage("/document-with-text.png")
    setIsProcessing(true)
    setTimeout(() => {
      setExtractedText(
        "这是一段模拟的 OCR 识别结果。\n\n在实际应用中，这里会显示从图片中提取的真实文字内容。\n\n支持印刷体、手写体以及多种语言的识别。",
      )
      setIsProcessing(false)
    }, 2000)
  }

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">识别配置</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label>语言</Label>
              <Select value={language} onValueChange={setLanguage}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="auto">自动检测</SelectItem>
                  <SelectItem value="zh">中文</SelectItem>
                  <SelectItem value="en">英语</SelectItem>
                  <SelectItem value="ja">日语</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <Button onClick={handleUpload} disabled={isProcessing} className="w-full">
              <Upload className="mr-2 h-4 w-4" />
              上传图片
            </Button>
          </CardContent>
        </Card>

        <Card className="h-[400px]">
          <CardHeader className="border-b">
            <CardTitle className="text-lg">原图</CardTitle>
          </CardHeader>
          <CardContent className="flex h-[calc(400px-4rem)] items-center justify-center p-6">
            {uploadedImage ? (
              <img
                src={uploadedImage || "/placeholder.svg"}
                alt="Uploaded"
                className="max-h-full max-w-full rounded-lg"
              />
            ) : (
              <div className="text-center text-muted-foreground">
                <ImageIcon className="mx-auto mb-4 h-12 w-12 opacity-50" />
                <p>上传图片进行识别</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <div>
        <Card className="h-full min-h-[600px] flex flex-col">
          <CardHeader className="border-b">
            <CardTitle>识别结果</CardTitle>
          </CardHeader>
          <CardContent className="flex-1 flex items-center justify-center p-6">
            {isProcessing ? (
              <div className="text-center">
                <Loader2 className="mx-auto mb-4 h-12 w-12 animate-spin text-primary" />
                <p className="text-muted-foreground">正在识别文字...</p>
              </div>
            ) : extractedText ? (
              <div className="w-full">
                <Textarea value={extractedText} readOnly className="min-h-[500px] text-base" />
              </div>
            ) : (
              <div className="text-center text-muted-foreground">
                <FileText className="mx-auto mb-4 h-12 w-12 opacity-50" />
                <p>上传图片后将显示识别结果</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
