/**
 * ImagePreview 组件
 *
 * 职责：
 * - 渲染 AI 返回的图片内容
 * - 纯展示组件，不包含任何状态管理或副作用
 *
 * 代码设计：
 * - 使用纯函数组件实现，接收 imageUrl 作为 props
 * - 使用原生 <img> 标签渲染图片
 * - 通过 max-width: 100% 确保图片响应式适配容器宽度
 *
 * 避免 re-render：
 * - 组件无内部状态，仅依赖 props.imageUrl
 * - 只有当父组件传入的 imageUrl 发生变化时才会重新渲染
 * - React 默认的浅比较机制确保 props 未变化时不会重新渲染
 */

interface ImagePreviewProps {
  imageUrl: string;
}

export function ImagePreview({ imageUrl }: ImagePreviewProps) {
  return (
    <img
      src={imageUrl}
      alt="AI generated image"
      style={{ maxWidth: '100%' }}
    />
  );
}
