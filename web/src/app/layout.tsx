'use client';
import localFont from "next/font/local"
import "./globals.css"
import {ThemeProvider} from "next-themes"
import {Toaster} from "@/components/ui/toaster"
import {UserProvider} from "@/lib/context/user-context";

const geistSans = localFont({
    src: "./fonts/GeistVF.woff",
    variable: "--font-geist-sans",
    weight: "100 900",
})

const geistMono = localFont({
    src: "./fonts/GeistMonoVF.woff",
    variable: "--font-geist-mono",
    weight: "100 900",
})

export default function RootLayout({
                                       children,
                                   }: Readonly<{
    children: React.ReactNode
}>) {
    return (
        <html lang="en" suppressHydrationWarning>
        <body className={`${geistSans.variable} ${geistMono.variable} font-sans antialiased bg-white`}>
        <UserProvider>
            <ThemeProvider
                attribute="class"
                defaultTheme="light"
                enableSystem={false}
                disableTransitionOnChange
            >
                <main className="min-h-screen bg-white text-gray-900">
                    <Toaster />
                    {children}
                </main>
            </ThemeProvider>
        </UserProvider>
        </body>
        </html>
    )
}
