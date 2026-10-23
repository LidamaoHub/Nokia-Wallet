import Foundation
import IOBluetooth

// Cache-first remains the fast path. Only a failed cached open reaches this query.
// No guessed channel, pairing removal, manual override, or competing direct SDP query.
struct WalletRefreshRecord {
 let channel:UInt8?
 let updated:Date?
 static func eligible(previous:UInt8,before:Date?,current:WalletRefreshRecord)->UInt8? {
  guard let channel=current.channel,(1...30).contains(channel) else{return nil}
  let newer=current.updated.map{after in before.map{after>$0} ?? true} ?? false
  return channel != previous || newer ? channel : nil
 }
}
final class WalletServiceRefresh {
 private let previous:UInt8,before:Date?
 private let query:()->IOReturn,read:()->WalletRefreshRecord
 private var poll:Timer?,deadline:Timer?,active=true
 var completion:((Result<UInt8,LabError>)->Void)?
 init(previous:UInt8,before:Date?,query:@escaping ()->IOReturn,read:@escaping ()->WalletRefreshRecord){self.previous=previous;self.before=before;self.query=query;self.read=read}
 convenience init(device:IOBluetoothDevice,previous:UInt8){
  let uuid=serviceBytes.withUnsafeBytes{IOBluetoothSDPUUID(bytes:$0.baseAddress,length:16)}
  self.init(previous:previous,before:device.getLastServicesUpdate() as Date?,query:{device.performSDPQuery(nil,uuids:[uuid])},read:{
   var value:BluetoothRFCOMMChannelID=0
   let record=device.getServiceRecord(for:uuid)
   let valid=record?.getRFCOMMChannelID(&value)==kIOReturnSuccess && (1...30).contains(value)
   return WalletRefreshRecord(channel:valid ? value:nil,updated:device.getLastServicesUpdate() as Date?)
  })
 }
 func start(){
  // Apple's API permits a nil callback target. Poll the cache update timestamp as
  // well as the exact UUID record: old macOS/C5 callbacks have been unreliable.
  let result=query()
  guard result==kIOReturnSuccess else{finish(.failure(LabError("SERVICE_REFRESH_START_\(result)")));return}
  poll=Timer.scheduledTimer(withTimeInterval:0.25,repeats:true){[weak self] _ in self?.observe()}
  deadline=Timer.scheduledTimer(withTimeInterval:20,repeats:false){[weak self] _ in self?.expire()}
  observe()
 }
 func observe(){
  guard active else{return}
  let snapshot=read();let updated=snapshot.updated
  if let channel=WalletRefreshRecord.eligible(previous:previous,before:before,current:snapshot){finish(.success(channel));return}
  let newer=updated.map{after in before.map{after>$0} ?? true} ?? false
  if newer && snapshot.channel==nil{finish(.failure(LabError("WALLET_SERVICE_NOT_ADVERTISED")))}
 }
 func expire(){finish(.failure(LabError("SERVICE_REFRESH_TIMEOUT")))}
 func cancel(){active=false;completion=nil;poll?.invalidate();poll=nil;deadline?.invalidate();deadline=nil}
 private func finish(_ result:Result<UInt8,LabError>){guard active else{return};let callback=completion;cancel();callback?(result)}
}
