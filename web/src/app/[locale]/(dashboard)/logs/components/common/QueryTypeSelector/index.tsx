"use client"

import * as React from "react"
import { useLanguage } from "@/components/providers/language-provider"
import {
  PrefixedInput,
  type InputSuggestionOption,
  type PrefixOption,
} from "@/components/common/prefixed-input"
import { useAuth } from "@/components/providers/auth-provider"
import { getManagerApiKeys } from "@/lib/api/apiKeys"
import type { ApikeyInfo } from "@/lib/types/apikeys"
import { QueryTypeSelectorProps, QueryType } from "./types"

export type { QueryType } from "./types"
export { QUERY_TYPE_TO_FIELD_MAP } from "./constants"

const OWNER_TYPE_BADGE_CLASS: Record<string, string> = {
  person: "bg-secondary text-secondary-foreground",
  org: "bg-blue-500/15 text-blue-600",
  project: "bg-purple-500/15 text-purple-600",
}

/**
 * 通用查询类型选择器组件
 *
 * 支持动态配置可用的查询类型：
 * - OpenAPI 日志: AK Code / Request ID / Bella TraceID
 * - Bella 链路: AK Code / Bella TraceID
 */
export function QueryTypeSelector({
  queryType,
  queryValue,
  error,
  onQueryTypeChange,
  onQueryValueChange,
  className,
  availableTypes = [], // 默认全部
  enableAkCodeSuggestions = false,
}: QueryTypeSelectorProps) {
  const { t } = useLanguage()
  const { user } = useAuth()
  const [akOptions, setAkOptions] = React.useState<InputSuggestionOption[]>([])
  const [akOptionsLoading, setAkOptionsLoading] = React.useState(false)
  const [akOptionsError, setAkOptionsError] = React.useState("")
  const [akOptionsOpen, setAkOptionsOpen] = React.useState(false)
  const [akSearchValue, setAkSearchValue] = React.useState("")
  const akRequestSeqRef = React.useRef(0)
  const akOptionsLoadAttemptedRef = React.useRef(false)
  const managerCode = user?.userId?.toString() ?? ""
  const enableSuggestions = enableAkCodeSuggestions && queryType === "AK Code"

  // 根据 availableTypes 动态生成前缀选项
  const prefixOptions: PrefixOption[] = React.useMemo(() => {
    return availableTypes.map(type => ({
      value: type,
      label: type,
    }))
  }, [availableTypes])

  const handlePrefixChange = (selectedType: string) => {
    if (availableTypes.includes(selectedType as QueryType)) {
      onQueryTypeChange(selectedType as QueryType)
    }
  }

  const handleQueryValueChange = React.useCallback(
    (nextValue: string) => {
      if (akOptionsOpen && enableSuggestions) {
        setAkSearchValue(nextValue)
      }
      onQueryValueChange(nextValue)
    },
    [akOptionsOpen, enableSuggestions, onQueryValueChange]
  )

  const handleAkOptionsOpenChange = React.useCallback((open: boolean) => {
    setAkOptionsOpen(open)
    if (open) {
      setAkSearchValue("")
    }
  }, [])

  const handleAkSuggestionSelect = React.useCallback(() => {
    setAkSearchValue("")
  }, [])

  const formatOwnerType = React.useCallback(
    (ownerType?: string): { label: string; className?: string } | undefined => {
      const normalizedOwnerType = ownerType?.trim()

      if (
        normalizedOwnerType === "person" ||
        normalizedOwnerType === "org" ||
        normalizedOwnerType === "project"
      ) {
        return {
          label: t(`logs.akQuickSelect.ownerTypes.${normalizedOwnerType}`),
          className: OWNER_TYPE_BADGE_CLASS[normalizedOwnerType],
        }
      }

      return normalizedOwnerType ? { label: normalizedOwnerType } : undefined
    },
    [t]
  )

  const buildAkOption = React.useCallback(
    (apiKey: ApikeyInfo): InputSuggestionOption => {
      const label =
        apiKey.name?.trim() ||
        apiKey.akDisplay?.trim() ||
        apiKey.serviceId?.trim() ||
        t("logs.akQuickSelect.unnamed")

      const description = [apiKey.serviceId, apiKey.akDisplay]
        .map((item) => item?.trim())
        .filter(
          (item, index, items): item is string =>
            Boolean(item) &&
            item !== label &&
            item !== apiKey.code &&
            items.indexOf(item) === index
        )
        .join(" / ")
      const ownerTypeLabel = formatOwnerType(apiKey.ownerType)

      return {
        value: apiKey.code,
        label,
        description: apiKey.akDisplay?.trim() || description,
        badge: ownerTypeLabel,
        details: [
          {
            label: t("logs.akQuickSelect.name"),
            value: apiKey.name?.trim(),
          },
          {
            label: t("logs.akQuickSelect.serviceId"),
            value: apiKey.serviceId?.trim(),
          },
          {
            label: t("logs.akQuickSelect.remark"),
            value: apiKey.remark?.trim(),
          },
        ],
      }
    },
    [formatOwnerType, t]
  )

  const loadAkOptions = React.useCallback(async () => {
    if (!enableSuggestions || !managerCode) {
      setAkOptions([])
      setAkOptionsLoading(false)
      return
    }

    const requestSeq = akRequestSeqRef.current + 1
    akRequestSeqRef.current = requestSeq
    setAkOptionsLoading(true)
    setAkOptionsError("")

    try {
      const search = akSearchValue.trim() || undefined
      const [delegatedResponse, assignedResponse] = await Promise.all([
        getManagerApiKeys(1, managerCode, search),
        getManagerApiKeys(1, managerCode, search, true),
      ])

      if (akRequestSeqRef.current !== requestSeq) {
        return
      }

      const nextOptions: InputSuggestionOption[] = []
      const seenCodes = new Set<string>()
      const apiKeys = [
        ...(delegatedResponse.data || []),
        ...(assignedResponse.data || []),
      ]

      apiKeys.forEach((apiKey) => {
        if (apiKey.code && !seenCodes.has(apiKey.code)) {
          seenCodes.add(apiKey.code)
          nextOptions.push(buildAkOption(apiKey))
        }
      })

      setAkOptions(nextOptions)
    } catch (error) {
      if (akRequestSeqRef.current !== requestSeq) {
        return
      }

      console.error("Failed to load AK Code options:", error)
      setAkOptionsError(t("logs.akQuickSelect.loadFailed"))
    } finally {
      if (akRequestSeqRef.current === requestSeq) {
        akOptionsLoadAttemptedRef.current = true
        setAkOptionsLoading(false)
      }
    }
  }, [akSearchValue, buildAkOption, enableSuggestions, managerCode, t])

  React.useEffect(() => {
    if (!akOptionsOpen || !enableSuggestions) {
      return
    }

    const delay = akOptionsLoadAttemptedRef.current ? 250 : 0
    const timer = window.setTimeout(() => {
      loadAkOptions()
    }, delay)

    return () => window.clearTimeout(timer)
  }, [akOptionsOpen, enableSuggestions, loadAkOptions])

  return (
    <PrefixedInput
      prefixOptions={prefixOptions}
      defaultPrefix={queryType}
      onPrefixChange={handlePrefixChange}
      value={queryValue}
      onValueChange={handleQueryValueChange}
      placeholder={t("logs.queryPlaceholder")}
      containerClassName={className}
      error={error}
      suggestionsEnabled={enableSuggestions}
      suggestions={akOptions}
      suggestionsFilterValue={akSearchValue}
      selectedSuggestionValue={queryValue.trim()}
      onSuggestionsOpenChange={handleAkOptionsOpenChange}
      onSuggestionSelect={handleAkSuggestionSelect}
      suggestionsLoading={akOptionsLoading}
      suggestionsError={akOptionsError}
      suggestionsHint={t("logs.akQuickSelect.hint")}
      suggestionsLoadingText={t("logs.akQuickSelect.loading")}
      suggestionsEmptyText={t("logs.akQuickSelect.empty")}
      required
    />
  )
}
