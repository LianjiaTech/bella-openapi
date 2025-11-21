import OCRPlayground from '@/components/ocr/OCRPlayground';

export default function TmpIdCardOCRPage() {
    return (
        <OCRPlayground
            title="临时身份证 OCR 识别"
            apiEndpoint="/v1/ocr/tmp-idcard"
        />
    );
}
