import { useRef, useEffect, useState } from "react";
/**
 * 聊天区域滚动控制 Hook
 *
 * 职责说明：
 * 1. 管理聊天消息列表的自动滚动行为
 * 2. 检测用户主动上滑行为，显示/隐藏"回到底部"按钮
 * 3. 在消息增长时（流式输出、新消息）自动保持底部可见
 * 4. 提供程序化滚动和用户手动滚动的控制函数
 *
 * 设计解释：
 * - 使用三个 Observer 实现高效的滚动管理：
 *   1. scroll 事件监听：检测用户向上滚动行为
 *   2. IntersectionObserver：检测是否在底部位置
 *   3. ResizeObserver：监听内容增长自动滚动
 * - 使用 ref 存储内部状态，避免不必要的 re-render
 * - 所有 useEffect 依赖数组为空，仅挂载时执行一次，避免重复创建监听器
 */
export function useChatScroll() {
  /** 
   * 会话区滚动控制相关 ref 和状态
   * scrollRef: 滚动容器，用于监听/控制滚动条
   * contentRef: 消息内容容器，用于内容区域监听
   * bottomRef: 底部锚点，用于判断滚动是否到底
   */
  const scrollRef = useRef<HTMLDivElement | null>(null)         // 聊天容器的 ref
  const contentRef = useRef<HTMLDivElement | null>(null)        // 内容区域的 ref
  const bottomRef = useRef<HTMLDivElement | null>(null)         // 底部锚点 ref

  /** 
   * 是否显示“滚动到最底部”按钮
   * 仅用户上滑后才会显示
   */
  const [showScrollButton, setShowScrollButton] = useState(false)

  /**
   * 内部状态用于判断当前滚动行为
   * isAtBottomRef: 当前是否到底部
   * isUserScrollingRef: 用户是否正在主动滚动
   * isAutoScrollingRef: 自动滚动过程中的标记（避免重复触发）
   * lastScrollTopRef: 记录上次滚动位置，用于计算滚动方向
   * rafIdRef: requestAnimationFrame ID，用于取消动画帧
   */
  const isAtBottomRef = useRef(true)
  const isUserScrollingRef = useRef(false)
  const isAutoScrollingRef = useRef(false)
  const lastScrollTopRef = useRef(0)
  const rafIdRef = useRef<number | null>(null)


  /**
   * 滚动到聊天底部的函数
   * 
   * 职责说明：
   * 1. 使聊天区平滑或瞬时滚动到底部锚点元素（bottomRef）
   * 2. 设置 isAutoScrollingRef 标志为 true，用于区分自动滚动与用户滚动（避免重复触发滚动事件）
   * 3. 使用 requestAnimationFrame 在下一帧重置 isAutoScrollingRef，保证滚动状态同步
   * 
   * 设计解释：
   * - 保证自动滚动过程对滚动监听有区分能力，防止干扰用户主动滚动判断
   * - 调用时机一般在内容变化或需要程序控制滚动时
   * 
   * @param behavior 滚动行为控制（'auto' 或 'smooth'），默认自动
   */
  const scrollToBottom = (behavior: ScrollBehavior = "auto") => {
    // 如果底部锚点未渲染，直接返回
    if (!bottomRef.current) return;

    // 标记：开始自动滚动
    isAutoScrollingRef.current = true;

    // 执行滚动到底部锚点（block: end 保证锚点出现在可视区域底部）
    bottomRef.current.scrollIntoView({
      behavior,
      block: "end",
    });

    // 在下一个动画帧取消自动滚动状态
    requestAnimationFrame(() => {
      isAutoScrollingRef.current = false;
    });
  };

  /**
   * 监听用户滚动行为
   *
   * 职责说明：
   * 1. 监听滚动容器的 scroll 事件
   * 2. 计算滚动方向：对比当前 scrollTop 与上次记录的 scrollTop
   * 3. 当用户向上滚动时，标记 isUserScrollingRef 为 true 并显示"回到底部"按钮
   *
   * 设计解释：
   * - 向上滚动：scrollTop 减小（diff < 0）
   * - 直接更新 UI 状态：当用户上滑且不在底部时，立即显示"回到底部"按钮
   * - 更新 lastScrollTopRef：每次滚动后记录当前位置，为下次计算做准备
   */
  useEffect(() => {
    const container = scrollRef.current;
    if (!container) return;

    // 初始化上次滚动位置
    lastScrollTopRef.current = container.scrollTop;

    const handleScroll = () => {
      
      const currentScrollTop = container.scrollTop;
      const diff = currentScrollTop - lastScrollTopRef.current;

      // 向上滚动时 diff < 0，标记为用户主动滚动
      if (diff < 0) {
        isUserScrollingRef.current = true;
        // 用户上滑且不在底部时，显示"回到底部"按钮
        if (!isAtBottomRef.current) {
          setShowScrollButton(true);
        }
      }

      // 更新上次滚动位置
      lastScrollTopRef.current = currentScrollTop;
    };

    container.addEventListener("scroll", handleScroll, { passive: true });

    return () => {
      container.removeEventListener("scroll", handleScroll);
    };
  }, [scrollRef.current]);

  /**
   * 监听底部锚点是否可见（IntersectionObserver）
   *
   * 职责说明：
   * 1. 使用 IntersectionObserver 监听 bottomRef 是否进入视口
   * 2. 更新 isAtBottomRef 状态，标记当前是否在底部
   * 3. 当滚动到底部时，重置用户滚动标记并隐藏"回到底部"按钮
   *
   * 设计解释：
   * - 使用 IntersectionObserver 比滚动事件更高效，专门用于判断元素可见性
   * - threshold: 0 表示只要有任何部分可见就触发
   * - 到底部时重置 isUserScrollingRef，因为用户已经回到底部
   * - 直接调用 setShowScrollButton 更新 UI 状态，无需额外 useEffect 监听
   */
  useEffect(() => {
    const container = scrollRef.current;
    const bottom = bottomRef.current;

    if (!container || !bottom) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        const atBottom = entry.isIntersecting;
        isAtBottomRef.current = atBottom;
        
        if (atBottom) {
          // 到达底部时，重置用户滚动状态并隐藏按钮
          isUserScrollingRef.current = false;
          setShowScrollButton(false);
        }
      },
      {
        root: container,
        threshold: 0,
      }
    );

    observer.observe(bottom);

    return () => observer.disconnect();
  }, [scrollRef.current]); 

  /**
   * 监听内容区域尺寸变化（ResizeObserver）
   *
   * 职责说明：
   * 1. 监听 contentRef 内容区域的尺寸变化（如新消息追加、流式内容更新）
   * 2. 当内容增长且当前在底部时，自动滚动到底部保持可见
   * 3. 使用 requestAnimationFrame 节流，避免频繁滚动
   *
   * 设计解释：
   * - 仅在满足以下条件时自动滚动：
   *   1. 非用户主动上滑状态（isUserScrollingRef = false）
   *   2. 当前在底部（isAtBottomRef = true）
   *   3. 非自动滚动进行中（isAutoScrollingRef = false）
   * - 使用 RAF 节流防止高频触发
   * - 取消之前的 RAF 避免重复滚动
   */
  useEffect(() => {
    const content = contentRef.current;
    if (!content) return;

    const resizeObserver = new ResizeObserver(() => {
      
      // 用户主动上滑时，不自动滚动
      if (isUserScrollingRef.current) return;

      // 非底部，不自动滚动
      if (!isAtBottomRef.current && showScrollButton) return;

      // 取消之前的动画帧
      if (rafIdRef.current) {
        cancelAnimationFrame(rafIdRef.current);
      }
      // 使用 RAF 节流滚动操作
      rafIdRef.current = requestAnimationFrame(() => {
        scrollToBottom("auto");
      });
    });

    resizeObserver.observe(content);

    return () => {
      resizeObserver.disconnect();
      if (rafIdRef.current) cancelAnimationFrame(rafIdRef.current);
    };
  }, [contentRef.current]); 

  /**
   * 用户点击"回到底部"按钮的处理函数
   *
   * 职责说明：
   * 1. 执行平滑滚动到底部
   * 2. 隐藏"回到底部"按钮
   * 3. 重置用户滚动标记
   *
   * 设计解释：
   * - 使用 smooth 行为提供更好的用户体验
   * - 主动重置 isUserScrollingRef，表示用户已回到底部
   * - 直接隐藏按钮，无需等待 IntersectionObserver 触发
   */
  const handleScrollToBottom = () => {
    scrollToBottom("smooth");
    setShowScrollButton(false);
    isUserScrollingRef.current = false;
  };

  /**
   * 返回值说明：
   * - scrollRef: 滚动容器的 ref，需要绑定到可滚动的父容器
   * - contentRef: 内容区域的 ref，需要绑定到包含所有消息的容器
   * - bottomRef: 底部锚点的 ref，需要绑定到消息列表最底部的空元素
   * - showScrollButton: 是否显示"回到底部"按钮
   * - scrollToBottom: 程序化滚动到底部的函数
   * - handleScrollToBottom: 用户点击"回到底部"按钮的处理函数
   */
  return {
    scrollRef,
    contentRef,
    bottomRef,
    showScrollButton,
    scrollToBottom,
    handleScrollToBottom,
  };
}