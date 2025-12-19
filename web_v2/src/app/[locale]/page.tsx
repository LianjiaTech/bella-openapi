'use client';

import { useRouter } from '@/i18n/routing';
import { useEffect } from 'react';

export default function HomePage() {
  const router = useRouter();
  
  useEffect(() => {
    router.replace('/overview');
  }, [router]);

  return null;
}
