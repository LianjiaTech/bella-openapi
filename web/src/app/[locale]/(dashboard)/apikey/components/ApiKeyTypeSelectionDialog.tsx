"use client";

import { Button } from "@/components/common/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/common/dialog";
import type { ApiKeyCreateOwnerType } from "@/lib/integrations/apiKeyCreateApply";
import { Building2, ExternalLink, FolderKanban, Info, UserRound } from "lucide-react";

type ApiKeyTypeSelectionDialogProps = {
  isOpen: boolean;
  onClose: () => void;
  onSelectPersonal?: () => void;
  onSelectApplyType: (ownerType: ApiKeyCreateOwnerType) => void;
  personalLoading?: boolean;
  showPersonalOption?: boolean;
  description?: string;
};

const apiKeyTypeOptions = [
  {
    type: "org" as const,
    title: "组织 APIKey",
    description: "组织通用密钥，用于组织长期调用，由组织管理者统一管控整体资源配额，支持 BPM 流程提额。",
    action: "进入 BPM 申请",
    icon: Building2,
  },
  {
    type: "project" as const,
    title: "项目 APIKey",
    description: "项目专属密钥，绑定独立项目，生命周期与项目同步启停，支持 BPM 流程提额。",
    action: "进入 BPM 申请",
    icon: FolderKanban,
  },
  {
    type: "person" as const,
    title: "个人 APIKey",
    description: "个人调试密钥，仅本地测试使用，固定 50元 基础配额，不支持提额。",
    action: "创建个人 APIKey",
    icon: UserRound,
  },
];

const cityOrgApiKeyQuotaDocUrl =
  "https://doc.weixin.qq.com/doc/w3_AH4AOQbyANMCNUKaCWhoeS1iwRqRw?scode=AJMA1Qc4AAww35tgpPAH4AOQbyANM";

export function ApiKeyTypeSelectionDialog({
  isOpen,
  onClose,
  onSelectPersonal,
  onSelectApplyType,
  personalLoading = false,
  showPersonalOption = true,
  description = "根据调用场景选择组织、项目或个人 APIKey，进入对应创建流程。",
}: ApiKeyTypeSelectionDialogProps) {
  const visibleOptions = showPersonalOption
    ? apiKeyTypeOptions
    : apiKeyTypeOptions.filter((option) => option.type !== "person");

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>选择 APIKey 类型</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>

        <div className="flex gap-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm leading-6 text-amber-950">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-amber-700" />
          <p>
            城市申请组织apikey额度，需完成预算调拨流程，具体参考
            <a
              href={cityOrgApiKeyQuotaDocUrl}
              target="_blank"
              rel="noreferrer"
              className="font-medium underline underline-offset-2"
            >
              文档
            </a>
            。
          </p>
        </div>

        <div className="grid gap-3 md:grid-cols-3">
          {visibleOptions.map((option) => {
            const Icon = option.icon;
            const isPersonal = option.type === "person";
            return (
              <button
                key={option.type}
                type="button"
                className="flex h-full min-h-56 flex-col rounded-lg border bg-background p-4 text-left transition-colors hover:border-primary hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-60"
                onClick={() => {
                  if (isPersonal) {
                    onSelectPersonal?.();
                  } else {
                    onSelectApplyType(option.type);
                  }
                }}
                disabled={isPersonal && personalLoading}
              >
                <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-md bg-muted text-muted-foreground">
                  <Icon className="h-5 w-5" />
                </div>
                <div className="text-base font-semibold text-foreground">{option.title}</div>
                <p className="mt-2 flex-1 text-sm leading-6 text-muted-foreground">{option.description}</p>
                <div className="mt-4">
                  <span className="inline-flex items-center gap-2 text-sm font-medium text-primary">
                    {isPersonal && personalLoading ? "创建中..." : option.action}
                    {!isPersonal && <ExternalLink className="h-4 w-4" />}
                  </span>
                </div>
              </button>
            );
          })}
        </div>

        <div className="flex justify-end">
          <Button type="button" variant="outline" onClick={onClose} disabled={personalLoading}>
            取消
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
