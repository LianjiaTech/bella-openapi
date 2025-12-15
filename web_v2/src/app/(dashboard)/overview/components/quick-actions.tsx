"use client"

import Link from "next/link"
import { Card, CardDescription, CardHeader, CardTitle } from "@/common/ui/card"
import { Code, MessageSquare, Gauge } from "lucide-react"
import { useLanguage } from "@/components/providers/language-provider"

export const QuickActions = () => {
  const { t } = useLanguage()

  const quickActions = [
    {
      icon: Code,
      title: t("browseModels"),
      description: t("browseModelsDesc"),
      href: "/models",
    },
    {
      icon: MessageSquare,
      title: t("testInPlayground"),
      description: t("testInPlaygroundDesc"),
      href: "/playground",
    },
    {
      icon: Gauge,
      title: t("monitorUsage"),
      description: t("monitorUsageDesc"),
      href: "/logs",
    },
  ]

  return (
    <div className="mb-16 grid grid-cols-1 gap-4 md:grid-cols-3">
      {quickActions.map((action, index) => {
        const Icon = action.icon
        return (
          <Link key={index} href={action.href}>
            <Card className="h-full border-border/50 transition-all hover:border-primary/50 hover:shadow-md">
              <CardHeader>
                <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
                  <Icon className="h-5 w-5 text-primary" />
                </div>
                <CardTitle className="text-lg">{action.title}</CardTitle>
                <CardDescription>{action.description}</CardDescription>
              </CardHeader>
            </Card>
          </Link>
        )
      })}
    </div>
  )
}
