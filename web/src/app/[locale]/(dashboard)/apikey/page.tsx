import { redirect } from "@/i18n/routing";

export default async function LegacyApiKeysPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  redirect({ href: "/manager", locale });
}
