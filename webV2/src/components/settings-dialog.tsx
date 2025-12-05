"use client"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { useLanguage } from "@/components/language-provider"
import { useTheme } from "@/components/theme-provider"
import type { ReactNode } from "react"
import { Settings } from "lucide-react"

export function SettingsDialog({ children }: { children?: ReactNode }) {
  const { language, setLanguage, t } = useLanguage()
  const { theme, setTheme } = useTheme()

  return (
    <Dialog>
      <DialogTrigger asChild>
        {children || (
          <Button variant="ghost" size="icon" className="relative">
            <Settings className="h-5 w-5" />
            <span className="sr-only">设置</span>
          </Button>
        )}
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("settings")}</DialogTitle>
          <DialogDescription>管理您的偏好设置</DialogDescription>
        </DialogHeader>
        <div className="space-y-6 py-4">
          {/* Language Settings */}
          <div className="space-y-3">
            <Label className="text-sm font-medium">{t("language")}</Label>
            <RadioGroup value={language} onValueChange={(value) => setLanguage(value as "zh-CN" | "en-US")}>
              <div className="flex items-center space-x-2">
                <RadioGroupItem value="zh-CN" id="zh-CN-dialog" />
                <Label htmlFor="zh-CN-dialog" className="cursor-pointer font-normal">
                  {t("chinese")}
                </Label>
              </div>
              <div className="flex items-center space-x-2">
                <RadioGroupItem value="en-US" id="en-US-dialog" />
                <Label htmlFor="en-US-dialog" className="cursor-pointer font-normal">
                  {t("english")}
                </Label>
              </div>
            </RadioGroup>
          </div>

          {/* Theme Settings */}
          <div className="space-y-3">
            <Label className="text-sm font-medium">{t("theme")}</Label>
            <RadioGroup value={theme} onValueChange={setTheme}>
              <div className="flex items-center space-x-2">
                <RadioGroupItem value="light" id="light-dialog" />
                <Label htmlFor="light-dialog" className="cursor-pointer font-normal">
                  {t("light")}
                </Label>
              </div>
              <div className="flex items-center space-x-2">
                <RadioGroupItem value="dark" id="dark-dialog" />
                <Label htmlFor="dark-dialog" className="cursor-pointer font-normal">
                  {t("dark")}
                </Label>
              </div>
              <div className="flex items-center space-x-2">
                <RadioGroupItem value="system" id="system-dialog" />
                <Label htmlFor="system-dialog" className="cursor-pointer font-normal">
                  {t("system")}
                </Label>
              </div>
            </RadioGroup>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
