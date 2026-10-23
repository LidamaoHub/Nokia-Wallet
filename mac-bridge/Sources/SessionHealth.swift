import Foundation
// Radio probes test liveness; they never extend the five-minute business idle limit.
struct SessionHealth {
 enum Action:Equatable {case none,probe,disconnect(String)}
 var lastBusiness:TimeInterval,lastProbe:TimeInterval
 var pendingID:UInt32?,pendingSince:TimeInterval?
 init(now:TimeInterval){lastBusiness=now;lastProbe=now}
 mutating func sent(_ id:UInt32,now:TimeInterval){pendingID=id;pendingSince=now;lastProbe=now}
 mutating func pong(_ id:UInt32)->Bool{guard pendingID==id else{return false};pendingID=nil;pendingSince=nil;return true}
 func action(now:TimeInterval,open:Bool,ready:Bool,supported:Bool)->Action {
  if !open{return .disconnect("CHANNEL_CLOSED")}
  if now-lastBusiness>=300{return .disconnect("IDLE_TIMEOUT_5_MIN")}
  guard ready&&supported else{return .none}
  if let sent=pendingSince,now-sent>=20{return .disconnect("PEER_UNRESPONSIVE")}
  if pendingID==nil&&now-lastProbe>=15{return .probe}
  return .none
 }
}
