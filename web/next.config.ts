import type { NextConfig } from "next";
const config: NextConfig = { poweredByHeader: false, distDir:process.env.NWALLET_BUILD_DIR||'.next' };
export default config;
