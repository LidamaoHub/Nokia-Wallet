import {test} from 'node:test';import assert from 'node:assert/strict';
import {connectionPhase,connectionError,normalizeSnapshot} from './connection-status';
test('connect phase is independent of channel/handshake and errors explain stage',()=>{
 assert.match(connectionPhase({state:'discovering-service-direct'}),/查找诺基亚/);
 assert.match(connectionPhase({state:'connected'}),/在诺基亚上允许/);
 assert.equal(connectionPhase({state:'connected',sessionReady:true}),'已连接');
 assert.match(connectionError('SDP_DIRECT_TIMEOUT'),/连接信息/);
 assert.match(connectionError('SESSION_TIMEOUT_hello'),/蓝牙已接通/);
 assert.match(connectionError('WALLET_SERVICE_NOT_ADVERTISED'),/NWallet/);
 assert.match(connectionError('OTHER'),/连接未完成/);
 assert.doesNotMatch(connectionError('SESSION_TIMEOUT_hello'),/0\.1\.|旧版|握手/);
});

test('stale ready states are rejected and lost local service is explicit',()=>{
 assert.equal(normalizeSnapshot({state:'disconnected',sessionReady:true}).sessionReady,false);
 assert.notEqual(connectionPhase({state:'unavailable',sessionReady:true}),'已连接');
 assert.notEqual(connectionPhase({state:'disconnected',sessionReady:true}),'已连接');
 assert.throws(()=>normalizeSnapshot(null));assert.throws(()=>normalizeSnapshot({}));
 assert.match(connectionError('LOCAL_CLIENT_UNREACHABLE'),/无法确认连接状态/);
 assert.match(connectionError('IDLE_TIMEOUT_5_MIN'),/自动关闭/);
 assert.match(connectionError('PEER_UNRESPONSIVE'),/没有回应/);
});

test('rejection and old peer close explain the next step',()=>{
 assert.match(connectionError('CONNECTION_REJECTED'),/拒绝本次连接.*重新连接/);
 assert.match(connectionError('REMOTE_CLOSED'),/重新开启蓝牙/);
});

test('failed cached channel refresh is a distinct phase and failure',()=>{
 assert.match(connectionPhase({state:'refreshing-service'}),/更新诺基亚连接信息/);
 assert.match(connectionError('SERVICE_REFRESH_TIMEOUT'),/未能更新.*本次连接已停止/);
});
