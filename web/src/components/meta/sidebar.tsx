import {useState} from 'react';
import {ChevronDown, ChevronsDownUp, ChevronsUpDown, Search, X} from 'lucide-react';
import {CategoryTree, Endpoint} from '@/lib/types/openapi';
import {useSidebar} from '@/lib/context/sidebar-context';

// 动画样式组件
const AnimatedButton = ({children, className = '', ...props}: any) => (
    <button
        className={`transform transition-all duration-200 hover:scale-110 active:scale-95 ${className}`}
        {...props}
    >
        {children}
    </button>
);

export function Sidebar() {
    const {
        categoryTrees,
        selectedEndpoint,
        setSelectedEndpoint,
        collapsedCategories,
        setCollapsedCategories,
        toggleCategory,
        toggleAllCategories,
        isAllCollapsed
    } = useSidebar();

    const [inputValue, setInputValue] = useState('');
    const [searchTerm, setSearchTerm] = useState('');

    // 转义正则表达式特殊字符，防止用户输入导致正则错误
    const escapeRegExp = (string: string) => {
        return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    };

    const handleEndpointClick = (endpoint: string) => {
        setSelectedEndpoint(endpoint);
    };

    // 检查分类树是否包含匹配的 endpoint
    const hasMatchingEndpoints = (tree: CategoryTree, searchText: string): boolean => {
        if (!searchText.trim()) return false;
        const lowerSearch = searchText.toLowerCase();

        // 检查直接子 endpoints
        const hasDirectMatch = tree.endpoints?.some(ep =>
            ep.endpointName.toLowerCase().includes(lowerSearch) ||
            ep.endpoint.toLowerCase().includes(lowerSearch)
        ) ?? false;

        // 检查子分类的 endpoints
        const hasChildMatch = tree.children?.some(child =>
            child.endpoints?.some(ep =>
                ep.endpointName.toLowerCase().includes(lowerSearch) ||
                ep.endpoint.toLowerCase().includes(lowerSearch)
            )
        ) ?? false;

        return hasDirectMatch || hasChildMatch;
    };

    // 执行搜索
    const handleSearch = () => {
        if (!inputValue.trim()) {
            handleClearSearch();
            return;
        }

        setSearchTerm(inputValue);

        // 找出所有不包含匹配项的分类（这些需要被折叠）
        const newCollapsedCategories = new Set<string>();

        categoryTrees.forEach(tree => {
            if (!hasMatchingEndpoints(tree, inputValue)) {
                // 不包含匹配项的分类，添加到折叠列表
                newCollapsedCategories.add(tree.categoryCode);
            }
            // 包含匹配项的分类不添加，意味着它们会被展开
        });

        // 批量设置折叠状态
        setCollapsedCategories(newCollapsedCategories);
    };

    // 处理回车键
    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'Enter') {
            handleSearch();
        }
    };

    // 清空搜索
    const handleClearSearch = () => {
        setInputValue('');
        setSearchTerm('');
    };

    // 高亮显示匹配的文本
    const highlightText = (text: string) => {
        if (!searchTerm.trim()) {
            return <span>{text}</span>;
        }

        // 转义特殊字符，确保用户输入被当作字面量而非正则模式
        const escapedSearchTerm = escapeRegExp(searchTerm);
        const regex = new RegExp(`(${escapedSearchTerm})`, 'gi');
        const parts = text.split(regex);

        return (
            <span>
                {parts.map((part, index) =>
                    regex.test(part) ? (
                        <mark key={index}
                              className="bg-yellow-300 dark:bg-yellow-600 text-gray-900 dark:text-gray-100 px-0.5 rounded">
                            {part}
                        </mark>
                    ) : (
                        <span key={index}>{part}</span>
                    )
                )}
            </span>
        );
    };

    // 检查端点是否匹配搜索词
    const isMatchingSearch = (endpoint: Endpoint) => {
        if (!searchTerm.trim()) return true;
        const lowerSearchTerm = searchTerm.toLowerCase();
        return (
            endpoint.endpointName.toLowerCase().includes(lowerSearchTerm) ||
            endpoint.endpoint.toLowerCase().includes(lowerSearchTerm)
        );
    };

    const renderCategoryTree = (tree: CategoryTree) => {
        const isCollapsed = collapsedCategories.has(tree.categoryCode);

        return (
            <div key={tree.categoryCode} className="mb-4 last:mb-6">
                <button
                    onClick={() => toggleCategory(tree.categoryCode)}
                    className="flex items-center w-full font-semibold text-lg mb-3 text-gray-700 dark:text-gray-300 hover:text-gray-900 dark:hover:text-gray-100 transition-colors duration-200"
                >
                    <span className="flex-1 text-left">{tree.categoryName}</span>
                    <ChevronDown
                        className={`w-4 h-4 transition-transform duration-300 ${isCollapsed ? '' : 'rotate-180'}`}
                    />
                </button>

                <div
                    className={`overflow-hidden transition-all duration-300 ease-in-out ${
                        isCollapsed
                            ? 'max-h-0 opacity-0'
                            : 'max-h-[1000px] opacity-100'
                    }`}
                >
                    <div className="space-y-2">
                        {tree.endpoints && tree.endpoints.map(endpoint => {
                            const isActive = selectedEndpoint === endpoint.endpoint;
                            const isMatch = isMatchingSearch(endpoint);

                            return (
                                <div
                                    key={endpoint.endpointCode}
                                    className={`cursor-pointer p-2 rounded-lg transition-all duration-200 transform hover:scale-[1.02] ${
                                        isActive
                                            ? 'bg-gray-600 text-white dark:bg-gray-700'
                                            : isMatch
                                                ? 'text-gray-700 dark:text-gray-200 hover:text-gray-900 dark:hover:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-700'
                                                : 'text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-800'
                                    }`}
                                    onClick={() => handleEndpointClick(endpoint.endpoint)}
                                >
                                    {highlightText(endpoint.endpointName)}
                                </div>
                            );
                        })}
                        {tree.children && tree.children.map(child => (
                            <div key={child.categoryCode} className="mt-4">
                                <h3 className="font-medium text-base mb-2 text-gray-700 dark:text-gray-300">{child.categoryName}</h3>
                                <div className="space-y-2">
                                    {child.endpoints && child.endpoints.map(endpoint => {
                                        const isActive = selectedEndpoint === endpoint.endpoint;
                                        const isMatch = isMatchingSearch(endpoint);

                                        return (
                                            <div
                                                key={endpoint.endpointCode}
                                                className={`cursor-pointer p-2 rounded-lg transition-all duration-200 transform hover:scale-[1.02] ${
                                                    isActive
                                                        ? 'bg-gray-600 text-white dark:bg-gray-700'
                                                        : isMatch
                                                            ? 'text-gray-700 dark:text-gray-200 hover:text-gray-900 dark:hover:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-700'
                                                            : 'text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-800'
                                                }`}
                                                onClick={() => handleEndpointClick(endpoint.endpoint)}
                                            >
                                                {highlightText(endpoint.endpointName)}
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        );
    };

    return (
        <aside
            className="w-64 bg-white dark:bg-gray-800 shadow-md border-r border-gray-200 dark:border-gray-700 flex flex-col overflow-hidden">
            {/* 可滚动的分类列表 - 填满剩余空间，隐藏滚动条 */}
            <div className="flex-1 overflow-y-auto px-4 scrollbar-hide" style={{overscrollBehavior: 'contain'}}>
                {/* 顶部搜索栏和按钮 */}
                <div
                    className="sticky top-0 bg-white dark:bg-gray-800 pt-3 pb-3 mb-4 border-b border-gray-200 dark:border-gray-700 z-10">
                    <div className="flex items-center gap-2">
                        {/* 搜索框 */}
                        <div className="relative flex-1">
                            <input
                                type="text"
                                placeholder="搜索能力点"
                                value={inputValue}
                                onChange={(e) => setInputValue(e.target.value)}
                                onKeyDown={handleKeyDown}
                                className="w-full pl-3 pr-16 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-gray-400 dark:focus:ring-gray-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500"
                            />
                            {/* 搜索和清空按钮 */}
                            <div
                                className="absolute right-1.5 top-1/2 transform -translate-y-1/2 flex items-center gap-1">
                                {inputValue && (
                                    <button
                                        onClick={handleClearSearch}
                                        className="p-1 text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300 rounded transition-colors"
                                        title="清空"
                                    >
                                        <X className="w-3 h-3"/>
                                    </button>
                                )}
                                <button
                                    onClick={handleSearch}
                                    className="p-1 text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-600 rounded transition-colors"
                                    title="搜索 (Enter)"
                                >
                                    <Search className="w-3.5 h-3.5"/>
                                </button>
                            </div>
                        </div>

                        {/* 展开/收缩按钮 */}
                        <AnimatedButton
                            onClick={toggleAllCategories}
                            className="flex items-center justify-center w-8 h-8 text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-700 rounded transition-colors flex-shrink-0"
                            title={isAllCollapsed ? "全部展开" : "全部收起"}
                        >
                            {isAllCollapsed ? (
                                <ChevronsUpDown className="w-4 h-4"/>
                            ) : (
                                <ChevronsDownUp className="w-4 h-4"/>
                            )}
                        </AnimatedButton>
                    </div>
                </div>

                {categoryTrees.map(renderCategoryTree)}
            </div>

            <style jsx global>{`
                .scrollbar-hide {
                    -ms-overflow-style: none;
                    scrollbar-width: none;
                }

                .scrollbar-hide::-webkit-scrollbar {
                    display: none;
                }
            `}</style>
        </aside>
    );
}
