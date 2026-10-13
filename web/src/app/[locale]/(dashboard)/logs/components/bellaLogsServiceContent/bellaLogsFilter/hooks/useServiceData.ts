import * as React from "react"

export function useServiceData() {
  const [serviceData, setServiceData] = React.useState<any[]>([])
  const [loading, setLoading] = React.useState(false)
  const [error, setError] = React.useState<string | null>(null)

  const fetchServiceData = React.useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const response = await fetch('/api/logs/trace/service', {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      })

      if (!response.ok) {
        const errorData = await response.json()
        throw new Error(errorData.error || '获取服务列表失败')
      }

      const data = await response.json()
      setServiceData(data)
    } catch (err) {
      console.error('Failed to fetch service data:', err)
      setError(err instanceof Error ? err.message : '获取服务列表失败')
      setServiceData([])
    } finally {
      setLoading(false)
    }
  }, [])

  return {
    serviceData,
    loading,
    error,
    fetchServiceData,
  }
}
