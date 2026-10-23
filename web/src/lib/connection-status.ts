export type ConnectionSnapshot={state?:string;sessionReady?:boolean;handshakePending?:boolean;error?:string;events?:string[];heartbeatSupported?:boolean};
export function connectionPhase(status:ConnectionSnapshot){
 if(status.state==='connected'&&status.sessionReady)return '已连接';
 const names:Record<string,string>={'unavailable':'无法获取连接状态','disconnected':'未连接','discovering-service-direct':'正在查找诺基亚','discovering-service':'正在查找诺基亚','refreshing-service':'正在更新诺基亚连接信息','connecting':'正在连接诺基亚','connected':'请在诺基亚上允许本次连接'};
 return names[status.state||'']||status.state||'尚未连接';
}
export function normalizeSnapshot(raw:unknown):ConnectionSnapshot{
 if(!raw||typeof raw!=='object'||Array.isArray(raw)||typeof (raw as ConnectionSnapshot).state!=='string')throw Error('状态响应无效');
 const value=raw as ConnectionSnapshot;return {...value,sessionReady:value.state==='connected'&&value.sessionReady===true};
}
export function connectionError(code:string){
 if(code.startsWith('SERVICE_REFRESH_'))return '未能更新诺基亚连接信息。请确认手机仍显示等待连接；本次连接已停止。';
 if(code==='CONNECTION_REJECTED')return '已在诺基亚上拒绝本次连接。可以重新连接，手机会再次请求允许。';
 if(code==='REMOTE_CLOSED')return '手机已关闭本次连接。若刚才选择拒绝且手机未显示等待连接，请在手机上重新开启蓝牙后再试。';
 if(code==='LOCAL_CLIENT_UNREACHABLE')return '客户端服务未响应，无法确认连接状态。请重新打开 NWallet 启动器。';
 if(code==='IDLE_TIMEOUT_5_MIN')return '5分钟没有业务交互，蓝牙会话已自动关闭；请重新开放并连接。';
 if(code==='PEER_UNRESPONSIVE')return '诺基亚暂时没有回应，连接已断开。请检查手机状态后重新连接。';
 if(['REMOTE_CLOSED','CHANNEL_CLOSED','HEARTBEAT_WRITE_FAILED'].includes(code))return '手机蓝牙会话已断开，请重新连接。';
 if(code==='WALLET_SERVICE_NOT_ADVERTISED')return '未找到 NWallet，请在诺基亚上解锁并开启蓝牙。';
 if(code==='SDP_DIRECT_TIMEOUT')return '未能获取诺基亚的连接信息，请检查手机上的 NWallet 是否正在等待连接。';
 if(code==='WALLET_CACHED_RFCOMM_DESCRIPTOR_INVALID')return '无法读取设备连接信息，请展开连接诊断查看详情。';
 if(code.startsWith('CONNECT_TIMEOUT_connecting'))return '未能建立蓝牙连接，请检查诺基亚是否保持前台并等待连接。';
 if(code==='SESSION_TIMEOUT_hello')return '蓝牙已接通，但未收到 NWallet 确认。请在诺基亚上允许本次连接，并保持应用在前台。';
 if(code.startsWith('SESSION_TIMEOUT_'))return '诺基亚未及时回应，请检查手机上的 NWallet。';
 if(code==='TIMEOUT'||code.startsWith('CONNECT_TIMEOUT_'))return '连接超时，请检查手机状态。详细信息在连接诊断中。';
 if(code.startsWith('SDP_L2CAP_'))return '无法获取诺基亚的蓝牙服务，请检查手机状态和访问权限。';
 return /^[A-Z0-9_]+$/.test(code)?'连接未完成，请展开连接诊断查看详情。':code;
}
