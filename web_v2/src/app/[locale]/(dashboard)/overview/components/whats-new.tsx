"use client"

import { Sparkles, Code, Zap, TrendingUp, type LucideIcon } from "lucide-react"

import { Card, CardContent } from "@/components/common/card"
import { useLanguage } from "@/components/providers/language-provider"

type WhatsNewItem = {
  icon: LucideIcon
  titleKey: string
  descriptionKey: string
  badgeKey?: string
}

// 静态数据提取到组件外部，避免重复创建
const WHATS_NEW_ITEMS: WhatsNewItem[] = [
  {
    icon: Sparkles,
    titleKey: "gpt5ProTitle",
    descriptionKey: "gpt5ProDesc",
    badgeKey: "newFeature",
  },
  {
    icon: Code,
    titleKey: "sora2Title",
    descriptionKey: "sora2Desc",
    badgeKey: "beta",
  },
  {
    icon: Zap,
    titleKey: "realtimeApiTitle",
    descriptionKey: "realtimeApiDesc",
    badgeKey: "newFeature",
  },
  {
    icon: TrendingUp,
    titleKey: "functionCallingTitle",
    descriptionKey: "functionCallingDesc",
  },
]

export function WhatsNew() {
  const { t } = useLanguage()

  return (
    <section className="mb-16" aria-labelledby="whats-new-heading">
      <h2 id="whats-new-heading" className="mb-6 text-2xl font-bold">
        {t("whatsNew")}
      </h2>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {WHATS_NEW_ITEMS.map((item, index) => {
          const Icon = item.icon
          const title = t(item.titleKey)
          const description = t(item.descriptionKey)
          const badge = item.badgeKey ? t(item.badgeKey) : undefined

          return (
            <Card
              key={index}
              className="border-border/50 transition-colors hover:border-primary/30"
              role="article"
              aria-label={title}
            >
              <CardContent className="p-6">
                <div className="flex items-start gap-4">
                  <div
                    className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10"
                    aria-hidden="true"
                  >
                    <Icon className="h-5 w-5 text-primary" />
                  </div>
                  <div className="flex-1">
                    <div className="mb-1 flex items-center gap-2">
                      <h3 className="font-semibold">{title}</h3>
                      {badge && (
                        <span className="rounded-full bg-primary/20 px-2 py-0.5 text-xs font-medium text-primary">
                          {badge}
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-muted-foreground">{description}</p>
                  </div>
                </div>
              </CardContent>
            </Card>
          )
        })}
      </div>
    </section>
  )
}


