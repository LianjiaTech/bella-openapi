'use client';
import React, { createContext, useState, useEffect, useContext } from 'react';
import { getUserInfo } from '@/lib/api/userInfo';
import {UserInfo} from "@/lib/types/openapi";

interface UserContextType {
    userInfo: UserInfo | null;
    setUserInfo: React.Dispatch<React.SetStateAction<UserInfo | null>>;
    isLoading: boolean;
    error: Error | null;
    logout: () => void;
}

const UserContext = createContext<UserContextType | undefined>(undefined);

export const UserProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<Error | null>(null);

    useEffect(() => {
        // Skip userInfo fetch on login page
        if (window.location.pathname === '/login') {
            setIsLoading(false);
            return;
        }

        const fetchUserInfo = async () => {
            try {
                const info = await getUserInfo();
                setUserInfo(info);
            } catch (err) {
                setError(err instanceof Error ? err : new Error('An error occurred'));
            } finally {
                setIsLoading(false);
            }
        };

        fetchUserInfo();
    }, []);

    const logout = () => {
        // 清除用户信息状态
        setUserInfo(null);
        // 重置加载状态
        setIsLoading(false);
        // 清除错误状态
        setError(null);
        // 清除任何可能存在的本地存储
        if (typeof window !== 'undefined') {
            // 检查并清除任何与用户相关的本地存储
            localStorage.removeItem('user-preferences');
            sessionStorage.removeItem('user-session');
        }
    };

    return (
        <UserContext.Provider value={{ userInfo, setUserInfo, isLoading, error, logout }}>
            {children}
        </UserContext.Provider>
    );
};

export const useUser = () => {
    const context = useContext(UserContext);
    if (context === undefined) {
        throw new Error('useUser must be used within a UserProvider');
    }
    return context;
};
