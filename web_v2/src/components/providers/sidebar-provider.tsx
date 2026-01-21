"use client"

import React, { createContext, useState, useEffect, useContext, useCallback, useRef } from "react"
import { useSearchParams, useRouter } from "next/navigation"
import { getAllCategoryTrees } from "@/lib/api/meta"
import { CategoryTree } from "@/lib/types/openapi"
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
}

const SidebarContext = createContext<SidebarContextType | undefined>(undefined)

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

  // Initialize selectedEndpoint: URL params > sessionStorage > default
  const [selectedEndpoint, setSelectedEndpointState] = useState<string>(() => {
    if (endpointParam) return endpointParam

    const saved = safeGetItem("sidebar-selected-endpoint")
    if (saved) return saved

    return DEFAULT_ENDPOINT
  })

  // Initialize collapsed state from sessionStorage
  const [collapsedCategories, setCollapsedCategories] = useState<Set<string>>(() => {
    const saved = safeGetJSON<string[]>("sidebar-collapsed-categories", [])
    return new Set<string>(saved)
  })

  const [isAllCollapsed, setIsAllCollapsed] = useState(false)

  // Load category trees
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

  // Initialize: expand only the category containing selectedEndpoint (once, when no saved state)
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

        // Collapse all by default, then expand only the category containing selectedEndpoint
        const allCategoryCodes = new Set(categoryTrees.map(tree => tree.categoryCode))
        if (categoryToExpand) {
          allCategoryCodes.delete(categoryToExpand.categoryCode)
        }
        setCollapsedCategories(allCategoryCodes)

        // Save initial state to sessionStorage
        safeSetJSON("sidebar-collapsed-categories", Array.from(allCategoryCodes))
      }

      isInitialized.current = true
    }
  }, [categoryTrees, selectedEndpoint])

  // Set selected endpoint (save to sessionStorage and update URL)
  const setSelectedEndpoint = useCallback((endpoint: string) => {
    setSelectedEndpointState(endpoint)

    // Save to sessionStorage
    safeSetItem("sidebar-selected-endpoint", endpoint)

    // Update URL
    const url = new URL(window.location.href)
    url.searchParams.set("endpoint", endpoint)
    router.push(url.pathname + url.search)
  }, [router])

  // Sync selectedEndpoint when URL params change
  useEffect(() => {
    if (endpointParam && endpointParam !== selectedEndpoint) {
      setSelectedEndpointState(endpointParam)
    }
  }, [endpointParam, selectedEndpoint])

  // Auto-update isAllCollapsed state
  useEffect(() => {
    if (categoryTrees?.length === 0) return

    const allCategoryCodes = categoryTrees?.map(tree => tree.categoryCode) || []
    const allCollapsed = allCategoryCodes.every(code => collapsedCategories.has(code))
    setIsAllCollapsed(allCollapsed)
  }, [collapsedCategories, categoryTrees])

  // Save to sessionStorage helper
  const saveToSessionStorage = useCallback((categories: Set<string>) => {
    safeSetJSON("sidebar-collapsed-categories", Array.from(categories))
  }, [])

  // Batch set collapsed state (with sessionStorage save)
  const setCollapsedCategoriesWithStorage = useCallback((categories: Set<string>) => {
    setCollapsedCategories(categories)
    saveToSessionStorage(categories)
  }, [saveToSessionStorage])

  // Toggle single category
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

  // Toggle all categories
  const toggleAllCategories = useCallback(() => {
    const newCategories = isAllCollapsed
      ? new Set<string>() // Expand all
      : new Set(categoryTrees.map(tree => tree.categoryCode)) // Collapse all

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
      }}
    >
      {children}
    </SidebarContext.Provider>
  )
}

// Re-export useSidebar hook for convenience
export function useSidebar() {
  const context = useContext(SidebarContext)
  if (context === undefined) {
    throw new Error("useSidebar must be used within a SidebarProvider")
  }
  return context
}

// Export as NavigationProvider for backward compatibility
export const NavigationProvider = SidebarProvider