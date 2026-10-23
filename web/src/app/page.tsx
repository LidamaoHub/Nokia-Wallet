import WalletGate from '@/components/wallet-gate';
import {enabled} from '@/lib/policy';
export const dynamic='force-dynamic';
export default function Page(){return <WalletGate local={enabled(process.env,process.platform)}/>;}
