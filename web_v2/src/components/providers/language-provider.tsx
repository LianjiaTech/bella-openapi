"use client"

import type React from "react"
import { createContext, useContext, useEffect, useState } from "react"
import { useTranslations, useLocale } from 'next-intl'
import { useRouter, usePathname } from '@/i18n/routing'

type Language = "zh-CN" | "en-US"

interface LanguageContextType {
  language: Language
  setLanguage: (language: Language) => void
  t: (key: string) => string
}

const LanguageContext = createContext<LanguageContextType | undefined>(undefined)

export function LanguageProvider({ children }: { children: React.ReactNode }) {
  const locale = useLocale() as Language;
  const router = useRouter();
  const pathname = usePathname();

  // Get all translations from next-intl
  const tNav = useTranslations('navigation');
  const tSettings = useTranslations('settings');
  const tHome = useTranslations('home');
  const tModels = useTranslations('models');
  const tMetadata = useTranslations('metadata');
  const tCommon = useTranslations('common');

  const [language, setLanguageState] = useState<Language>(locale)

  // Sync with localStorage on mount
  useEffect(() => {
    const savedLanguage = localStorage.getItem("language") as Language
    if (savedLanguage && (savedLanguage === "zh-CN" || savedLanguage === "en-US")) {
      if (savedLanguage !== locale) {
        setLanguage(savedLanguage);
      }
    } else {
      // Save current locale to localStorage
      localStorage.setItem("language", locale);
    }
  }, [locale])

  const setLanguage = async (newLanguage: Language) => {
    setLanguageState(newLanguage);

    // Update localStorage
    if (typeof window !== "undefined") {
      localStorage.setItem("language", newLanguage);
      document.documentElement.lang = newLanguage;

      // Update cookie for next-intl
      document.cookie = `NEXT_LOCALE=${newLanguage}; path=/; max-age=31536000; SameSite=Lax`;

      // Navigate to the same page with new locale
      router.replace(pathname, { locale: newLanguage });
    }
  }

  // Compatibility translation function - supports flat keys like before
  const t = (key: string): string => {
    // Map old flat keys to new nested structure
    const keyMap: Record<string, string> = {
      // Navigation
      "home": "navigation.home",
      "models": "navigation.models",
      "playground": "navigation.playground",
      "apiKeys": "navigation.apiKeys",
      "logs": "navigation.logs",
      "modelStatus": "navigation.modelStatus",
      "metadata": "navigation.metadata",
      "docs": "navigation.docs",
      "settings": "navigation.settings",
      "login": "navigation.login",
      "logout": "navigation.logout",
      "help": "navigation.help",

      // Playground items
      "chat": "navigation.chat",
      "embedding": "navigation.embedding",
      "audio": "navigation.audio",
      "tts": "navigation.tts",
      "realtime": "navigation.realtime",
      "images": "navigation.images",
      "knowledge": "navigation.knowledge",
      "rag": "navigation.rag",
      "docparse": "navigation.docparse",
      "workflow": "navigation.workflow",
      "search": "navigation.search",
      "ocr": "navigation.ocr",

      // Settings
      "settingsTitle": "settings.settingsTitle",
      "settingsDescription": "settings.settingsDescription",
      "language": "settings.language",
      "theme": "settings.theme",
      "light": "settings.light",
      "dark": "settings.dark",
      "system": "settings.system",
      "chinese": "settings.chinese",
      "english": "settings.english",

      // Home page
      "aiDeveloperPlatform": "home.aiDeveloperPlatform",
      "fastestPathToProduction": "home.fastestPathToProduction",
      "homeDescription": "home.homeDescription",
      "startBuilding": "home.startBuilding",
      "viewDocs": "home.viewDocs",
      "browseModels": "home.browseModels",
      "browseModelsDesc": "home.browseModelsDesc",
      "testInPlayground": "home.testInPlayground",
      "testInPlaygroundDesc": "home.testInPlaygroundDesc",
      "monitorUsage": "home.monitorUsage",
      "monitorUsageDesc": "home.monitorUsageDesc",
      "whatsNew": "home.whatsNew",
      "gpt5ProTitle": "home.gpt5ProTitle",
      "gpt5ProDesc": "home.gpt5ProDesc",
      "sora2Title": "home.sora2Title",
      "sora2Desc": "home.sora2Desc",
      "realtimeApiTitle": "home.realtimeApiTitle",
      "realtimeApiDesc": "home.realtimeApiDesc",
      "functionCallingTitle": "home.functionCallingTitle",
      "functionCallingDesc": "home.functionCallingDesc",
      "newFeature": "home.newFeature",
      "beta": "home.beta",
      "quickStart": "home.quickStart",
      "viewAPIKeys": "home.viewAPIKeys",
      "browseDocs": "home.browseDocs",

      // Models page
      "modelCatalog": "models.modelCatalog",
      "modelCatalogDesc": "models.modelCatalogDesc",
      "capabilityCategory": "models.capabilityCategory",
      "searchModels": "models.searchModels",
      "quickFilter": "models.quickFilter",
      "foundModels": "models.foundModels",
      "modelsCount": "models.modelsCount",
      "addPrivateChannel": "models.addPrivateChannel",
      "tryNow": "models.tryNow",
      "domestic": "models.domestic",
      "international": "models.international",
      "internal": "models.internal",
      "longContext": "models.longContext",
      "longOutput": "models.longOutput",
      "deepThinking": "models.deepThinking",
      "streaming": "models.streaming",
      "privateChannel": "models.privateChannel",
      "inputOutputLength": "models.inputOutputLength",
      "inputOutputPricing": "models.inputOutputPricing",
      "cachedReadPricing": "models.cachedReadPricing",
      "pricing": "models.pricing",
      "loadingModels": "models.loadingModels",
      "loading": "models.loading",
      "noFilterTags": "models.noFilterTags",
      "noModelsFound": "models.noModelsFound",
      "retry": "models.retry",

      // Metadata page
      "metadataManagement": "metadata.metadataManagement",
      "metadataManagementDesc": "metadata.metadataManagementDesc",
      "metadataType": "metadata.metadataType",
      "searchMetadata": "metadata.searchMetadata",
      "foundMetadata": "metadata.foundMetadata",
      "itemsCount": "metadata.itemsCount",

      // Common
      "star": "common.star",
      "toggleTheme": "common.toggleTheme",
    };

    const mappedKey = keyMap[key] || key;
    const [namespace, ...rest] = mappedKey.split('.');
    const translationKey = rest.join('.');

    try {
      switch(namespace) {
        case 'navigation':
          return tNav(translationKey);
        case 'settings':
          return tSettings(translationKey);
        case 'home':
          return tHome(translationKey);
        case 'models':
          return tModels(translationKey);
        case 'metadata':
          return tMetadata(translationKey);
        case 'common':
          return tCommon(translationKey);
        default:
          return key;
      }
    } catch {
      return key;
    }
  }

  return (
    <LanguageContext.Provider value={{ language, setLanguage, t }}>
      {children}
    </LanguageContext.Provider>
  )
}

export function useLanguage() {
  const context = useContext(LanguageContext)
  if (context === undefined) {
    throw new Error("useLanguage must be used within a LanguageProvider")
  }
  return context
}
