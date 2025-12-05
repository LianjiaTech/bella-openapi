"use client"

import { FlaskConical, ChevronDown, LucideIcon } from "lucide-react"
import Link from "next/link"
import { cn } from "@/lib/utils"
import { useMemo, useState } from "react"
import { useSidebar } from "@/lib/context/sidebar-context"
import { flattenCategoryTrees, EndpointWithCategory } from "@/app/models/utils/category-tree"
import { getPlaygroundUrl, getIconForEndpoint } from "./endpoint-mapping"

// 为 endpoint 添加图标
interface PlaygroundItemWithIcon extends EndpointWithCategory {
  icon: LucideIcon
  href: string
}

interface PlaygroundSidebarMenuProps {
  pathname: string
  t: (key: string) => string
}

export function PlaygroundSidebarMenu({ pathname, t }: PlaygroundSidebarMenuProps) {
  const [isPlaygroundOpen, setIsPlaygroundOpen] = useState(pathname.startsWith("/playground"))
  const isPlaygroundActive = pathname.startsWith("/playground")
  const { categoryTrees } = useSidebar()

  const playgroundItems = useMemo(() => {
    const flatItems = flattenCategoryTrees(categoryTrees)
    return flatItems.map((item): PlaygroundItemWithIcon => {
      // 使用 endpoint-mapping 获取图标和路由
      const icon = getIconForEndpoint(item.endpoint || item.endpointCode)
      const href = getPlaygroundUrl(item.endpoint)
      
      return {
        ...item,
        icon,
        href,
      }
    })
  }, [categoryTrees])

  return (
    <div>
      <button
        onClick={() => setIsPlaygroundOpen(!isPlaygroundOpen)}
        className={cn(
          "flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
          isPlaygroundActive
            ? "bg-sidebar-accent text-sidebar-accent-foreground bg-gray-300"
            : "text-gray-500 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
        )}
      >
        <FlaskConical className="h-5 w-5" />
        <span className="flex-1 text-left">{t("playground")}</span>
        <ChevronDown className={cn("h-4 w-4 transition-transform", isPlaygroundOpen ? "rotate-180" : "")} />
      </button>

      {/* Playground的子菜单 */}
      {isPlaygroundOpen && (
        <div className="ml-4 mt-1 space-y-1 border-l border-sidebar-border pl-4">
          {playgroundItems.map((item) => {
            const baseHref = item.href.split("?")[0]
            const isActive = pathname === baseHref
            const Icon = item.icon
            
            return (
              <Link
                key={item.endpointName}
                href={item.href}
                className={cn(
                  "flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm transition-colors",
                  isActive
                    ? "bg-sidebar-accent text-sidebar-accent-foreground font-medium bg-gray-300"
                    : "text-gray-500 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
                )}
              >
                <Icon className="h-4 w-4" />
                {t(item.endpointName) || item.endpointName}
              </Link>
            )
          })}
        </div>
      )}
    </div>
  )
}
