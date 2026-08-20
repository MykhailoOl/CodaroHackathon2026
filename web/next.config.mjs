/** @type {import('next').NextConfig} */

// The API authenticates with the ordinary Spring form login and a session cookie, and
// the backend declares no CORS. Proxying it under this origin is what makes that
// workable from the browser: same-origin means the JSESSIONID cookie and the CSRF
// token travel on every call with no cross-origin negotiation at all.
const API_ORIGIN = process.env.API_ORIGIN || "http://localhost:8080";

const nextConfig = {
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${API_ORIGIN}/api/:path*` },
      { source: "/login", destination: `${API_ORIGIN}/login` },
      { source: "/logout", destination: `${API_ORIGIN}/logout` },
    ];
  },
};

export default nextConfig;
