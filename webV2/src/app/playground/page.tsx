"use client"

import { TopBar } from "@/components/top-bar"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ChatPlayground } from "@/components/playground/chat-playground"
import { ImagePlayground } from "@/components/playground/image-playground"
import { AudioPlayground } from "@/components/playground/audio-playground"
import { OCRPlayground } from "@/components/playground/ocr-playground"
import { MessageSquare, ImageIcon, Mic, FileText } from "lucide-react"

export default function PlaygroundPage() {
  return (
    <>
      <TopBar />

      <div className="flex-1 overflow-y-auto">
        <div className="container px-6 py-8">
          <div className="mb-8">
            <h1 className="mb-4 text-4xl font-bold">Playground</h1>
            <p className="text-lg text-muted-foreground">在线测试和体验我们的 AI 能力</p>
          </div>

          <Tabs defaultValue="chat" className="w-full">
            <TabsList className="grid w-full grid-cols-2 lg:w-auto lg:grid-cols-4">
              <TabsTrigger value="chat" className="gap-2">
                <MessageSquare className="h-4 w-4" />
                <span className="hidden sm:inline">ChatCompletion</span>
                <span className="sm:hidden">对话</span>
              </TabsTrigger>
              <TabsTrigger value="image" className="gap-2">
                <ImageIcon className="h-4 w-4" />
                <span className="hidden sm:inline">图像生成</span>
                <span className="sm:hidden">图像</span>
              </TabsTrigger>
              <TabsTrigger value="audio" className="gap-2">
                <Mic className="h-4 w-4" />
                <span className="hidden sm:inline">语音识别</span>
                <span className="sm:hidden">语音</span>
              </TabsTrigger>
              <TabsTrigger value="ocr" className="gap-2">
                <FileText className="h-4 w-4" />
                <span className="hidden sm:inline">OCR 识别</span>
                <span className="sm:hidden">OCR</span>
              </TabsTrigger>
            </TabsList>

            <TabsContent value="chat" className="mt-6">
              <ChatPlayground />
            </TabsContent>

            <TabsContent value="image" className="mt-6">
              <ImagePlayground />
            </TabsContent>

            <TabsContent value="audio" className="mt-6">
              <AudioPlayground />
            </TabsContent>

            <TabsContent value="ocr" className="mt-6">
              <OCRPlayground />
            </TabsContent>
          </Tabs>
        </div>
      </div>
    </>
  )
}
