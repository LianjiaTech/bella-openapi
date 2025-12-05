'use client';
import React, { createContext, useState, useEffect, useContext, useCallback, useRef } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { getAllCategoryTrees } from '@/lib/api/meta';
import { CategoryTree } from '@/lib/types/openapi';

interface SidebarContextType {
    categoryTrees: CategoryTree[];
    selectedEndpoint: string;
    setSelectedEndpoint: (endpoint: string) => void;
    collapsedCategories: Set<string>;
    setCollapsedCategories: (categories: Set<string>) => void;
    toggleCategory: (categoryCode: string) => void;
    toggleAllCategories: () => void;
    isAllCollapsed: boolean;
    isLoading: boolean;
}

const SidebarContext = createContext<SidebarContextType | undefined>(undefined);

export const SidebarProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const searchParams = useSearchParams();
    const router = useRouter();
    const endpointParam = searchParams.get('endpoint');

    const [categoryTrees, setCategoryTrees] = useState<CategoryTree[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const isInitialized = useRef(false);

    // 初始化 selectedEndpoint: URL 参数 > sessionStorage > 默认值
    const [selectedEndpoint, setSelectedEndpointState] = useState<string>(() => {
        if (endpointParam) return endpointParam;

        if (typeof window !== 'undefined') {
            const saved = sessionStorage.getItem('sidebar-selected-endpoint');
            if (saved) return saved;
        }

        return '/v1/chat/completions';
    });

    // 初始化折叠状态：从 sessionStorage 恢复
    const [collapsedCategories, setCollapsedCategories] = useState<Set<string>>(() => {
        if (typeof window !== 'undefined') {
            const saved = sessionStorage.getItem('sidebar-collapsed-categories');
            if (saved) {
                try {
                    return new Set<string>(JSON.parse(saved));
                } catch (e) {
                    return new Set<string>();
                }
            }
        }
        return new Set<string>();
    });

    const [isAllCollapsed, setIsAllCollapsed] = useState(false);

    // 加载 category trees
    useEffect(() => {
        async function fetchCategoryTrees() {
            try {
                setIsLoading(true);
                const trees = await getAllCategoryTrees();
                setCategoryTrees(trees);
            } catch (error) {
                console.error('Error fetching category trees:', error);
            } finally {
                setIsLoading(false);
            }
        }
        fetchCategoryTrees();
    }, []);

    // 初始化时仅展开包含 selectedEndpoint 的 category（只执行一次，且只在没有保存状态时）
    useEffect(() => {
        if (!isInitialized.current && selectedEndpoint && categoryTrees.length > 0) {
            // 检查是否有保存的状态
            const hasSavedState = typeof window !== 'undefined' &&
                sessionStorage.getItem('sidebar-collapsed-categories');

            if (!hasSavedState) {
                // 没有保存状态时，使用默认逻辑
                const categoryToExpand = categoryTrees.find(tree =>
                    tree.endpoints?.some(ep => ep.endpoint === selectedEndpoint) ||
                    tree.children?.some(child =>
                        child.endpoints?.some(ep => ep.endpoint === selectedEndpoint)
                    )
                );

                // 默认全部收缩，然后仅展开包含 selectedEndpoint 的 category
                const allCategoryCodes = new Set(categoryTrees.map(tree => tree.categoryCode));
                if (categoryToExpand) {
                    allCategoryCodes.delete(categoryToExpand.categoryCode);
                }
                setCollapsedCategories(allCategoryCodes);

                // 保存初始状态到 sessionStorage
                sessionStorage.setItem('sidebar-collapsed-categories', JSON.stringify(Array.from(allCategoryCodes)));
            }

            isInitialized.current = true;
        }
    }, [categoryTrees, selectedEndpoint]);

    // 设置选中的 endpoint（保存到 sessionStorage 并更新 URL）
    const setSelectedEndpoint = useCallback((endpoint: string) => {
        setSelectedEndpointState(endpoint);

        // 保存到 sessionStorage
        if (typeof window !== 'undefined') {
            sessionStorage.setItem('sidebar-selected-endpoint', endpoint);
        }

        // 更新 URL
        const url = new URL(window.location.href);
        url.searchParams.set('endpoint', endpoint);
        router.push(url.pathname + url.search);
    }, [router]);

    // 当 URL 参数变化时同步更新 selectedEndpoint
    useEffect(() => {
        if (endpointParam && endpointParam !== selectedEndpoint) {
            setSelectedEndpointState(endpointParam);
        }
    }, [endpointParam]);

    // 自动更新 isAllCollapsed 状态
    useEffect(() => {
        if (categoryTrees.length === 0) return;

        const allCategoryCodes = categoryTrees.map(tree => tree.categoryCode);
        const allCollapsed = allCategoryCodes.every(code => collapsedCategories.has(code));
        setIsAllCollapsed(allCollapsed);
    }, [collapsedCategories, categoryTrees]);

    // 保存到 sessionStorage 的辅助函数
    const saveToSessionStorage = useCallback((categories: Set<string>) => {
        if (typeof window !== 'undefined') {
            sessionStorage.setItem('sidebar-collapsed-categories', JSON.stringify(Array.from(categories)));
        }
    }, []);

    // 批量设置折叠状态（带 sessionStorage 保存）
    const setCollapsedCategoriesWithStorage = useCallback((categories: Set<string>) => {
        setCollapsedCategories(categories);
        saveToSessionStorage(categories);
    }, [saveToSessionStorage]);

    // 切换单个分类的折叠状态
    const toggleCategory = useCallback((categoryCode: string) => {
        setCollapsedCategories(prev => {
            const newCollapsed = new Set(prev);
            if (newCollapsed.has(categoryCode)) {
                newCollapsed.delete(categoryCode);
            } else {
                newCollapsed.add(categoryCode);
            }
            saveToSessionStorage(newCollapsed);
            return newCollapsed;
        });
    }, [saveToSessionStorage]);

    // 全部展开/收起
    const toggleAllCategories = useCallback(() => {
        const newCategories = isAllCollapsed
            ? new Set<string>()  // 展开全部
            : new Set(categoryTrees.map(tree => tree.categoryCode));  // 折叠全部

        setCollapsedCategoriesWithStorage(newCategories);
    }, [isAllCollapsed, categoryTrees, setCollapsedCategoriesWithStorage]);

    return (
        <SidebarContext.Provider value={{
            categoryTrees,
            selectedEndpoint,
            setSelectedEndpoint,
            collapsedCategories,
            setCollapsedCategories: setCollapsedCategoriesWithStorage,
            toggleCategory,
            toggleAllCategories,
            isAllCollapsed,
            isLoading
        }}>
            {children}
        </SidebarContext.Provider>
    );
};

export const useSidebar = () => {
    const context = useContext(SidebarContext);
    if (context === undefined) {
        throw new Error('useSidebar must be used within a SidebarProvider');
    }
    return context;
};
