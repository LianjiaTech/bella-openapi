import OCRPlayground from '@/components/ocr/OCRPlayground';

export default function HMTResidencePermitOCRPage() {
    return (
        <OCRPlayground
            title="港澳台居民居住证 OCR 识别"
            apiEndpoint="/v1/ocr/hmt-residence-permit"
        />
    );
}
