"use client"

import {
  Home,
  Sparkles,
  FlaskConical,
  Key,
  ScrollText,
  HelpCircle,
  ChevronDown,
  MessageSquare,
  Brain,
  Mic,
  Volume2,
  Radio,
  ImageIcon,
  Database,
  Network,
  FileText,
  WorkflowIcon,
  Search,
  ScanText,
  Settings,
  Activity,
  FolderTree,
  LogIn,
} from "lucide-react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { cn } from "@/lib/utils"
import { useState } from "react"
import { SettingsDialog } from "@/components/settings-dialog"
import { useLanguage } from "@/components/language-provider"

export function AppSidebar() {
  const pathname = usePathname()
  const [isPlaygroundOpen, setIsPlaygroundOpen] = useState(pathname.startsWith("/playground"))
  const { t } = useLanguage()

  const isPlaygroundActive = pathname.startsWith("/playground")

  return (
    <aside className="fixed left-0 top-0 z-40 h-screen w-64 flex-shrink-0 border-r border-border bg-sidebar">
      <div className="flex h-full flex-col">
        {/* Logo */}
        <div className="flex h-16 items-center border-b border-sidebar-border px-6">
          <Link href="/" className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary">
              <Sparkles className="h-5 w-5 text-primary-foreground" />
            </div>
            <span className="text-lg font-semibold text-sidebar-foreground">Bella</span>
          </Link>
        </div>

        {/* Main Navigation */}
        <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
          <div className="space-y-1">
            {[
              { name: t("home"), href: "/", icon: Home },
              { name: t("models"), href: "/models", icon: Sparkles },
            ].map((item) => {
              const isActive = pathname === item.href
              return (
                <Link
                  key={item.name}
                  href={item.href}
                  className={cn(
                    "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                    isActive
                      ? "bg-sidebar-accent text-sidebar-accent-foreground"
                      : "text-sidebar-foreground/70 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
                  )}
                >
                  <item.icon className="h-5 w-5" />
                  {item.name}
                </Link>
              )
            })}

            {/* Playground Menu Item */}
            <div>
              <button
                onClick={() => setIsPlaygroundOpen(!isPlaygroundOpen)}
                className={cn(
                  "flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                  isPlaygroundActive
                    ? "bg-sidebar-accent text-sidebar-accent-foreground"
                    : "text-sidebar-foreground/70 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
                )}
              >
                <FlaskConical className="h-5 w-5" />
                <span className="flex-1 text-left">{t("playground")}</span>
                <ChevronDown className={cn("h-4 w-4 transition-transform", isPlaygroundOpen ? "rotate-180" : "")} />
              </button>

              {/* Submenu for Playground */}
              {isPlaygroundOpen && (
                <div className="ml-4 mt-1 space-y-1 border-l border-sidebar-border pl-4">
                  {[
                    { name: t("chat"), href: "/playground/chat", icon: MessageSquare },
                    { name: t("embedding"), href: "/playground/embedding", icon: Brain },
                    { name: t("audio"), href: "/playground/audio", icon: Mic },
                    { name: t("tts"), href: "/playground/tts", icon: Volume2 },
                    { name: t("realtime"), href: "/playground/realtime", icon: Radio },
                    { name: t("images"), href: "/playground/images", icon: ImageIcon },
                    { name: t("knowledge"), href: "/playground/knowledge", icon: Database },
                    { name: t("rag"), href: "/playground/rag", icon: Network },
                    { name: t("docparse"), href: "/playground/docparse", icon: FileText },
                    { name: t("workflow"), href: "/playground/workflow", icon: WorkflowIcon },
                    { name: t("search"), href: "/playground/search", icon: Search },
                    { name: t("ocr"), href: "/playground/ocr", icon: ScanText },
                  ].map((item) => {
                    const isActive = pathname === item.href
                    return (
                      <Link
                        key={item.name}
                        href={item.href}
                        className={cn(
                          "flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm transition-colors",
                          isActive
                            ? "bg-sidebar-accent text-sidebar-accent-foreground font-medium"
                            : "text-sidebar-foreground/60 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
                        )}
                      >
                        <item.icon className="h-4 w-4" />
                        {item.name}
                      </Link>
                    )
                  })}
                </div>
              )}
            </div>

            {[
              { name: t("apiKeys"), href: "/api-keys", icon: Key },
              { name: t("logs"), href: "/logs", icon: ScrollText },
              { name: t("modelStatus"), href: "/model-status", icon: Activity },
              { name: t("metadata"), href: "/metadata", icon: FolderTree }, // Added metadata navigation item
            ].map((item) => {
              const isActive = pathname === item.href || pathname.startsWith(item.href + "/")
              return (
                <Link
                  key={item.name}
                  href={item.href}
                  className={cn(
                    "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                    isActive
                      ? "bg-sidebar-accent text-sidebar-accent-foreground"
                      : "text-sidebar-foreground/70 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
                  )}
                >
                  <item.icon className="h-5 w-5" />
                  {item.name}
                </Link>
              )
            })}
          </div>
        </nav>

        {/* Bottom Navigation */}
        <div className="border-t border-sidebar-border p-3 space-y-1">
          {[
            { name: t("settings"), component: "settings" },
            { name: t("login"), href: "/login", icon: LogIn }, // 添加登录导航项
            { name: t("help"), href: "/help", icon: HelpCircle },
          ].map((item) => {
            if (item.component === "settings") {
              return (
                <SettingsDialog key={item.name}>
                  <button
                    className={cn(
                      "flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                      "text-sidebar-foreground/70 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
                    )}
                  >
                    <Settings className="h-5 w-5" />
                    {item.name}
                  </button>
                </SettingsDialog>
              )
            }

            const isActive = pathname === item.href
            return (
              <Link
                key={item.name}
                href={item.href!}
                className={cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-sidebar-accent text-sidebar-accent-foreground"
                    : "text-sidebar-foreground/70 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
                )}
              >
                <item.icon className="h-5 w-5" />
                {item.name}
              </Link>
            )
          })}
        </div>
      </div>
    </aside>
  )
}
