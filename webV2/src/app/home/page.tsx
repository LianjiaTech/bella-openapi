"use client"
import { TopBar } from "@/components/top-bar"
import { HeroSection } from "@/app/home/components/hero-section"
import { QuickActions } from "@/app/home/components/quick-actions"
import { WhatsNewSection } from "@/app/home/components/whats-new-section"
import { CodeExample } from "@/app/home/components/code-example"

const HomePage = () => {

  return (
    <div className="flex h-screen flex-col overflow-hidden">
      <TopBar />

      <div className="flex-1 overflow-y-auto">
        <div className="container px-4 py-8">
          {/* Hero Section */}
          <HeroSection />

          {/* Quick Actions */}
          <QuickActions />

          {/* What's New */}
          <WhatsNewSection />

          {/* Code Example */}
          <CodeExample />
        </div>
      </div>
    </div>
  )
}

export { HomePage }
export default HomePage
