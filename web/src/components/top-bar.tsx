"use client"

import { Bell, Github, Star, Sun, Moon } from "lucide-react"
import { Button } from "@/components/ui/button"
import { useTheme } from "@/components/theme-provider"
import { useLanguage } from "@/components/language-provider"
import type React from "react"

interface TopBarProps {
  title?: string
  description?: string
  action?: React.ReactNode
}

export function TopBar({ title, description, action }: TopBarProps) {
  const { theme, setTheme } = useTheme()
  const { t } = useLanguage()

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-4 border-b border-border bg-background/95 px-6 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      {title && (
        <div className="flex flex-1 flex-col justify-center">
          <h1 className="text-lg font-semibold">{title}</h1>
          {description && <p className="text-xs text-muted-foreground">{description}</p>}
        </div>
      )}
      {!title && <div className="flex flex-1" />}

      {action && <div className="flex items-center gap-2">{action}</div>}

      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          className="gap-2 bg-transparent"
          onClick={() => window.open("https://github.com/yourusername/bella", "_blank")}
        >
          <Github className="h-4 w-4" />
          <Star className="h-4 w-4" />
          {t("star")}
        </Button>
        <Button
          variant="ghost"
          size="icon"
          onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
          className="relative"
          title={t("toggleTheme")}
        >
          <Sun className="h-5 w-5 rotate-0 scale-100 transition-transform dark:-rotate-90 dark:scale-0" />
          <Moon className="absolute h-5 w-5 rotate-90 scale-0 transition-transform dark:rotate-0 dark:scale-100" />
          <span className="sr-only">{t("toggleTheme")}</span>
        </Button>
      </div>
    </header>
  )
}
