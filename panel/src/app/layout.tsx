import type { Metadata, Viewport } from 'next';
import { Roboto } from 'next/font/google';
import Providers from './providers';
import ServiceWorkerRegistrar from '@/components/ServiceWorkerRegistrar';

const roboto = Roboto({
  weight: ['300', '400', '500', '700'],
  subsets: ['latin'],
  display: 'swap',
  variable: '--font-roboto',
});

export const metadata: Metadata = {
  title: 'TonbilTerm | Akilli Termostat',
  description: 'TonbilTerm akilli termostat kontrol paneli',
  manifest: '/manifest.json',
  icons: {
    icon: '/icons/icon-192.png',
    apple: '/icons/icon-192.png',
  },
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  themeColor: '#8B4513',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="tr" className={roboto.variable}>
      <body style={{ margin: 0, fontFamily: 'var(--font-roboto), Roboto, sans-serif' }}>
        <Providers>
          <ServiceWorkerRegistrar />
          {children}
        </Providers>
      </body>
    </html>
  );
}
