export async function copyToClipboard(text: string): Promise<boolean> {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      // fallthrough to legacy method
    }
  }

  try {
    return fallbackCopyToClipboard(text);
  } catch {
    return false;
  }
}

function fallbackCopyToClipboard(text: string): boolean {
  const textArea = document.createElement('textarea');
  textArea.value = text;
  textArea.style.position = 'fixed';
  textArea.style.top = '-9999px';
  textArea.style.left = '-9999px';
  textArea.style.opacity = '0';
  textArea.setAttribute('readonly', '');

  document.body.appendChild(textArea);

  try {
    textArea.focus();
    textArea.select();

    if (navigator.userAgent.match(/ipad|iphone/i)) {
      const range = document.createRange();
      range.selectNodeContents(textArea);
      const selection = window.getSelection();
      if (selection) {
        selection.removeAllRanges();
        selection.addRange(range);
      }
      textArea.setSelectionRange(0, text.length);
    }

    const successful = document.execCommand('copy');
    return successful;
  } catch {
    return false;
  } finally {
    document.body.removeChild(textArea);
  }
}

export function isClipboardSupported(): boolean {
  return !!(navigator.clipboard && window.isSecureContext);
}

export function downloadAsFile(content: string, filename: string, mimeType: string = 'application/json'): void {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
