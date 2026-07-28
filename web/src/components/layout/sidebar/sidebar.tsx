'use client';

import { Link, usePathname } from '@/i18n/routing';
import { useSearchParams } from 'next/navigation';
import { useState, useEffect, useMemo } from 'react';
import {
  Home,
  Sparkles,
  FlaskConical,
  Key,
  ScrollText,
  HelpCircle,
  ChevronDown,
  MessageSquare,
  Brain,
  Mic,
  FileAudio,
  Volume2,
  Radio,
  ImageIcon,
  FileText,
  WorkflowIcon,
  ScanText,
  Settings,
  type LucideIcon,
  LogOut,
  Wand2,
  MessageCircle,
  Shield,
  KeySquare,
  Database,
  X,
  Activity,
  ReceiptText,
  ListFilter,
  Wrench,
} from "lucide-react"
import { useLanguage } from "../../providers/language-provider"
import { useAuth } from "../../providers/auth-provider"
import { useSidebar } from "../../providers/sidebar-provider"
import { cn } from "@/lib/utils"
import { Button } from "@/components/common/button"
import { SettingsDialog } from "./settings-dialog"
import { logout } from '@/lib/api/auth';
import { hasPermission } from '@/lib/utils/permission';
import { API_KEY_NAV_ITEM, getSidebarActivePathname } from './sidebar-active';

interface NavItem {
  label: string;
  href?: string;
  icon: LucideIcon;
  children?: NavItem[];
}

const BASE_NAV_ITEMS: NavItem[] = [
  { label: 'home', href: '/overview', icon: Home },
  { label: 'models', href: '/models', icon: FlaskConical },
  {
    label: 'playground',
    icon: Sparkles,
    children: [
      { label: 'intelligentQA', href: '/playground/chat', icon: MessageSquare },
      { label: 'vectorization', href: '/playground/embedding', icon: Brain },
      { label: 'rerank', href: '/playground/rerank', icon: ListFilter },
      { label: 'speechSynthesis', href: '/playground/audio/tts', icon: Mic },
      { label: 'fileTranscription', href: '/playground/audio/asr/transcription', icon: FileAudio },
      { label: 'speechRecognition', href: '/playground/audio/asr/flash', icon: Volume2 },
      { label: 'realtimeRecognition', href: '/playground/audio/asr/realtime/transcription', icon: Radio },
      { label: 'realtimeConversation', href: '/playground/audio/asr/realtime/chat', icon: MessageCircle },
      { label: 'textToImage', href: '/playground/images/generations', icon: ImageIcon },
      { label: 'imageToImage', href: '/playground/images/edits', icon: Wand2 },
      { label: 'documentParsing', href: '/playground/docparse', icon: FileText },
      { label: 'aiWorkflow', href: '/playground/workflow', icon: WorkflowIcon },
      { label: 'ocr', href: '/playground/ocr', icon: ScanText },
    ]
  },
  { ...API_KEY_NAV_ITEM, icon: Key },
  { label: 'logs', href: '/logs', icon: ScrollText },
  { label: 'billing', href: '/billing', icon: ReceiptText },
  { label: 'modelStatus', href: '/status', icon: HelpCircle },
  { label: 'channelHealth', href: '/channel-health', icon: Activity },
];

const ADMIN_NAV_GROUP: NavItem = {
  label: '管理员',
  icon: Shield,
  children: [
    { label: 'API Key 管理', href: '/apikey-admin', icon: KeySquare },
    { label: 'adminBilling', href: '/billing-admin', icon: ReceiptText },
    { label: '运维工具', href: '/ops-tools', icon: Wrench },
    { label: '元数据管理', href: '/metadata', icon: Database },
  ],
};

export default function Sidebar() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { t } = useLanguage();
  const { user } = useAuth();
  const {
    isDesktop,
    isSidebarOpen,
    closeSidebar,
    isSidebarExpanded,
    isSidebarManuallyCollapsed,
  } = useSidebar();
  const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set());
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const shouldSuppressSidebar = isSidebarManuallyCollapsed;
  const shouldSuppressMobileSidebar = !isDesktop && shouldSuppressSidebar;
  const isMobileSidebarOpen = !shouldSuppressMobileSidebar && isSidebarOpen;
  const activePathname = useMemo(
    () => getSidebarActivePathname(pathname, searchParams.get('viewer')),
    [pathname, searchParams]
  );

  const navItems = useMemo((): NavItem[] => {
    if (hasPermission(user, '/console/**')) {
      return [...BASE_NAV_ITEMS, ADMIN_NAV_GROUP];
    }
    return BASE_NAV_ITEMS;
  }, [user]);

  const toggleExpanded = (label: string) => {
    setExpandedItems(prev => {
      const next = new Set(prev);
      if (next.has(label)) {
        next.delete(label);
      } else {
        next.add(label);
      }
      return next;
    });
  };

  const handleNavigation = () => {
    if (!isDesktop) {
      closeSidebar();
    }
  };

  useEffect(() => {
    if (activePathname?.startsWith('/playground/')) {
      setExpandedItems(prev => new Set(prev).add('playground'));
    }
    if (activePathname?.startsWith('/apikey-admin') || activePathname?.startsWith('/billing-admin') || activePathname?.startsWith('/metadata') || activePathname?.startsWith('/ops-tools')) {
      setExpandedItems(prev => new Set(prev).add('管理员'));
    }
  }, [activePathname]);

  const sidebarContent = (
    <>
      <div className="flex h-16 items-center justify-between border-b border-sidebar-border px-6">
        <Link href="/" className="flex items-center gap-2" onClick={handleNavigation}>
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary">
            <Sparkles className="h-5 w-5 text-primary-foreground" />
          </div>
          <span className="text-lg font-semibold text-sidebar-foreground">OpenAPI 服务平台</span>
        </Link>
        {!isDesktop && (
          <Button variant="ghost" size="icon" onClick={closeSidebar} aria-label={t("close")}>
            <X className="h-5 w-5" />
          </Button>
        )}
      </div>

      <nav className="flex-1 p-4 space-y-1 overflow-y-auto">
        {navItems.map((item) => {
          const Icon = item.icon;

          if (item.children) {
            const isExpanded = expandedItems.has(item.label);
            const isAnyChildActive = item.children.some(
              child => activePathname === child.href || activePathname?.startsWith(child.href + '/')
            );

            return (
              <div key={item.label}>
                <button
                  onClick={() => toggleExpanded(item.label)}
                  className={cn(
                    "w-full flex items-center justify-between px-4 py-3 rounded-lg transition-all text-sm",
                    isAnyChildActive
                      ? "bg-sidebar-accent text-sidebar-primary font-medium"
                      : "text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
                  )}
                >
                  <div className="flex items-center space-x-3">
                    <Icon className="w-5 h-5" />
                    <span>{t(item.label)}</span>
                  </div>
                  <ChevronDown
                    className={cn(
                      "w-4 h-4 transition-transform duration-200",
                      isExpanded && "rotate-180"
                    )}
                  />
                </button>

                <div
                  className={cn(
                    "overflow-hidden transition-all duration-200 ease-in-out",
                    isExpanded ? "max-h-[600px] opacity-100" : "max-h-0 opacity-0"
                  )}
                >
                  <div className="ml-4 mt-1 space-y-1">
                    {item.children.map((child) => {
                      const isChildActive = activePathname === child.href || activePathname?.startsWith(child.href + '/');
                      const ChildIcon = child.icon;

                      return (
                        <Link
                          key={child.href}
                          href={child.href!}
                          onClick={handleNavigation}
                          className={cn(
                            "flex items-center space-x-3 px-4 py-2 rounded-lg transition-colors text-sm",
                            isChildActive
                              ? "bg-sidebar-accent text-sidebar-primary font-medium"
                              : "text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
                          )}
                        >
                          <ChildIcon className="w-4 h-4" />
                          <span>{t(child.label)}</span>
                        </Link>
                      );
                    })}
                  </div>
                </div>
              </div>
            );
          }

          const isActive = activePathname === item.href || activePathname?.startsWith(item.href + '/');

          return (
            <Link
              key={item.href}
              href={item.href!}
              onClick={handleNavigation}
              className={cn(
                "flex items-center space-x-3 px-4 py-3 rounded-lg transition-colors text-sm",
                isActive
                  ? "bg-sidebar-accent text-sidebar-primary font-medium"
                  : "text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
              )}
            >
              <Icon className="w-5 h-5" />
              <span>{t(item.label)}</span>
            </Link>
          );
        })}
      </nav>

      <div className="border-t border-sidebar-border p-3 space-y-1">
        <button
          onClick={() => setIsSettingsOpen(true)}
          className={cn(
            "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors w-full",
            "text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
          )}
        >
          <Settings className="h-5 w-5" />
          {t("settings")}
        </button>

        <button
          onClick={() => {
            if (!isDesktop) {
              closeSidebar();
            }
            logout();
            window.location.href = '/login';
          }}
          className={cn(
            "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
            pathname === "/logout"
              ? "bg-sidebar-accent text-sidebar-primary"
              : "text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
          )}
        >
          <LogOut className="h-5 w-5" />
          {t("logout")}
        </button>
      </div>

      <SettingsDialog
        open={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
      />
    </>
  )

  return (
    <>
      {!isDesktop && !shouldSuppressMobileSidebar && (
        <div
          onClick={closeSidebar}
          className={cn(
            "fixed inset-0 z-40 bg-black/40 transition-opacity duration-200",
            isMobileSidebarOpen ? "opacity-100" : "pointer-events-none opacity-0"
          )}
        />
      )}
      <aside
        className={cn(
          "bg-sidebar border-sidebar-border flex flex-col h-screen top-0 overflow-hidden",
          isDesktop
            ? cn(
                "fixed left-0 z-30 border-r",
                shouldSuppressSidebar ? "transition-none" : "transition-[width] duration-200",
                isSidebarExpanded ? "w-64" : "w-0 border-r-0"
              )
            : cn(
                "fixed left-0 z-50 w-64 border-r shadow-xl",
                shouldSuppressSidebar
                  ? "pointer-events-none -translate-x-full transition-none"
                  : "transition-transform duration-200 ease-out"
              ),
          !isDesktop &&
            !shouldSuppressMobileSidebar &&
            (isMobileSidebarOpen ? "translate-x-0" : "-translate-x-full")
        )}
      >
        {sidebarContent}
      </aside>
    </>
  );
}
