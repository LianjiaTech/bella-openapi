import { TopBar } from "@/components/layout";
import { CodeExample } from "./components/code-example";
import { HeroSection } from "./components/hero-section";
import { QuickActions } from "./components/quick-actions";
import { WhatsNewSection } from "./components/whats-new-section";

export default function OverviewPage() {
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
  );
}
