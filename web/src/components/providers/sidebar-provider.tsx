"use client"

import React, { createContext, useState, useEffect, useContext, useCallback, useRef } from "react"
import { useSearchParams, useRouter } from "next/navigation"
import { getAllCategoryTrees } from "@/lib/api/meta"
import { CategoryTree } from "@/lib/types/models"
import { DEFAULT_ENDPOINT } from "@/lib/constants/constants"
import { safeGetItem, safeGetJSON, safeSetItem, safeSetJSON } from "@/lib/utils/storage"

type SidebarContextType = {
  categoryTrees: CategoryTree[]
  selectedEndpoint: string
  setSelectedEndpoint: (endpoint: string) => void
  collapsedCategories: Set<string>
  setCollapsedCategories: (categories: Set<string>) => void
  toggleCategory: (categoryCode: string) => void
  toggleAllCategories: () => void
  isAllCollapsed: boolean
  isLoading: boolean
  isDesktop: boolean
  isSidebarOpen: boolean
  isSidebarExpanded: boolean
  isSidebarManuallyCollapsed: boolean
  openSidebar: () => void
  closeSidebar: () => void
  toggleSidebar: () => void
  toggleSidebarExpanded: () => void
}

const SidebarContext = createContext<SidebarContextType | undefined>(undefined)
const DESKTOP_BREAKPOINT = 1280

type SidebarProviderProps = {
  children: React.ReactNode
}

export function SidebarProvider({ children }: SidebarProviderProps) {
  const searchParams = useSearchParams()
  const router = useRouter()
  const endpointParam = searchParams.get("endpoint")

  const [categoryTrees, setCategoryTrees] = useState<CategoryTree[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const isInitialized = useRef(false)

  const [selectedEndpoint, setSelectedEndpointState] = useState<string>(() => {
    if (endpointParam) return endpointParam

    const saved = safeGetItem("sidebar-selected-endpoint")
    if (saved) return saved

    return DEFAULT_ENDPOINT
  })

  const [collapsedCategories, setCollapsedCategories] = useState<Set<string>>(() => {
    const saved = safeGetJSON<string[]>("sidebar-collapsed-categories", [])
    return new Set<string>(saved)
  })

  const [isAllCollapsed, setIsAllCollapsed] = useState(false)
  const [isDesktop, setIsDesktop] = useState(() => {
    if (typeof window === "undefined") return true
    return window.innerWidth >= DESKTOP_BREAKPOINT
  })
  const [isSidebarOpen, setIsSidebarOpen] = useState(() => {
    if (typeof window === "undefined") return true
    return window.innerWidth >= DESKTOP_BREAKPOINT
  })
  const [isSidebarExpanded, setIsSidebarExpanded] = useState(true)
  const [isSidebarManuallyCollapsed, setIsSidebarManuallyCollapsed] = useState(false)

  useEffect(() => {
    async function fetchCategoryTrees() {
      try {
        setIsLoading(true)
        const trees = await getAllCategoryTrees()
        setCategoryTrees(trees)
      } catch (error) {
        console.error("Error fetching category trees:", error)
      } finally {
        setIsLoading(false)
      }
    }
    fetchCategoryTrees()
  }, [])

  useEffect(() => {
    if (typeof window === "undefined") return

    const syncViewportState = () => {
      const desktop = window.innerWidth >= DESKTOP_BREAKPOINT
      setIsDesktop(desktop)
      setIsSidebarOpen(prev => (desktop ? prev : false))
    }

    syncViewportState()
    window.addEventListener("resize", syncViewportState)

    return () => {
      window.removeEventListener("resize", syncViewportState)
    }
  }, [])

  useEffect(() => {
    if (!isInitialized.current && selectedEndpoint && categoryTrees?.length > 0) {
      const hasSavedState = safeGetItem("sidebar-collapsed-categories") !== null

      if (!hasSavedState) {
        const categoryToExpand = categoryTrees.find(tree =>
          tree.endpoints?.some(ep => ep.endpoint === selectedEndpoint) ||
          tree.children?.some(child =>
            child.endpoints?.some(ep => ep.endpoint === selectedEndpoint)
          )
        )

        const allCategoryCodes = new Set(categoryTrees.map(tree => tree.categoryCode))
        if (categoryToExpand) {
          allCategoryCodes.delete(categoryToExpand.categoryCode)
        }
        setCollapsedCategories(allCategoryCodes)
        safeSetJSON("sidebar-collapsed-categories", Array.from(allCategoryCodes))
      }

      isInitialized.current = true
    }
  }, [categoryTrees, selectedEndpoint])

  const setSelectedEndpoint = useCallback((endpoint: string) => {
    setSelectedEndpointState(endpoint)
    safeSetItem("sidebar-selected-endpoint", endpoint)

    const url = new URL(window.location.href)
    url.searchParams.set("endpoint", endpoint)
    router.push(url.pathname + url.search)
  }, [router])

  const openSidebar = useCallback(() => {
    setIsSidebarManuallyCollapsed(false)
    setIsSidebarOpen(true)
  }, [])

  const closeSidebar = useCallback(() => {
    if (!isDesktop) {
      setIsSidebarManuallyCollapsed(true)
      setIsSidebarOpen(false)
    }
  }, [isDesktop])

  const toggleSidebar = useCallback(() => {
    if (!isDesktop) {
      setIsSidebarManuallyCollapsed(false)
      setIsSidebarOpen(prev => !prev)
    }
  }, [isDesktop])

  const toggleSidebarExpanded = useCallback(() => {
    const next = !isSidebarExpanded
    setIsSidebarExpanded(next)
    setIsSidebarManuallyCollapsed(!next)
    if (!next) {
      setIsSidebarOpen(false)
    }
  }, [isSidebarExpanded])

  useEffect(() => {
    if (endpointParam && endpointParam !== selectedEndpoint) {
      setSelectedEndpointState(endpointParam)
    }
  }, [endpointParam, selectedEndpoint])

  useEffect(() => {
    if (categoryTrees?.length === 0) return

    const allCategoryCodes = categoryTrees.map(tree => tree.categoryCode)
    const allCollapsed = allCategoryCodes.every(code => collapsedCategories.has(code))
    setIsAllCollapsed(allCollapsed)
  }, [collapsedCategories, categoryTrees])

  const saveToSessionStorage = useCallback((categories: Set<string>) => {
    safeSetJSON("sidebar-collapsed-categories", Array.from(categories))
  }, [])

  const setCollapsedCategoriesWithStorage = useCallback((categories: Set<string>) => {
    setCollapsedCategories(categories)
    saveToSessionStorage(categories)
  }, [saveToSessionStorage])

  const toggleCategory = useCallback((categoryCode: string) => {
    setCollapsedCategories(prev => {
      const newCollapsed = new Set(prev)
      if (newCollapsed.has(categoryCode)) {
        newCollapsed.delete(categoryCode)
      } else {
        newCollapsed.add(categoryCode)
      }
      saveToSessionStorage(newCollapsed)
      return newCollapsed
    })
  }, [saveToSessionStorage])

  const toggleAllCategories = useCallback(() => {
    const newCategories = isAllCollapsed
      ? new Set<string>()
      : new Set(categoryTrees.map(tree => tree.categoryCode))

    setCollapsedCategoriesWithStorage(newCategories)
  }, [isAllCollapsed, categoryTrees, setCollapsedCategoriesWithStorage])

  return (
    <SidebarContext.Provider
      value={{
        categoryTrees,
        selectedEndpoint,
        setSelectedEndpoint,
        collapsedCategories,
        setCollapsedCategories: setCollapsedCategoriesWithStorage,
        toggleCategory,
        toggleAllCategories,
        isAllCollapsed,
        isLoading,
        isDesktop,
        isSidebarOpen,
        isSidebarExpanded,
        isSidebarManuallyCollapsed,
        openSidebar,
        closeSidebar,
        toggleSidebar,
        toggleSidebarExpanded,
      }}
    >
      {children}
    </SidebarContext.Provider>
  )
}

export function useSidebar() {
  const context = useContext(SidebarContext)
  if (context === undefined) {
    throw new Error("useSidebar must be used within a SidebarProvider")
  }
  return context
}

export const NavigationProvider = SidebarProvider
