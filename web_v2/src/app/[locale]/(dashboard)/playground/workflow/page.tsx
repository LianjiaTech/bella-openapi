"use client";

import { TopBar } from "@/components/layout";
import { IframePlayground } from "@/components/common/iframe-playground";
import config from "@/config";

export default function WorkflowPage() {
  return (
    <div className="flex flex-col h-full">
      {/* 顶部导航栏 */}
      <TopBar
        title="AI工作流 Playground"
        description="AI 工作流编排"
      />

      {/* 主内容区域 */}
      <div className="flex-1 overflow-y-auto p-8">
        <IframePlayground
          title="Workflow Apps"
          url={config.api.workflowPlayground}
          height="calc(100vh - 200px)"
          unavailableMessage="工作流功能"
        />
      </div>
    </div>
  );
}
