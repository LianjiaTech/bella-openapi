import OCRPlayground from '@/components/ocr/OCRPlayground';

export default function IdCardOCRPage() {
    return (
        <OCRPlayground
            title="身份证 OCR 识别"
            apiEndpoint="/v1/ocr/idcard"
        />
    );
}
