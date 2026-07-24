import Script from "next/script";

type RiskControlInitOptions = Record<string, unknown>;

const RISK_CONTROL_ENABLED = process.env.NEXT_PUBLIC_RISK_CONTROL_ENABLED === "true";
const RISK_CONTROL_SCRIPT_URL = process.env.NEXT_PUBLIC_RISK_CONTROL_SCRIPT_URL?.trim();
const RISK_CONTROL_ENV = process.env.NEXT_PUBLIC_RISK_CONTROL_ENV?.trim();
const RISK_CONTROL_INIT_OPTIONS = process.env.NEXT_PUBLIC_RISK_CONTROL_INIT_OPTIONS?.trim();

function parseInitOptions(value?: string): RiskControlInitOptions {
    if (!value) return {};

    try {
        const parsed: unknown = JSON.parse(value);
        if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
            return parsed as RiskControlInitOptions;
        }
    } catch {
        return {};
    }

    return {};
}

function buildInitOptions(): RiskControlInitOptions {
    return {
        ...parseInitOptions(RISK_CONTROL_INIT_OPTIONS),
        ...(RISK_CONTROL_ENV ? { env: RISK_CONTROL_ENV } : {}),
    };
}

function serializeForInlineScript(value: RiskControlInitOptions): string {
    return JSON.stringify(value).replace(/</g, "\\u003c");
}

function buildInitScript(): string {
    const initOptions = serializeForInlineScript(buildInitOptions());

    return `try {
  if (window.RiskControlWithSign) {
    var riskControl = new window.RiskControlWithSign();
    riskControl.init(${initOptions});
  }
} catch (error) {}`;
}

export function RiskControlScripts() {
    if (!RISK_CONTROL_ENABLED || !RISK_CONTROL_SCRIPT_URL) {
        return null;
    }

    return (
        <>
            <Script
                id="risk-control-sdk"
                src={RISK_CONTROL_SCRIPT_URL}
                strategy="beforeInteractive"
                crossOrigin="anonymous"
            />
            <Script
                id="risk-control-init"
                strategy="beforeInteractive"
                dangerouslySetInnerHTML={{ __html: buildInitScript() }}
            />
        </>
    );
}
