"use client"

import Link from "next/link"

import { Button } from "@/components/common/button"
import { Card, CardContent } from "@/components/common/card"
import { useLanguage } from "@/components/providers/language-provider"

export function CodeExample() {
  const { t } = useLanguage()

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-2xl font-bold">{t("quickStart")}</h2>
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" asChild>
            <Link href="/api-keys">{t("viewAPIKeys")}</Link>
          </Button>
          <Button variant="ghost" size="sm" asChild>
            <Link href="/docs">{t("browseDocs")}</Link>
          </Button>
        </div>
      </div>

      <Card className="border-border/50 bg-card/50">
        <CardContent className="p-6">
          <div className="mb-3 flex items-center gap-2">
            <span className="text-sm font-medium text-muted-foreground">Python</span>
          </div>
          <pre className="overflow-x-auto rounded-lg bg-muted/50 p-4 text-sm">
            <code className="font-mono">
              <span className="text-pink-400">from</span> <span className="text-foreground">openai</span>{" "}
              <span className="text-pink-400">import</span> <span className="text-foreground">OpenAI</span>
              {"\n\n"}
              <span className="text-foreground">client</span> <span className="text-pink-400">=</span>{" "}
              <span className="text-foreground">OpenAI</span>
              <span className="text-yellow-400">(</span>
              {"\n"}
              {"  "}
              <span className="text-foreground">api_key</span>
              <span className="text-pink-400">=</span>
              <span className="text-green-400">"your-api-key"</span>
              <span className="text-yellow-400">,</span>
              {"\n"}
              {"  "}
              <span className="text-foreground">base_url</span>
              <span className="text-pink-400">=</span>
              <span className="text-green-400">"https://api.example.com/v1"</span>
              {"\n"}
              <span className="text-yellow-400">)</span>
              {"\n\n"}
              <span className="text-foreground">response</span> <span className="text-pink-400">=</span>{" "}
              <span className="text-foreground">client</span>
              <span className="text-pink-400">.</span>
              <span className="text-foreground">chat</span>
              <span className="text-pink-400">.</span>
              <span className="text-foreground">completions</span>
              <span className="text-pink-400">.</span>
              <span className="text-blue-400">create</span>
              <span className="text-yellow-400">(</span>
              {"\n"}
              {"  "}
              <span className="text-foreground">model</span>
              <span className="text-pink-400">=</span>
              <span className="text-green-400">"gpt-5-mini"</span>
              <span className="text-yellow-400">,</span>
              {"\n"}
              {"  "}
              <span className="text-foreground">messages</span>
              <span className="text-pink-400">=</span>
              <span className="text-yellow-400">[{"{"}</span>
              <span className="text-green-400">"role"</span>
              <span className="text-pink-400">:</span> <span className="text-green-400">"user"</span>
              <span className="text-yellow-400">,</span> <span className="text-green-400">"content"</span>
              <span className="text-pink-400">:</span> <span className="text-green-400">"Hello!"</span>
              <span className="text-yellow-400">{"}"}]</span>
              {"\n"}
              <span className="text-yellow-400">)</span>
              {"\n\n"}
              <span className="text-blue-400">print</span>
              <span className="text-yellow-400">(</span>
              <span className="text-foreground">response</span>
              <span className="text-pink-400">.</span>
              <span className="text-foreground">choices</span>
              <span className="text-yellow-400">[</span>
              <span className="text-purple-400">0</span>
              <span className="text-yellow-400">].</span>
              <span className="text-foreground">message</span>
              <span className="text-pink-400">.</span>
              <span className="text-foreground">content</span>
              <span className="text-yellow-400">)</span>
            </code>
          </pre>
        </CardContent>
      </Card>
    </div>
  )
}


