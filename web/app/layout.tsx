import { Cormorant_Garamond, Manrope } from "next/font/google";
import type { Metadata } from "next";
import "./globals.css";

const display = Cormorant_Garamond({
  subsets: ["latin"],
  weight: ["500", "600", "700"],
  variable: "--font-display",
});

const sans = Manrope({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-sans",
});

export const metadata: Metadata = {
  title: "EverRest",
  description:
    "I'm sorry for your loss. Tell us the circumstances. We work out when the service can be held.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className={`${display.variable} ${sans.variable} min-h-screen bg-ivory font-sans text-charcoal antialiased`}>
        {children}
      </body>
    </html>
  );
}
