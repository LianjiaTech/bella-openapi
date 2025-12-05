import OCRPlayground from '@/components/ocr/OCRPlayground';

export default function GeneralOCRPage() {
    return (
        <OCRPlayground
            title="通用 OCR 识别"
            apiEndpoint="/v1/ocr/general"
        />
    );
}
