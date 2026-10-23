import Foundation
@main struct HealthChecks {
 static func main(){
  var s=SessionHealth(now:0);var checks=0
  precondition(s.action(now:14,open:true,ready:true,supported:true)==SessionHealth.Action.none);checks+=1
  precondition(s.action(now:15,open:true,ready:true,supported:true)==SessionHealth.Action.probe);checks+=1
  s.sent(99,now:15);precondition(s.lastBusiness==0);checks+=1
  precondition(!s.pong(98)&&s.pendingID==99);checks+=1
  precondition(s.action(now:34,open:true,ready:true,supported:true)==SessionHealth.Action.none);checks+=1
  precondition(s.action(now:35,open:true,ready:true,supported:true)==SessionHealth.Action.disconnect("PEER_UNRESPONSIVE"));checks+=1
  precondition(s.pong(99)&&s.lastBusiness==0&&s.pendingID==nil);checks+=1
  for t in stride(from:30,through:285,by:15){s.sent(UInt32(t),now:Double(t));precondition(s.pong(UInt32(t)))}
  precondition(s.action(now:300,open:true,ready:true,supported:true)==SessionHealth.Action.disconnect("IDLE_TIMEOUT_5_MIN"));checks+=1
  precondition(s.action(now:10,open:false,ready:true,supported:true)==SessionHealth.Action.disconnect("CHANNEL_CLOSED"));checks+=1
  s=SessionHealth(now:0);precondition(s.action(now:20,open:true,ready:true,supported:false)==SessionHealth.Action.none);checks+=1
  s.lastBusiness=250;precondition(s.action(now:301,open:true,ready:true,supported:false)==SessionHealth.Action.none);checks+=1
  precondition(s.action(now:550,open:true,ready:true,supported:false)==SessionHealth.Action.disconnect("IDLE_TIMEOUT_5_MIN"));checks+=1
  print("PASS \(checks) deterministic health checks: nonce, deadlines, idle independent of pings, closed channel, legacy capability; no radio")
 }
}
