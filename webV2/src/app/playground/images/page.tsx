"use client"

import type React from "react"
import { Badge } from "@/components/ui/badge"
import { TopBar } from "@/components/top-bar"
import { ImageIcon, Wand2, Upload, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Card } from "@/components/ui/card"
import { Slider } from "@/components/ui/slider"
import { Select, SelectTrigger, SelectValue, SelectContent, SelectItem } from "@/components/ui/select"
import { Label } from "@/components/ui/label"
import { useState } from "react"

export default function ImagesPlaygroundPage() {
  const [selectedModel, setSelectedModel] = useState("dall-e-3")
  const [uploadedImage, setUploadedImage] = useState<string | null>(null)

  // 检查模型是否支持图生图
  const supportsImageInput = ["stable-diffusion-xl", "midjourney"].includes(selectedModel)

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      const reader = new FileReader()
      reader.onloadend = () => {
        setUploadedImage(reader.result as string)
      }
      reader.readAsDataURL(file)
    }
  }

  return (
    <div className="flex h-screen flex-col">
      <TopBar title="Images Playground" description="AI 图像生成" />
      <main className="flex flex-1 overflow-hidden">
        {/* 左侧内容区域 */}
        <div className="flex-1 overflow-auto p-6">
          <div className="mx-auto max-w-4xl space-y-6">
            {supportsImageInput && (
              <Card className="p-6">
                <div className="mb-4 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Upload className="h-5 w-5 text-primary" />
                    <h3 className="font-semibold">参考图片 (图生图)</h3>
                  </div>
                  {uploadedImage && (
                    <Button variant="ghost" size="sm" onClick={() => setUploadedImage(null)}>
                      <X className="h-4 w-4" />
                    </Button>
                  )}
                </div>
                {!uploadedImage ? (
                  <label className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-muted-foreground/25 p-8 cursor-pointer hover:border-primary/50 transition-colors">
                    <Upload className="mb-3 h-10 w-10 text-muted-foreground" />
                    <p className="mb-1 text-sm font-medium">点击上传参考图片</p>
                    <p className="text-xs text-muted-foreground">支持 JPG, PNG, WebP 格式</p>
                    <input type="file" accept="image/*" className="hidden" onChange={handleImageUpload} />
                  </label>
                ) : (
                  <div className="relative rounded-lg overflow-hidden border">
                    <img
                      src={uploadedImage || "/placeholder.svg"}
                      alt="Uploaded reference"
                      className="w-full h-auto max-h-64 object-contain bg-muted"
                    />
                  </div>
                )}
              </Card>
            )}

            <Card className="p-6">
              <div className="mb-4 flex items-center gap-2">
                <Wand2 className="h-5 w-5 text-primary" />
                <h3 className="font-semibold">{supportsImageInput && uploadedImage ? "图像修改描述" : "图像描述"}</h3>
              </div>
              <Textarea
                placeholder={
                  supportsImageInput && uploadedImage
                    ? "描述你想如何修改这张图片...&#10;&#10;例如: 保持主体不变，将背景改为夕阳下的海滩"
                    : "描述你想生成的图像...&#10;&#10;例如: 一只在太空中漂浮的宇航猫，周围是星云和星星，赛博朋克风格，8k 高清"
                }
                className="min-h-[150px]"
              />
              <Button className="mt-4 w-full" size="lg">
                {supportsImageInput && uploadedImage ? "生成修改后的图像" : "生成图像"}
              </Button>
            </Card>

            <Card className="p-6">
              <h3 className="mb-4 font-semibold">生成结果</h3>
              <div className="grid gap-4 md:grid-cols-2">
                <div className="flex aspect-square items-center justify-center rounded-lg bg-muted">
                  <ImageIcon className="h-24 w-24 text-muted-foreground" />
                </div>
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
                  <Select value={selectedModel} onValueChange={setSelectedModel}>
                    <SelectTrigger className="w-full">
                      <SelectValue>
                        {selectedModel === "dall-e-3" && "DALL-E 3"}
                        {selectedModel === "dall-e-2" && "DALL-E 2"}
                        {selectedModel === "stable-diffusion-xl" && "Stable Diffusion XL"}
                        {selectedModel === "midjourney" && "Midjourney"}
                      </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="dall-e-3">
                        <div className="flex flex-col gap-1 py-1">
                          <div className="font-medium">DALL-E 3</div>
                          <div className="text-xs text-muted-foreground">最新的图像生成模型，质量最高</div>
                          <div className="flex flex-wrap gap-1 mt-1">
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              文生图
                            </Badge>
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              高清
                            </Badge>
                          </div>
                        </div>
                      </SelectItem>
                      <SelectItem value="dall-e-2">
                        <div className="flex flex-col gap-1 py-1">
                          <div className="font-medium">DALL-E 2</div>
                          <div className="text-xs text-muted-foreground">快速生成，性价比高</div>
                          <div className="flex flex-wrap gap-1 mt-1">
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              文生图
                            </Badge>
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              快速
                            </Badge>
                          </div>
                        </div>
                      </SelectItem>
                      <SelectItem value="stable-diffusion-xl">
                        <div className="flex flex-col gap-1 py-1">
                          <div className="font-medium">Stable Diffusion XL</div>
                          <div className="text-xs text-muted-foreground">开源模型，支持图生图</div>
                          <div className="flex flex-wrap gap-1 mt-1">
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              文生图
                            </Badge>
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              图生图
                            </Badge>
                          </div>
                        </div>
                      </SelectItem>
                      <SelectItem value="midjourney">
                        <div className="flex flex-col gap-1 py-1">
                          <div className="font-medium">Midjourney</div>
                          <div className="text-xs text-muted-foreground">艺术风格强，支持图生图</div>
                          <div className="flex flex-wrap gap-1 mt-1">
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              文生图
                            </Badge>
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              图生图
                            </Badge>
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                              艺术
                            </Badge>
                          </div>
                        </div>
                      </SelectItem>
                    </SelectContent>
                  </Select>

                  {/* 选择后的模型信息显示在下方 */}
                  <div className="mt-3 rounded-lg border bg-muted/50 p-3 space-y-2">
                    <p className="text-xs text-muted-foreground leading-relaxed">
                      {selectedModel === "dall-e-3" && "最新的图像生成模型，质量最高"}
                      {selectedModel === "dall-e-2" && "快速生成，性价比高"}
                      {selectedModel === "stable-diffusion-xl" && "开源模型，支持图生图"}
                      {selectedModel === "midjourney" && "艺术风格强，支持图生图"}
                    </p>
                    <div className="flex flex-wrap gap-1">
                      {selectedModel === "dall-e-3" && (
                        <>
                          <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                            文生图
                          </Badge>
                          <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                            高清
                          </Badge>
                        </>
                      )}
                      {selectedModel === "dall-e-2" && (
                        <>
                          <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                            文生图
                          </Badge>
                          <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                            快速
                          </Badge>
                        </>
                      )}
                      {selectedModel === "stable-diffusion-xl" && (
                        <>
                          <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                            文生图
                          </Badge>
                          <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                            图生图
                          </Badge>
                        </>
                      )}
                      {selectedModel === "midjourney" && (
                        <>
                          <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                            文生图
                          </Badge>
                          <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                            图生图
                          </Badge>
                          <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                            艺术
                          </Badge>
                        </>
                      )}
                    </div>
                  </div>
                </div>

                <div>
                  <Label className="mb-2 block">图像尺寸</Label>
                  <Select defaultValue="1024x1024">
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="1024x1024">1024 × 1024 (方形)</SelectItem>
                      <SelectItem value="1792x1024">1792 × 1024 (横向)</SelectItem>
                      <SelectItem value="1024x1792">1024 × 1792 (纵向)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <Label className="mb-2 block">图像质量</Label>
                  <Select defaultValue="hd">
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="hd">HD (高清)</SelectItem>
                      <SelectItem value="standard">Standard (标准)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div>
                  <Label className="mb-2 block">风格</Label>
                  <Select defaultValue="vivid">
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="vivid">Vivid (鲜艳)</SelectItem>
                      <SelectItem value="natural">Natural (自然)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                {supportsImageInput && (
                  <div>
                    <Label className="mb-2 block">图像强度: 0.75</Label>
                    <Slider defaultValue={[0.75]} min={0} max={1} step={0.05} />
                    <p className="mt-2 text-xs text-muted-foreground">控制参考图片的影响程度</p>
                  </div>
                )}

                <div>
                  <Label className="mb-2 block">生成数量: 1</Label>
                  <Slider defaultValue={[1]} min={1} max={4} step={1} />
                  <p className="mt-2 text-xs text-muted-foreground">一次生成 1-4 张图片</p>
                </div>
              </div>
            </div>

            <div className="border-t pt-6">
              <h4 className="mb-3 text-sm font-medium">模型信息</h4>
              <div className="space-y-2 text-xs text-muted-foreground">
                <div className="flex justify-between">
                  <span>最大提示词</span>
                  <span className="font-medium">4000 字符</span>
                </div>
                <div className="flex justify-between">
                  <span>生成时间</span>
                  <span className="font-medium">~30秒</span>
                </div>
                <div className="flex justify-between">
                  <span>价格 (HD)</span>
                  <span className="font-medium">$0.08/张</span>
                </div>
                <div className="flex justify-between">
                  <span>价格 (标准)</span>
                  <span className="font-medium">$0.04/张</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}
