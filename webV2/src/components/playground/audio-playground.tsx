"use client"

import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Loader2, Mic, Upload, FileAudio } from "lucide-react"
import { Textarea } from "@/components/ui/textarea"

export function AudioPlayground() {
  const [isRecording, setIsRecording] = useState(false)
  const [isProcessing, setIsProcessing] = useState(false)
  const [transcript, setTranscript] = useState("")
  const [language, setLanguage] = useState("zh-CN")

  const handleRecord = () => {
    setIsRecording(!isRecording)
    if (!isRecording) {
      // 开始录音
      setTimeout(() => {
        setIsRecording(false)
        setIsProcessing(true)
        setTimeout(() => {
          setTranscript("这是一段模拟的语音识别结果。在实际应用中，这里会显示真实的语音转文字内容。")
          setIsProcessing(false)
        }, 1500)
      }, 3000)
    }
  }

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
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
                  <SelectItem value="zh-CN">简体中文</SelectItem>
                  <SelectItem value="en-US">英语 (美国)</SelectItem>
                  <SelectItem value="ja-JP">日语</SelectItem>
                  <SelectItem value="ko-KR">韩语</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Button
                onClick={handleRecord}
                disabled={isProcessing}
                variant={isRecording ? "destructive" : "default"}
                className="w-full"
              >
                <Mic className="mr-2 h-4 w-4" />
                {isRecording ? "停止录音" : "开始录音"}
              </Button>
            </div>

            <div className="relative">
              <div className="absolute inset-0 flex items-center">
                <span className="w-full border-t" />
              </div>
              <div className="relative flex justify-center text-xs uppercase">
                <span className="bg-card px-2 text-muted-foreground">或</span>
              </div>
            </div>

            <Button variant="outline" className="w-full bg-transparent">
              <Upload className="mr-2 h-4 w-4" />
              上传音频文件
            </Button>
          </CardContent>
        </Card>
      </div>

      <div className="lg:col-span-2">
        <Card className="h-[600px] flex flex-col">
          <CardHeader className="border-b">
            <CardTitle>识别结果</CardTitle>
          </CardHeader>
          <CardContent className="flex-1 flex items-center justify-center p-6">
            {isProcessing ? (
              <div className="text-center">
                <Loader2 className="mx-auto mb-4 h-12 w-12 animate-spin text-primary" />
                <p className="text-muted-foreground">正在识别语音...</p>
              </div>
            ) : transcript ? (
              <div className="w-full">
                <Textarea value={transcript} readOnly className="min-h-[400px] text-base" />
              </div>
            ) : (
              <div className="text-center text-muted-foreground">
                <FileAudio className="mx-auto mb-4 h-12 w-12 opacity-50" />
                <p>开始录音或上传音频文件</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
