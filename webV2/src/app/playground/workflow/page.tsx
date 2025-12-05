import { TopBar } from "@/components/top-bar"
import { Workflow } from "lucide-react"
import { Card } from "@/components/ui/card"

export default function WorkflowPlaygroundPage() {
  return (
    <div className="flex h-screen flex-col">
      <TopBar title="Workflow Playground" description="AI 工作流编排" />
      <main className="flex-1 overflow-auto p-6">
        <div className="mx-auto max-w-5xl">
          <Card className="p-8">
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <Workflow className="mb-4 h-16 w-16 text-muted-foreground" />
              <h2 className="mb-2 text-xl font-semibold">工作流功能</h2>
              <p className="text-muted-foreground">即将推出</p>
            </div>
          </Card>
        </div>
      </main>
    </div>
  )
}
