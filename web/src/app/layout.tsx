import type {Metadata} from 'next';
import './globals.css';
export const metadata:Metadata={title:'NWallet · 公开样本',description:'NWallet 网页与电脑蓝牙桥接开发版'};
export default function Layout({children}:{children:React.ReactNode}){return <html lang="zh-CN"><body>{children}</body></html>;}
