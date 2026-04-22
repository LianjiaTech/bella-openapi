import React from 'react'
import { formatBatchDiscount } from '@/lib/utils/price'

export const DisplayPriceCard = ({data, batchDiscount, unit}: {data: any, batchDiscount: number | undefined, unit: string | undefined}) => {
  return (
    <div>
      {data?.map((item: any) => (
        <div className="flex justify-between py-1" key={item.label}>
          <span className="text-muted-foreground">{item.label}:</span>
          <span className="font-medium">{item.value}</span>
        </div>
      ))}
      {formatBatchDiscount(batchDiscount) && (
        <div className="text-xs text-right pt-2">
          {formatBatchDiscount(batchDiscount)}
        </div>
      )}
      <div className="text-xs text-right pt-2">单位: {unit}</div>
    </div>
  )
}