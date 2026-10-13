import { useRef, useCallback } from 'react';

export function useIME() {
  const isComposingRef = useRef(false);

  const handleCompositionStart = useCallback(() => {
    isComposingRef.current = true;
  }, []);

  const handleCompositionEnd = useCallback(() => {
    isComposingRef.current = false;
  }, []);

  const isComposing = useCallback(() => {
    return isComposingRef.current;
  }, []);

  return {
    isComposing,
    handleCompositionStart,
    handleCompositionEnd,
  };
}
