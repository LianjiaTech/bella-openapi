"use client";

import { Button } from "@/components/common/button";
import {
    buildApiKeyCreateApplyUrl,
    type ApiKeyCreateOwnerType,
} from "@/lib/integrations/apiKeyCreateApply";
import { ApiKeyTypeSelectionDialog } from "@/app/[locale]/(dashboard)/apikey/components/ApiKeyTypeSelectionDialog";
import { Plus } from "lucide-react";
import { useCallback, useState } from "react";
import { toast } from "sonner";

function openExternalUrl(url: string) {
    window.open(url, "_blank", "noopener,noreferrer");
}

export function CreateApiKeyApplyButton() {
    const [showTypeSelectionDialog, setShowTypeSelectionDialog] = useState(false);

    const handleSelect = useCallback((ownerType: ApiKeyCreateOwnerType) => {
        const url = buildApiKeyCreateApplyUrl(ownerType);
        if (!url) {
            const typeLabel = ownerType === "org" ? "组织 APIKey" : "项目 APIKey";
            toast.error(`${typeLabel} 创建申请入口未配置或地址无效`);
            return;
        }

        openExternalUrl(url);
        setShowTypeSelectionDialog(false);
    }, []);

    return (
        <>
            <Button
                type="button"
                size="sm"
                className="gap-2"
                onClick={() => setShowTypeSelectionDialog(true)}
            >
                <Plus className="h-4 w-4" />
                申请创建 AK
            </Button>
            <ApiKeyTypeSelectionDialog
                isOpen={showTypeSelectionDialog}
                onClose={() => setShowTypeSelectionDialog(false)}
                onSelectApplyType={handleSelect}
                showPersonalOption={false}
                description="根据调用场景选择组织或项目 APIKey，进入对应 BPM 创建申请流程。"
            />
        </>
    );
}
