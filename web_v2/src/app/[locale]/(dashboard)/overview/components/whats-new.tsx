"use client"

import { Sparkles, Code, Zap, TrendingUp, type LucideIcon } from "lucide-react"

import { Card, CardContent } from "@/components/common/card"
import { useLanguage } from "@/components/providers/language-provider"

type WhatsNewItem = {
  icon: LucideIcon
  title: string
  description: string
  badge?: string
}

export function WhatsNew() {
  const { t } = useLanguage()

  const whatsNew: WhatsNewItem[] = [
    {
      icon: Sparkles,
      title: t("gpt5ProTitle"),
      description: t("gpt5ProDesc"),
      badge: t("newFeature"),
    },
    {
      icon: Code,
      title: t("sora2Title"),
      description: t("sora2Desc"),
      badge: t("beta"),
    },
    {
      icon: Zap,
      title: t("realtimeApiTitle"),
      description: t("realtimeApiDesc"),
      badge: t("newFeature"),
    },
    {
      icon: TrendingUp,
      title: t("functionCallingTitle"),
      description: t("functionCallingDesc"),
    },
  ]

  return (
    <div className="mb-16">
      <h2 className="mb-6 text-2xl font-bold">{t("whatsNew")}</h2>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {whatsNew.map((item, index) => {
          const Icon = item.icon
          return (
            <Card key={index} className="border-border/50 transition-colors hover:border-primary/30">
              <CardContent className="p-6">
                <div className="flex items-start gap-4">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                    <Icon className="h-5 w-5 text-primary" />
                  </div>
                  <div className="flex-1">
                    <div className="mb-1 flex items-center gap-2">
                      <h3 className="font-semibold">{item.title}</h3>
                      {item.badge && (
                        <span className="rounded-full bg-primary/20 px-2 py-0.5 text-xs font-medium text-primary">
                          {item.badge}
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-muted-foreground">{item.description}</p>
                  </div>
                </div>
              </CardContent>
            </Card>
          )
        })}
      </div>
    </div>
  )
}


