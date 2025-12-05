"use client"

import { TopBar } from "@/components/top-bar"
import { ScanText, Upload, Download, Copy } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"

export default function OCRPlaygroundPage() {
  return (
    <div className="flex h-screen flex-col">
      <TopBar title="OCR Playground" description="光学字符识别" />
      <main className="flex flex-1 overflow-hidden">
        {/* 左侧主要内容区域 */}
        <div className="flex-1 overflow-auto p-6">
          <div className="mx-auto max-w-4xl space-y-6">
            <Card className="p-6">
              <div className="mb-4 flex items-center gap-2">
                <ScanText className="h-5 w-5 text-primary" />
                <h3 className="font-semibold">上传图片</h3>
              </div>
              <div className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-muted-foreground/25 p-12 text-center">
                <Upload className="mb-4 h-12 w-12 text-muted-foreground" />
                <p className="mb-2 text-sm font-medium">拖拽图片文件或点击上传</p>
                <p className="text-xs text-muted-foreground">支持 JPG, PNG, PDF 格式，最大 10MB</p>
              </div>
              <Button className="mt-4 w-full" size="lg">
                开始识别
              </Button>
            </Card>

            <Card className="p-6">
              <div className="mb-4 flex items-center justify-between">
                <h3 className="font-semibold">识别结果</h3>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm">
                    <Copy className="mr-2 h-4 w-4" />
                    复制文本
                  </Button>
                  <Button variant="outline" size="sm">
                    <Download className="mr-2 h-4 w-4" />
                    下载 JSON
                  </Button>
                </div>
              </div>
              <div className="min-h-[300px] rounded-lg bg-muted p-4 text-sm font-mono">
                <p className="text-muted-foreground">上传图片后，识别的文字将显示在这里...</p>
              </div>
            </Card>
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
                  <Select defaultValue="gpt-4o">
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="gpt-4o">GPT-4o (推荐)</SelectItem>
                      <SelectItem value="gpt-4o-mini">GPT-4o Mini</SelectItem>
                      <SelectItem value="claude-3-opus">Claude 3 Opus</SelectItem>
                    </SelectContent>
                  </Select>
                  <p className="mt-2 text-xs text-muted-foreground">支持多语言文字识别</p>
                </div>

                <div>
                  <Label className="mb-2 block">识别语言</Label>
                  <Select defaultValue="auto">
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="auto">自动检测</SelectItem>
                      <SelectItem value="zh">中文</SelectItem>
                      <SelectItem value="en">English</SelectItem>
                      <SelectItem value="ja">日本語</SelectItem>
                      <SelectItem value="multi">多语言混合</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <Label className="mb-2 block">输出格式</Label>
                  <Select defaultValue="text">
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="text">纯文本</SelectItem>
                      <SelectItem value="json">JSON (含位置)</SelectItem>
                      <SelectItem value="markdown">Markdown</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="flex items-center justify-between">
                  <Label>保留格式</Label>
                  <Switch defaultChecked />
                </div>

                <div className="flex items-center justify-between">
                  <Label>表格识别</Label>
                  <Switch defaultChecked />
                </div>

                <div className="flex items-center justify-between">
                  <Label>公式识别</Label>
                  <Switch />
                </div>
              </div>
            </div>

            <div className="border-t pt-6">
              <h4 className="mb-3 text-sm font-medium">模型信息</h4>
              <div className="space-y-2 text-xs text-muted-foreground">
                <div className="flex justify-between">
                  <span>最大文件大小</span>
                  <span className="font-medium">10 MB</span>
                </div>
                <div className="flex justify-between">
                  <span>支持格式</span>
                  <span className="font-medium">JPG, PNG, PDF</span>
                </div>
                <div className="flex justify-between">
                  <span>识别准确率</span>
                  <span className="font-medium">&gt; 98%</span>
                </div>
                <div className="flex justify-between">
                  <span>价格</span>
                  <span className="font-medium">$0.002/图片</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}
