import { Button } from "@/components/common/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/common/select";
import { Input } from "@/components/common/input";
import { useState, useEffect } from "react";

export interface PaginationProps {
  // === 现有属性(保持不变) ===
  currentPage: number;
  hasMore: boolean;
  onPrevPage: () => void;
  onNextPage: () => void;
  loading?: boolean;
  totalItems?: number;

  // === 新增属性(可选,用于前端分页模式) ===
  mode?: 'backend' | 'frontend';  // 分页模式,默认'backend'
  totalPages?: number;            // 总页数(前端分页模式需要)
  pageSize?: number;              // 每页条数
  pageSizeOptions?: number[];     // 每页条数选项
  onPageSizeChange?: (size: number) => void;  // 每页条数变更回调
  onPageChange?: (page: number) => void;      // 直接跳转页码回调
}

export function Pagination({
  currentPage,
  hasMore,
  onPrevPage,
  onNextPage,
  loading = false,
  totalItems,
  mode = 'backend',
  totalPages,
  pageSize = 10,
  pageSizeOptions = [10, 20, 50, 100],
  onPageSizeChange,
  onPageChange,
}: PaginationProps) {
  const [pageInput, setPageInput] = useState(currentPage.toString());

  // 同步 currentPage 到 pageInput
  useEffect(() => {
    setPageInput(currentPage.toString());
  }, [currentPage]);

  // 前端分页模式
  if (mode === 'frontend') {
    const handlePageJump = () => {
      const page = parseInt(pageInput);
      if (!isNaN(page) && page >= 1 && totalPages && page <= totalPages && onPageChange) {
        onPageChange(page);
      } else {
        setPageInput(currentPage.toString());
      }
    };

    return (
      <div className="flex items-center justify-between mt-4 px-2">
        <div className="text-sm text-muted-foreground">
          总共 {totalItems || 0} 条记录，第 {currentPage} / {totalPages || 1} 页
        </div>
        <div className="flex items-center gap-3">
          {/* 每页条数选择器 */}
          <div className="flex items-center gap-2">
            <Select
              value={pageSize.toString()}
              onValueChange={(value) => onPageSizeChange?.(parseInt(value))}
            >
              <SelectTrigger className="h-8 w-[100px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {pageSizeOptions.map((size) => (
                  <SelectItem key={size} value={size.toString()}>
                    {size}条/页
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* 上一页按钮 */}
          <Button
            variant="outline"
            size="sm"
            onClick={onPrevPage}
            disabled={currentPage === 1 || loading}
          >
            上一页
          </Button>

          {/* 页码输入框 */}
          <div className="flex items-center gap-2">
            <Input
              type="text"
              value={pageInput}
              onChange={(e) => setPageInput(e.target.value)}
              onBlur={handlePageJump}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  handlePageJump();
                }
              }}
              className="h-8 w-16 text-center"
            />
          </div>

          {/* 下一页按钮 */}
          <Button
            variant="outline"
            size="sm"
            onClick={onNextPage}
            disabled={currentPage >= (totalPages || 1) || loading}
          >
            下一页
          </Button>
        </div>
      </div>
    );
  }

  // 后端分页模式(原有逻辑保持不变)
  return (
    <div className="flex items-center justify-between mt-4">
      <div className="text-sm text-muted-foreground">
        第 {currentPage} 页
        {totalItems !== undefined && ` · 共 ${totalItems} 条`}
      </div>
      <div className="flex gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={onPrevPage}
          disabled={currentPage === 1 || loading}
        >
          上一页
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={onNextPage}
          disabled={!hasMore || loading}
        >
          下一页
        </Button>
      </div>
    </div>
  );
}
