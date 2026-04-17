import { shouldConvertTokenPrice, convertTokenPriceLabel, convertTokenPrice } from './price'

/**
 * 解析模型 features 字段（兼容 JSON 对象格式和逗号分隔字符串）
 * - JSON 对象：取 value === true 的 key 作为 feature 名
 * - JSON 数组：直接用数组元素
 * - 纯字符串：逗号分隔
 */
export function parseFeatures(raw: string | undefined | null): Set<string> {
  if (!raw) return new Set();
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) return new Set(parsed.map(String).filter(Boolean));
    if (typeof parsed === "object" && parsed !== null) {
      return new Set(
        Object.entries(parsed)
          .filter(([, v]) => v === true || v === 1)
          .map(([k]) => k)
      );
    }
  } catch {
    // fallback
  }
  return new Set(raw.split(",").map((f) => f.trim()).filter(Boolean));
}

/**
 * 解析 priceDetails.displayPrice 为可渲染的行数组
 * value 中的 \n 会被拆分为多行
 */
export function parsePriceRows(
  displayPrice: Record<string, string> | undefined,
  unit?: string
): Array<{ label: string; lines: string[] }> {
  if (!displayPrice) return [];
  return Object.entries(displayPrice).map(([label, value]) => {
    const shouldConvert = shouldConvertTokenPrice(unit, label);
    const convertedLabel = shouldConvert ? convertTokenPriceLabel(label) : label;
    const lines = value.split("\n").map((l) => {
      const trimmed = l.trim();
      if (!trimmed) return "";
      if (shouldConvert) {
        const num = Number(trimmed);
        if (!isNaN(num)) {
          return `${convertTokenPrice(num).toFixed(2)}`;
        }
        return convertTokenPriceLabel(trimmed);
      }
      return trimmed;
    }).filter(Boolean);
    return { label: convertedLabel, lines };
  });
}

/**
 * 下载图片
 * - url 格式：直接用 fetch 获取 blob 再触发下载（绕过跨域限制）
 * - base64 格式：直接触发下载
 */
export async function downloadImage(
  src: string,
  filename: string = "image.png"
): Promise<void> {
  if (src.startsWith("data:")) {
    // base64 直接下载
    const a = document.createElement("a");
    a.href = src;
    a.download = filename;
    a.click();
    return;
  }

  // url 格式：通过服务端代理获取 base64，避免跨域限制
  const res = await fetch("/api/fetch-image", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ url: src }),
  });
  const data = await res.json();
  if (!data.success) throw new Error(data.error || "下载失败");
  const a = document.createElement("a");
  a.href = data.imageData;
  a.download = filename;
  a.click();
}
