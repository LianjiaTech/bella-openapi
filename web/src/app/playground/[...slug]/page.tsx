import { notFound } from 'next/navigation'

// 这个组件会处理所有未被其他路由匹配的 /playground/... 请求
export default function CatchAllPage({ params }: { params: { slug: string[] } }) {
    // 重定向到 not-found 页面
    notFound()
}
