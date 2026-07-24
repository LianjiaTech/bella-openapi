import { Loader2 } from "lucide-react"

function LoadingRow({ index }: { index: number }) {
  return (
    <tr className="border-b">
      <td className="px-4 py-4">
        <div className="flex items-center gap-3">
          <div className="h-3 w-3 rounded-full bg-muted animate-pulse" />
          <div>
            <div className="h-4 w-44 rounded bg-muted animate-pulse" />
            <div className="mt-2 h-3 w-32 rounded bg-muted/70 animate-pulse" />
          </div>
        </div>
      </td>
      <td className="px-2 py-4"><div className="ml-auto h-4 w-14 rounded bg-muted animate-pulse" /></td>
      <td className="px-2 py-4"><div className="ml-auto h-4 w-10 rounded bg-muted animate-pulse" /></td>
      <td className="px-2 py-4"><div className="ml-auto h-4 w-10 rounded bg-muted animate-pulse" /></td>
      <td className="px-2 py-4"><div className="ml-auto h-4 w-10 rounded bg-muted animate-pulse" /></td>
      <td className="px-2 py-4"><div className="ml-auto h-4 w-10 rounded bg-muted animate-pulse" /></td>
      <td className="px-4 py-4">
        <div
          className="grid h-5 w-full min-w-[520px] gap-[3px]"
          style={{ gridTemplateColumns: "repeat(48, minmax(3px, 1fr))" }}
        >
          {Array.from({ length: 48 }).map((_, itemIndex) => (
            <div key={`${index}-${itemIndex}`} className="h-[18px] rounded-[2px] bg-muted animate-pulse" />
          ))}
        </div>
      </td>
    </tr>
  )
}

export default function ChannelHealthLoading() {
  return (
    <div className="h-full overflow-y-auto bg-background">
      <div className="flex h-16 items-center justify-between border-b px-6">
        <div className="flex items-center gap-4">
          <div className="h-9 w-9 rounded-md border bg-muted animate-pulse" />
          <div>
            <div className="h-5 w-36 rounded bg-muted animate-pulse" />
            <div className="mt-2 h-3 w-48 rounded bg-muted/70 animate-pulse" />
          </div>
        </div>
        <div className="h-9 w-72 rounded-md bg-muted animate-pulse" />
      </div>

      <main className="m-4">
          <div className="overflow-hidden rounded-lg border bg-card">
            <div className="flex items-center justify-between border-b p-4">
              <div className="h-5 w-32 rounded bg-muted animate-pulse" />
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                <span>数据加载中</span>
              </div>
            </div>

          <div className="overflow-x-auto border-y">
            <table className="w-full min-w-[1080px] table-fixed text-sm">
              <colgroup>
                <col className="w-[320px]" />
                <col className="w-[92px]" />
                <col className="w-[82px]" />
                <col className="w-[82px]" />
                <col className="w-[96px]" />
                <col className="w-[82px]" />
                <col />
              </colgroup>
              <thead className="bg-muted/40 text-xs text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 text-left font-medium">聚合对象</th>
                  <th className="px-2 py-3 text-right font-medium">异常率</th>
                  <th className="px-2 py-3 text-right font-medium">429</th>
                  <th className="px-2 py-3 text-right font-medium">5xx</th>
                  <th className="px-2 py-3 text-right font-medium">4xx</th>
                  <th className="px-2 py-3 text-right font-medium">其他</th>
                  <th className="px-4 py-3 text-left font-medium">采样</th>
                </tr>
              </thead>
              <tbody>
                {Array.from({ length: 10 }).map((_, index) => (
                  <LoadingRow key={index} index={index} />
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </main>
    </div>
  )
}
