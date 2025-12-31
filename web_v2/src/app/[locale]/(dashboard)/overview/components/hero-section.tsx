"use client"

import Link from "next/link"
import { ArrowRight } from "lucide-react"

import { Button } from "@/components/common/button"
import { useLanguage } from "@/components/providers/language-provider"

export function HeroSection() {
  const { t } = useLanguage()

  return (
    <div className="mb-12">
      <div className="mb-3 inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-sm">
        <span className="relative flex h-2 w-2">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary opacity-75"></span>
          <span className="relative inline-flex h-2 w-2 rounded-full bg-primary"></span>
        </span>
        <span className="text-primary font-medium">{t("aiDeveloperPlatform")}</span>
      </div>

      <h1 className="mb-4 text-balance text-5xl font-bold tracking-tight">{t("fastestPathToProduction")}</h1>
      <p className="mb-8 text-pretty text-xl text-muted-foreground">{t("homeDescription")}</p>

      <Button size="lg" asChild>
        <Link href="/models">
          {t("startBuilding")}
          <ArrowRight className="ml-2 h-4 w-4" />
        </Link>
      </Button>
    </div>
  )
}


