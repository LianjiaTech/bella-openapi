'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useState, useEffect } from 'react';
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
  Volume2,
  Radio,
  ImageIcon,
  Database,
  Network,
  FileText,
  WorkflowIcon,
  Search,
  ScanText,
  Settings,
  Activity,
  FolderTree,
  LogIn,
  type LucideIcon,
  LogOut,
} from "lucide-react"
import { useLanguage } from "@/components/language-provider"
import { cn } from "@/lib/utils"


interface NavItem {
  label: string;
  href?: string;
  icon: LucideIcon;
  children?: NavItem[];
}

const navItems: NavItem[] = [
  { label: 'home', href: '/overview', icon: Home },
  { label: 'models', href: '/models', icon: FlaskConical },
  {
    label: 'playground',
    icon: Sparkles,
    children: [
      { label: 'chat', href: '/playground/chat', icon: MessageSquare },
      { label: 'embedding', href: '/playground/embedding', icon: Brain },
      { label: 'audio', href: '/playground/audio', icon: Mic },
      { label: 'tts', href: '/playground/tts', icon: Volume2 },
      { label: 'realtime', href: '/playground/realtime', icon: Radio },
      { label: 'images', href: '/playground/images', icon: ImageIcon },
      { label: 'knowledge', href: '/playground/knowledge', icon: Database },
      { label: 'rag', href: '/playground/rag', icon: Network },
      { label: 'docparse', href: '/playground/docparse', icon: FileText },
      { label: 'workflow', href: '/playground/workflow', icon: WorkflowIcon },
      { label: 'search', href: '/playground/search', icon: Search },
      { label: 'ocr', href: '/playground/ocr', icon: ScanText },
    ]
  },
  { label: 'apiKeys', href: '/api-keys', icon: Key },
  { label: 'logs', href: '/analytics', icon: ScrollText },
  { label: 'modelStatus', href: '/status', icon: HelpCircle },
  { label: 'metadata', href: '/metadata', icon: FolderTree },
];

export default function Sidebar() {
  const pathname = usePathname();
  const { t } = useLanguage();
  const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set());

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

  // Auto-expand if user is currently on a child route
  useEffect(() => {
    if (pathname?.startsWith('/playground/')) {
      setExpandedItems(prev => new Set(prev).add('playground'));
    }
  }, [pathname]);


  return (
    <aside className="w-64 bg-white border-r border-gray-200 flex flex-col h-screen fixed left-0 top-0">
      {/* Logo */}
      <div className="flex h-16 items-center border-b border-sidebar-border px-6">
        <Link href="/" className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary">
            <Sparkles className="h-5 w-5 text-primary-foreground" />
          </div>
          <span className="text-lg font-semibold text-sidebar-foreground">Bella</span>
        </Link>
      </div>

      {/* Navigation */}
      <nav className="flex-1 p-4 space-y-1 overflow-y-auto">
        {navItems.map((item) => {
          const Icon = item.icon;

          // Check if this item has children
          if (item.children) {
            const isExpanded = expandedItems.has(item.label);
            const isAnyChildActive = item.children.some(
              child => pathname === child.href || pathname?.startsWith(child.href + '/')
            );

            return (
              <div key={item.label}>
                {/* Parent button (collapsible) */}
                <button
                  onClick={() => toggleExpanded(item.label)}
                  className={cn(
                    "w-full flex items-center justify-between px-4 py-3 rounded-lg transition-all text-sm",
                    isAnyChildActive
                      ? "bg-blue-50 text-blue-700 font-medium"
                      : "text-gray-700 hover:bg-gray-50 hover:text-gray-900"
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

                {/* Children (collapsible) */}
                <div
                  className={cn(
                    "overflow-hidden transition-all duration-200 ease-in-out",
                    isExpanded ? "max-h-[600px] opacity-100" : "max-h-0 opacity-0"
                  )}
                >
                  <div className="ml-4 mt-1 space-y-1">
                    {item.children.map((child) => {
                      const isChildActive = pathname === child.href || pathname?.startsWith(child.href + '/');
                      const ChildIcon = child.icon;

                      return (
                        <Link
                          key={child.href}
                          href={child.href!}
                          className={cn(
                            "flex items-center space-x-3 px-4 py-2 rounded-lg transition-colors text-sm",
                            isChildActive
                              ? "bg-blue-100 text-blue-700 font-medium"
                              : "text-gray-600 hover:bg-gray-50 hover:text-gray-900"
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

          // Regular item without children (unchanged)
          const isActive = pathname === item.href || pathname?.startsWith(item.href + '/');

          return (
            <Link
              key={item.href}
              href={item.href!}
              className={cn(
                "flex items-center space-x-3 px-4 py-3 rounded-lg transition-colors text-sm",
                isActive
                  ? "bg-blue-50 text-blue-700 font-medium"
                  : "text-gray-700 hover:bg-gray-50 hover:text-gray-900"
              )}
            >
              <Icon className="w-5 h-5" />
              <span>{t(item.label)}</span>
            </Link>
          );
        })}
      </nav>

      {/* Bottom Navigation */}
      <div className="border-t border-gray-200 p-3 space-y-1">
        {[
          { name: t("settings"), href: "/settings", icon: Settings },
          { name: t("logout"), href: "/logout", icon: LogOut }
        ].map((item) => {
          const isActive = pathname === item.href
          return (
            <Link
              key={item.name}
              href={item.href}
              className={cn(
                "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                isActive
                  ? "bg-blue-50 text-blue-700"
                  : "text-gray-700 hover:bg-gray-50 hover:text-gray-900",
              )}
            >
              <item.icon className="h-5 w-5" />
              {item.name}
            </Link>
          )
        })}
      </div>
    </aside>
  );
}
