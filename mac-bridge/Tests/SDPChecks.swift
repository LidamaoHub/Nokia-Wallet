import Foundation
struct LabError: Error { let message:String; init(_ message:String){self.message=message} }
@main struct SDPChecks {
 static func main() throws {
  let uuid=Data([0x4e,0x4b,0x57,0x31,0x43,0x35,0x40,0,0x80,0,0,1,0,0,0,1])
  func sequence(_ data:Data)->Data { if data.count<256{return Data([0x35,UInt8(data.count)])+data};return Data([0x36,UInt8(data.count>>8),UInt8(data.count&255)])+data }
  func record(_ id:Data,_ channel:UInt8)->Data {sequence(Data([9,0,1])+sequence(Data([0x1c])+id)+Data([9,0,4])+sequence(sequence(Data([0x19,1,0]))+sequence(Data([0x19,0,3,8,channel]))))}
  func response(_ data:Data,_ transaction:UInt16=1,_ continuation:Data=Data())->Data {let p=Data([UInt8(data.count>>8),UInt8(data.count&255)])+data+Data([UInt8(continuation.count)])+continuation;return Data([7,UInt8(transaction>>8),UInt8(transaction&255),UInt8(p.count>>8),UInt8(p.count&255)])+p}
  var checks=0
  func rejects(_ block:()throws->Void)throws {do{try block();fatalError("Expected rejection") }catch is LabError{checks+=1}}
  var q=SDPExchange(uuid:uuid);let request=try q.request();precondition(request.prefix(8)==Data([6,0,1,0,29,0x35,17,0x1c]));precondition(request.subdata(in:8..<24)==uuid);checks+=1
  let complete=sequence(record(uuid,7));let answer=try q.receive(response(complete));precondition(answer.channel==7&&answer.continuation==nil);checks+=1
  for n:UInt8 in [1,30] {var x=SDPExchange(uuid:uuid);_=try x.request();let received=try x.receive(response(sequence(record(uuid,n))));precondition(received.channel==n);checks+=1}
  for n:UInt8 in [0,31] {try rejects {var x=SDPExchange(uuid:uuid);_=try x.request();_=try x.receive(response(sequence(record(uuid,n))))}}
  try rejects {var x=SDPExchange(uuid:uuid);_=try x.request();_=try x.receive(response(sequence(record(Data(repeating:1,count:16),7))))}
  try rejects {var x=SDPExchange(uuid:uuid);_=try x.request();_=try x.receive(response(sequence(record(uuid,7)+record(uuid,8))))}
  try rejects {var x=SDPExchange(uuid:uuid);_=try x.request();_=try x.receive(response(sequence(Data())))}
  try rejects {var x=SDPExchange(uuid:uuid);_=try x.request();_=try x.receive(response(complete,2))}
  try rejects {var x=SDPExchange(uuid:uuid);_=try x.request();_=try x.receive(response(complete)+Data([0]))}
  try rejects {var x=SDPExchange(uuid:uuid);_=try x.request();_=try x.receive(Data([1,0,1,0,2,0,1]))}
  try rejects {var x=SDPExchange(uuid:uuid);_=try x.request();_=try x.receive(response(Data(repeating:0,count:1025)))}
  try rejects {var x=SDPExchange(uuid:uuid);_=try x.request();_=try x.receive(response(complete,1,Data(repeating:1,count:17)))}
  var continued=SDPExchange(uuid:uuid);_=try continued.request();let half=complete.count/2
  let first=try continued.receive(response(Data(complete.prefix(half)),1,Data([3,4])));precondition(first.continuation==Data([3,4]));let next=try continued.request(continuation:first.continuation!);precondition(next.suffix(3)==Data([2,3,4]));let end=try continued.receive(response(Data(complete.dropFirst(half)),2));precondition(end.channel==7);checks+=1
  try rejects {var x=SDPExchange(uuid:uuid);_=try x.request();_=try x.receive(response(Data([0x35]),1,Data([4])));_=try x.request(continuation:Data([4]));_=try x.receive(response(Data([0x35]),2,Data([4])))}
  try rejects {var x=SDPExchange(uuid:uuid);_=try x.request();_=try x.receive(response(Data([0x37,0xff,0xff,0xff,0xff])))}
  try rejects {var x=SDPExchange(uuid:uuid);_=try x.request();_=try x.receive(response(complete+Data([0])))}
  try rejects {var x=SDPExchange(uuid:Data([1]));_=try x.request()}
  print("PASS \(checks) SDP codec checks: exact UUID/class, RFCOMM bounds, framing, continuation, malformed/ambiguous rejection; no Bluetooth calls")
 }
}
