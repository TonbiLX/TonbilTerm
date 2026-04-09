/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  eslint: {
    ignoreDuringBuilds: true,
  },
  typescript: {
    ignoreBuildErrors: true,
  },
  // Nginx SPA fallback kullandigimiz icin trailing slash gerekmez
  trailingSlash: false,
  // Statik export'ta image optimization kapatilmali
  images: {
    unoptimized: true,
  },
};

module.exports = nextConfig;
