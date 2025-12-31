"use client"

import Link from "next/link"
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter"
import { vscDarkPlus } from "react-syntax-highlighter/dist/esm/styles/prism"

import { Button } from "@/components/common/button"
import { Card, CardContent } from "@/components/common/card"
import { useLanguage } from "@/components/providers/language-provider"
import { config } from "@/config"

export function CodeExample() {
  const { t } = useLanguage()

  const codeString = `from openai import OpenAI

client = OpenAI(
  api_key="your-api-key",
  base_url="${config.api.defaultEndpoint}"
)

response = client.chat.completions.create(
  model="gpt-5-mini",
  messages=[{"role": "user", "content": "Hello!"}]
)

print(response.choices[0].message.content)`

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-2xl font-bold">{t("quickStart")}</h2>
        <nav className="flex gap-2" aria-label={t("quickStartActions")}>
          <Button variant="ghost" size="sm" asChild>
            <Link href="/api-keys">{t("viewAPIKeys")}</Link>
          </Button>
          <Button variant="ghost" size="sm" asChild>
            <Link href="/docs">{t("browseDocs")}</Link>
          </Button>
        </nav>
      </div>

      <Card className="border-border/50 bg-card/50">
        <CardContent className="p-6">
          <div className="mb-3 flex items-center gap-2">
            <span className="text-sm font-medium text-muted-foreground">Python</span>
          </div>
          <div className="overflow-x-auto rounded-lg">
            <SyntaxHighlighter
              language="python"
              style={vscDarkPlus}
              customStyle={{
                margin: 0,
                padding: "1rem",
                fontSize: "0.875rem",
                background: "hsl(var(--muted) / 0.5)",
              }}
            >
              {codeString}
            </SyntaxHighlighter>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}


