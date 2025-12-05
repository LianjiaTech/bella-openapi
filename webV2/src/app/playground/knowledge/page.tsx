import { TopBar } from "@/components/top-bar"
import { Database } from "lucide-react"
import { Card } from "@/components/ui/card"

export default function KnowledgePlaygroundPage() {
  return (
    <div className="flex h-screen flex-col">
      <TopBar title="Knowledge Playground" description="知识库管理和查询" />
      <main className="flex-1 overflow-auto p-6">
        <div className="mx-auto max-w-5xl">
          <Card className="p-8">
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <Database className="mb-4 h-16 w-16 text-muted-foreground" />
              <h2 className="mb-2 text-xl font-semibold">知识库功能</h2>
              <p className="text-muted-foreground">即将推出</p>
            </div>
          </Card>
        </div>
      </main>
    </div>
  )
}
