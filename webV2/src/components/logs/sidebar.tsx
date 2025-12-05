'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'

export function LogsSidebar() {
  const pathname = usePathname()

  return (
    <div className="w-64 bg-white dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 min-h-screen p-4">
      <div className="space-y-4">
        <div className="text-sm font-medium text-gray-700 dark:text-gray-300 uppercase">日志查询</div>
        <nav className="space-y-2">
          <Link
            href="/logs"
            className={`flex items-center px-3 py-2 text-sm rounded-md ${
              pathname === '/logs'
                ? 'text-white bg-gray-600 dark:bg-gray-700'
                : 'text-gray-700 dark:text-gray-200 hover:text-gray-900 dark:hover:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-700'
            }`}
          >
            OpenAPI 服务查询
          </Link>
          <Link
            href="/logs/trace"
            className={`flex items-center px-3 py-2 text-sm rounded-md ${
              pathname === '/logs/trace'
                ? 'text-white bg-gray-600 dark:bg-gray-700'
                : 'text-gray-700 dark:text-gray-200 hover:text-gray-900 dark:hover:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-700'
            }`}
          >
            Bella 链路查询
          </Link>
        </nav>
      </div>
    </div>
  )
}
