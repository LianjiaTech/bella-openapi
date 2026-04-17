"use client";

import { TopBar } from "@/components/layout";
import { IframePlayground } from "@/components/common/iframe-playground";
import config from "@/config";

export default function DocParsePage() {
  return (
    <div className="flex flex-col h-full">
      <TopBar
        title="文档解析 Playground"
        description="文档解析和提取"
      />
      <div className="flex-1 overflow-y-auto p-8">
        <IframePlayground
          title="Document Parse"
          url={config.api.documentParsePlayground}
          height="calc(100vh - 200px)"
          unavailableMessage="文档解析功能"
        />
      </div>
    </div>
  );
}
