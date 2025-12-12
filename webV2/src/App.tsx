import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ThemeProvider } from '@/components/theme-provider'
import { LanguageProvider } from '@/components/language-provider'
import DashboardLayout from '@/app/(dashboard)/layout'
import ModelsPage from '@/app/(dashboard)/models/page'

function App() {
  return (
    <ThemeProvider>
      <LanguageProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<Navigate to="/models" replace />} />
            <Route path="/*" element={
              <DashboardLayout>
                <Routes>
                  <Route path="/models" element={<ModelsPage />} />
                </Routes>
              </DashboardLayout>
            } />
          </Routes>
        </BrowserRouter>
      </LanguageProvider>
    </ThemeProvider>
  )
}

export default App

