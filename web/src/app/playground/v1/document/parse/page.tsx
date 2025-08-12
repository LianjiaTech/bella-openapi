"use client";

import { IframePlayground } from "@/components/playground/iframe-playground";
import { document_parse_playground } from "@/config";

export default function DocumentParsePlaygroundPage() {
    return (
        <div className="container mx-auto p-6">
            <IframePlayground
                title="Document Parse"
                url={document_parse_playground}
                height="800px"
                unavailableMessage="Document Parse 功能暂未开放"
            />
        </div>
    );
}