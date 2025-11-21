import OCRPlayground from '@/components/ocr/OCRPlayground';

export default function BankCardOCRPage() {
    return (
        <OCRPlayground
            title="银行卡 OCR 识别"
            apiEndpoint="/v1/ocr/bankcard"
        />
    );
}
