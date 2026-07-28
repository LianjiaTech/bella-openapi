"use client";

import { Button } from "@/components/common/button";
import {
    buildApiKeyCreateApplyUrl,
    type ApiKeyCreateOwnerType,
} from "@/lib/integrations/apiKeyCreateApply";
import { applyApiKey } from "@/lib/api/apiKeys";
import { ApiKeyTypeSelectionDialog } from "@/app/[locale]/(dashboard)/apikey/components/ApiKeyTypeSelectionDialog";
import { useAuth } from "@/components/providers/auth-provider";
import { Plus } from "lucide-react";
import { useCallback, useState } from "react";
import { toast } from "sonner";

function openExternalUrl(url: string) {
    window.open(url, "_blank", "noopener,noreferrer");
}

interface CreateApiKeyApplyButtonProps {
    onPersonalCreated: (apiKey: string) => void;
}

export function CreateApiKeyApplyButton({ onPersonalCreated }: CreateApiKeyApplyButtonProps) {
    const { user } = useAuth();
    const [showTypeSelectionDialog, setShowTypeSelectionDialog] = useState(false);
    const [creatingPersonalApiKey, setCreatingPersonalApiKey] = useState(false);

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

    const handleCreatePersonal = useCallback(async () => {
        if (!user?.userId) {
            toast.error("无法获取当前用户信息，请刷新后重试");
            return;
        }

        try {
            setCreatingPersonalApiKey(true);
            const apiKey = await applyApiKey({
                ownerCode: user.userId.toString(),
                ownerName: user.userName || "",
            });
            setShowTypeSelectionDialog(false);
            onPersonalCreated(apiKey);
        } catch (error) {
            console.error("Failed to create personal API key:", error);
            toast.error("个人 APIKey 创建失败，请稍后重试");
        } finally {
            setCreatingPersonalApiKey(false);
        }
    }, [onPersonalCreated, user?.userId, user?.userName]);

    return (
        <>
            <Button
                type="button"
                size="sm"
                className="gap-2"
                onClick={() => setShowTypeSelectionDialog(true)}
            >
                <Plus className="h-4 w-4" />
                创建 AK
            </Button>
            <ApiKeyTypeSelectionDialog
                isOpen={showTypeSelectionDialog}
                onClose={() => setShowTypeSelectionDialog(false)}
                onSelectPersonal={handleCreatePersonal}
                onSelectApplyType={handleSelect}
                personalLoading={creatingPersonalApiKey}
            />
        </>
    );
}
