"use client"

import { TopBar } from "@/components/layout/top-bar"

import { HeroSection } from "./components/hero-section"
import { QuickActions } from "./components/quick-actions"
import { WhatsNew } from "./components/whats-new"
import { CodeExample } from "./components/code-example"

export default function OverviewPage() {
  return (
    <div className="flex h-screen flex-col overflow-hidden">
      <TopBar />

      <div className="flex-1 overflow-y-auto">
        <div className="container px-4 py-8">
          <HeroSection />
          <QuickActions />
          <WhatsNew />
          <CodeExample />
        </div>
      </div>
    </div>
  )
}
