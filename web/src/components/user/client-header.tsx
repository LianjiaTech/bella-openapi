'use client'

import React from "react"
import { Skeleton } from "@/components/ui/skeleton"
import { UserNav } from "@/components/user/user-nav"
import { useUser } from "@/lib/context/user-context"
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { hasPermission } from "@/lib/api/userInfo"

interface Props {
    title?: string
}

const EmptyHeader: React.FC<Props> = ({ title }) => (
    <header className="flex justify-between items-center p-4 bg-white dark:bg-gray-900 border-b border-gray-200 dark:border-gray-800 shadow-sm">
        <div className="w-48">
            <h1 className="text-2xl font-bold text-gray-800 dark:text-gray-200 truncate">{title}</h1>
        </div>
    </header>
)

interface NavLinkProps {
    href: string;
    children: React.ReactNode;
    active?: boolean;
}

const NavLink: React.FC<NavLinkProps> = ({ href, children, active }) => {
    return (
        <Link
            href={href}
            className={`relative px-4 py-2 text-sm font-medium transition-all duration-200 ease-in-out ${
                active 
                ? 'text-gray-800 dark:text-gray-200' 
                : 'text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200'
            }`}
        >
            {children}
            {active ? (
                <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-gray-800 dark:bg-gray-200 rounded-full transform origin-left transition-transform duration-200 ease-out" />
            ) : (
                <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-transparent hover:bg-gray-300 dark:hover:bg-gray-700 rounded-full transform origin-left transition-all duration-200 ease-out opacity-0 hover:opacity-100" />
            )}
        </Link>
    );
};

const MainNav: React.FC<{ userInfo: any }> = ({ userInfo }) => {
    const pathname = usePathname();

    const navItems = [
        { href: '/', label: '主页' },
        { href: '/playground', label: 'Playground' },
        { href: '/apikey', label: 'API Key管理' },
        { href: '/meta/console', label: '元数据管理', permission: '/console/model/**' },
        { href: '/monitor', label: '能力点监控' },
        { href: '/logs', label: '日志查询' },
        { href: 'https://doc.bella.top', label: 'Docs' }
    ];

    return (
        <nav className="flex items-center h-12">
            {navItems.map((item) => (
                (!item.permission || hasPermission(userInfo, item.permission)) && (
                    <NavLink
                        key={item.href}
                        href={item.href}
                        active={pathname === item.href || pathname.startsWith(`${item.href}/`)}
                    >
                        {item.label}
                    </NavLink>
                )
            ))}
        </nav>
    );
};

const SessionHeader: React.FC<Props & { userInfo: any }> = ({ title, userInfo }) => (
    <header className="sticky top-0 z-50 bg-white dark:bg-gray-900 border-b border-gray-200 dark:border-gray-800 shadow-sm backdrop-blur-sm bg-opacity-90 dark:bg-opacity-90">
        <div className="w-full px-4">
            <div className="flex justify-between items-center h-16">
                <div className="flex items-center">
                    <div className="w-48 mr-2">
                        <h1 className="text-xl font-bold text-gray-800 dark:text-gray-200 truncate">{title || "Bella Openapi"}</h1>
                    </div>
                    <MainNav userInfo={userInfo} />
                </div>
                <UserNav user={userInfo} />
            </div>
        </div>
    </header>
)

export const ClientHeader: React.FC<Props> = ({ title }) => {
    const { userInfo, isLoading, error } = useUser();

    if (isLoading) {
        return <Skeleton className="w-full h-16" />
    }

    if (error) {
        return <EmptyHeader title={title} />;
    }

    if (userInfo) {
        return <SessionHeader title={title} userInfo={userInfo} />;
    }

    return <EmptyHeader title={title} />;
}
