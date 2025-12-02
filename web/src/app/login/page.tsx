"use client"

import { Suspense} from 'react'
import LoginContent from './loginContent/page'


export default function LoginPage() {
  return (
    <Suspense fallback={<div>Loading...</div>}>
      <LoginContent />
    </Suspense>
  )
}
