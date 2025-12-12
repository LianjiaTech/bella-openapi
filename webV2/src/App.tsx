import { BrowserRouter, Routes, Route } from 'react-router-dom'

function HomePage() {
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="text-center">
        <h1 className="text-4xl font-bold text-gray-900 mb-4">
          Bella OpenAPI v2
        </h1>
        <p className="text-lg text-gray-600">
          欢迎使用 Bella OpenAPI 管理平台
        </p>
        <div className="mt-8 text-sm text-gray-500">
          React + Vite + TypeScript + TailwindCSS
        </div>
      </div>
    </div>
  )
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
