"use client";

import { IframePlayground } from "@/components/playground/iframe-playground";
import { workflow_playground } from "@/config";

export default function WorkflowPlaygroundPage() {
    return (
        <div className="container mx-auto p-6">
            <IframePlayground
                title="Workflow Apps"
                url={workflow_playground}
                height="800px"
                unavailableMessage="Workflow 功能暂未开放"
            />
        </div>
    );
}
