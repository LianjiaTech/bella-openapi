'use client'

import * as React from "react"
import { TopBar } from "@/components/layout/top-bar"
import { useLanguage } from "@/components/providers/language-provider"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/common/tabs"
import { OpenapiLogsContent } from "./components/openapiLogsContent"
import { BellaLogsServiceContent } from "./components/bellaLogsServiceContent/index"

export default function LogsPage() {
  const { t } = useLanguage()

  return (
    <div>
      <TopBar title={t("logs.logsTitle")} description={t("logs.logsDesc")} />
      <div className="m-4 space-y-4">
        <Tabs defaultValue="openapi" className="w-full">
          <TabsList>
            <TabsTrigger className="cursor-pointer" value="openapi">{t("logs.openapiTab")}</TabsTrigger>
            <TabsTrigger className="cursor-pointer" value="bella">{t("logs.bellaTab")}</TabsTrigger>
          </TabsList>

          <TabsContent value="openapi" className="space-y-4">
            <OpenapiLogsContent />
          </TabsContent>

          <TabsContent value="bella" className="space-y-4">
            {/* Bella链路查询内容 */}
            <BellaLogsServiceContent />
          </TabsContent>
        </Tabs>
      </div>
    </div>
  )
}
