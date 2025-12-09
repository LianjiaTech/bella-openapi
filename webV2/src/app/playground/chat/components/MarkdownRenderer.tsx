"use client"

import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter"
import { vscDarkPlus } from "react-syntax-highlighter/dist/esm/styles/prism"
import { Button } from "@/components/ui/button"
import { Copy, Check } from "lucide-react"
import { useState } from "react"

interface MarkdownRendererProps {
  content: string
}

interface CodeComponentProps {
  node?: any
  inline?: boolean
  className?: string
  children?: React.ReactNode
  [key: string]: any
}

/**
 * Markdown 渲染器组件
 * 支持 GFM 扩展和代码语法高亮
 */
export function MarkdownRenderer({ content }: MarkdownRendererProps) {
  const [copiedCode, setCopiedCode] = useState<string | null>(null)

  const copyToClipboard = (code: string) => {
    navigator.clipboard.writeText(code)
    setCopiedCode(code)
    setTimeout(() => setCopiedCode(null), 2000)
  }

  return (
    <div className="prose prose-sm dark:prose-invert max-w-none">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
        // 自定义代码块渲染
        code({ node, inline, className, children, ...props }: CodeComponentProps) {
          const match = /language-(\w+)/.exec(className || "")
          const codeString = String(children).replace(/\n$/, "")
          const language = match ? match[1] : ""

          if (!inline && match) {
            return (
              <div className="relative my-3 rounded-lg border bg-muted/50 overflow-hidden">
                <div className="flex items-center justify-between border-b bg-muted px-4 py-2">
                  <span className="text-xs font-medium text-muted-foreground">
                    {language}
                  </span>
                  <Button
                    size="sm"
                    variant="ghost"
                    className="h-7 gap-1.5 px-2"
                    onClick={() => copyToClipboard(codeString)}
                  >
                    {copiedCode === codeString ? (
                      <>
                        <Check className="h-3 w-3" />
                        <span className="text-xs">已复制</span>
                      </>
                    ) : (
                      <>
                        <Copy className="h-3 w-3" />
                        <span className="text-xs">复制</span>
                      </>
                    )}
                  </Button>
                </div>
                <SyntaxHighlighter
                  style={vscDarkPlus}
                  language={language}
                  PreTag="div"
                  className="!m-0 !bg-transparent"
                  customStyle={{
                    margin: 0,
                    padding: "1rem",
                    background: "transparent",
                    fontSize: "0.875rem",
                    lineHeight: "1.5",
                  }}
                  {...props}
                >
                  {codeString}
                </SyntaxHighlighter>
              </div>
            )
          }

          return (
            <code
              className="rounded bg-muted px-1.5 py-0.5 text-sm font-mono max-w-full inline-block break-all whitespace-pre-wrap"
            {...props}
            >
              {children}
            </code>
          )
        },
        // 自定义段落渲染
        p({ children }) {
          return <p className="mb-4 last:mb-0 leading-relaxed">{children}</p>
        },
        // 自定义标题渲染
        h1({ children }) {
          return <h1 className="text-2xl font-bold mt-6 mb-4 first:mt-0">{children}</h1>
        },
        h2({ children }) {
          return <h2 className="text-xl font-bold mt-5 mb-3 first:mt-0">{children}</h2>
        },
        h3({ children }) {
          return <h3 className="text-lg font-bold mt-4 mb-2 first:mt-0">{children}</h3>
        },
        // 自定义列表渲染
        ul({ children }) {
          return <ul className="list-disc list-inside mb-4 space-y-1">{children}</ul>
        },
        ol({ children }) {
          return <ol className="list-decimal list-inside mb-4 space-y-1">{children}</ol>
        },
        // 自定义链接渲染
        a({ href, children }) {
          return (
            <a
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              className="text-primary underline hover:text-primary/80 transition-colors"
            >
              {children}
            </a>
          )
        },
        // 自定义表格渲染
        table({ children }) {
          return (
            <div className="my-4 overflow-x-auto">
              <table className="min-w-full border-collapse border border-border">
                {children}
              </table>
            </div>
          )
        },
        thead({ children }) {
          return <thead className="bg-muted">{children}</thead>
        },
        th({ children }) {
          return (
            <th className="border border-border px-4 py-2 text-left font-semibold">
              {children}
            </th>
          )
        },
        td({ children }) {
          return <td className="border border-border px-4 py-2">{children}</td>
        },
        // 自定义引用块渲染
        blockquote({ children }) {
          return (
            <blockquote className="border-l-4 border-primary pl-4 italic my-4 text-muted-foreground">
              {children}
            </blockquote>
          )
        },
        // 自定义分隔线渲染
        hr() {
          return <hr className="my-6 border-border" />
        },
      }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}

