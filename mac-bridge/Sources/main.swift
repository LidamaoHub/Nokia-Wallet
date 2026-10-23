import Foundation
import IOBluetooth
import Security
import CryptoKit

let serviceBytes: [UInt8] = [0x4e,0x4b,0x57,0x31,0x43,0x35,0x40,0x00,0x80,0x00,0x00,0x01,0x00,0x00,0x00,0x01]
func crc(_ data: Data) -> UInt32 { var c: UInt32 = 0xffffffff; for b in data { c ^= UInt32(b); for _ in 0..<8 { c = (c >> 1) ^ ((c & 1) != 0 ? 0xedb88320 : 0) } }; return c ^ 0xffffffff }
func word(_ v:UInt32)->Data { Data([UInt8(v>>24),UInt8((v>>16)&255),UInt8((v>>8)&255),UInt8(v&255)]) }
func number(_ d:Data,_ offset:Int)->UInt32 { d[offset..<offset+4].reduce(0){($0<<8)|UInt32($1)} }
func packet(_ data:Data)->Data { Data("NKB1".utf8)+word(UInt32(data.count))+word(crc(data))+data }
func decodePacket(_ buffer:Data)throws->Data? {
 guard buffer.count>=12 else{return nil}
 let size=Int(number(buffer,4))
 guard buffer.prefix(4)==Data("NKB1".utf8),size>0,size<=65552 else{throw LabError("INVALID_FRAME")}
 guard buffer.count>=12+size else{return nil}
 guard buffer.count==12+size else{throw LabError("EXTRA_FRAME_DATA")}
 let body=buffer.subdata(in:12..<12+size)
 guard crc(body)==number(buffer,8) else{throw LabError("CRC_FAILED")}
 return body
}
func respond(_ id:String,_ value:Any? = nil,_ error:String? = nil) {
 let object:[String:Any] = error == nil ? ["id":id,"ok":true,"result":value ?? [:]] : ["id":id,"ok":false,"error":error!]
 if let d=try? JSONSerialization.data(withJSONObject:object,options:[.sortedKeys]) { FileHandle.standardOutput.write(d+Data([10])) }
}
struct LabError:Error { let message:String; init(_ value:String){message=value} }

// No wallet secrets are accepted. The encryption key stays inside this native process/Keychain.
final class Vault {
 let service="tech.lidamao.nokia.walletdev.addressbook.v1"
 let testURL:URL?,testKey:SymmetricKey?
 init(testURL:URL?=nil,testKey:SymmetricKey?=nil){self.testURL=testURL;self.testKey=testKey}
 var url:URL { if let testURL=testURL{return testURL};return FileManager.default.urls(for:.applicationSupportDirectory,in:.userDomainMask)[0].appendingPathComponent("NokiaWalletDev/addressbook-v1.enc") }
 func key(create:Bool)throws->SymmetricKey {
  if let testKey=testKey{return testKey}
  let query:[String:Any]=[kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:service,kSecAttrAccount as String:"addressbook",kSecReturnData as String:true,kSecMatchLimit as String:kSecMatchLimitOne]
  var result:CFTypeRef?;let status=SecItemCopyMatching(query as CFDictionary,&result)
  if status==errSecSuccess,let d=result as? Data,d.count==32 {return SymmetricKey(data:d)}
  guard status==errSecItemNotFound && create else {throw LabError("KEYCHAIN_UNAVAILABLE_\(status)")}
  var bytes=[UInt8](repeating:0,count:32);guard SecRandomCopyBytes(kSecRandomDefault,bytes.count,&bytes)==errSecSuccess else {throw LabError("RNG_FAILED")}
  let d=Data(bytes)
  let item:[String:Any]=[kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:service,kSecAttrAccount as String:"addressbook",kSecValueData as String:d,kSecAttrAccessible as String:kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
  let added=SecItemAdd(item as CFDictionary,nil);guard added==errSecSuccess else {throw LabError("KEYCHAIN_SAVE_FAILED_\(added)")}
  return SymmetricKey(data:d)
 }
 func load()throws->Any {
  guard FileManager.default.fileExists(atPath:url.path) else {return ["version":1,"records":[]] as [String:Any]}
  let encrypted=try Data(contentsOf:url);guard encrypted.count<=1048576 else {throw LabError("VAULT_LIMIT")}
  let raw=try AES.GCM.open(AES.GCM.SealedBox(combined:encrypted),using:key(create:false),authenticating:Data(service.utf8))
  return try JSONSerialization.jsonObject(with:raw)
 }
 func save(_ records:Any)throws {
  guard let list=records as? [[String:Any]],list.count<=100 else {throw LabError("RECORD_LIMIT")}
  let allowed:Set<String>=["id","chain","address","label","path","verified"]
  for row in list {
   guard Set(row.keys).isSubset(of:allowed),let label=row["label"] as? String,label.count<=80,let address=row["address"] as? String,address.count<=128 else {throw LabError("PUBLIC_METADATA_ONLY")}
  }
  let raw=try JSONSerialization.data(withJSONObject:["version":1,"records":list],options:[.sortedKeys]);guard raw.count<=65536 else {throw LabError("VAULT_LIMIT")}
  // Never create a replacement key when an existing vault cannot be unlocked.
  if FileManager.default.fileExists(atPath:url.path) {_=try load()}
  let k=try key(create:!FileManager.default.fileExists(atPath:url.path))
  let encrypted=try AES.GCM.seal(raw,using:k,authenticating:Data(service.utf8)).combined!
  try FileManager.default.createDirectory(at:url.deletingLastPathComponent(),withIntermediateDirectories:true,attributes:[.posixPermissions:0o700])
  try encrypted.write(to:url,options:.atomic);try FileManager.default.setAttributes([.posixPermissions:0o600],ofItemAtPath:url.path)
 }
}

final class Bridge:NSObject,IOBluetoothRFCOMMChannelDelegate {
 var device:IOBluetoothDevice?,channel:IOBluetoothRFCOMMChannel?,connectID:String?,testID:String?
 var buffer=Data(),expected=Data(),writing:NSMutableData?,writeOffset=0,writeCount=0
 var retiredWrites:[NSMutableData]=[],writeQueue:[Data]=[]
 var offlineTransport:((Data)throws->Void)?
 var linked:Bool{channel?.isOpen()==true || offlineTransport != nil}
 var walletSupported=false,walletUUID="",walletRoot="",walletExpected="-";var walletProgress:[String:Any]=[:]
 var heartbeatSupported=false
 var health=SessionHealth(now:Date().timeIntervalSince1970)
 var lastActivity:Date {get{Date(timeIntervalSince1970:health.lastBusiness)}set{health.lastBusiness=newValue.timeIntervalSince1970}}
 var sessionReady=false,operation="",requestNumber:UInt32=UInt32.random(in:1...1000000),activeNumber:UInt32=0
 var started:UInt64=0,receiveStarted:UInt64=0,idleTimer:Timer?
 var messageRows:[[String:Any]]=[],messageSequence=0
 func nextNumber()->UInt32{requestNumber+=1;return requestNumber}
 func addMessage(_ from:String,_ text:String){messageSequence+=1;messageRows.append(["seq":messageSequence,"from":from,"text":text]);if messageRows.count>20{messageRows.removeFirst()}}
 func enqueue(_ body:Data)throws{guard linked,writeQueue.count<8 else{throw LabError("NOT_CONNECTED_OR_QUEUE_FULL")};if !isHealthFrame(body){lastActivity=Date()};if let offlineTransport=offlineTransport{try offlineTransport(packet(body));return};writeQueue.append(packet(body));if writing==nil{startWrite()}}
 func startWrite(){guard writing==nil,!writeQueue.isEmpty else{return};writing=NSMutableData(data:writeQueue.removeFirst());writeOffset=0;writeNext()}
 func sendLab(_ type:UInt8,_ id:UInt32,_ param:UInt32=0,_ body:Data=Data())throws{try enqueue(SessionFrame(type:type,id:id,param:param,body:body).encoded())}
 func begin(_ id:String,_ name:String,_ seconds:Double=45)throws {guard linked,testID==nil else{throw LabError("NOT_CONNECTED_OR_BUSY")};if name != "hello" && !sessionReady{throw LabError("PHONE_0_1_7_SESSION_REQUIRED")};testID=id;operation=name;activeNumber=nextNumber();started=DispatchTime.now().uptimeNanoseconds;timeout(seconds)}
 func finish(_ value:[String:Any]){walletProgress=[:];last=value;if let id=testID{respond(id,value)};testID=nil;operation="";expected.removeAll();timer?.invalidate();timer=nil}

 var cachedChannelAttempt=false,openingRFCOMMCall=false
 var connectBudgetTimer:Timer?
 var serviceRefresh:WalletServiceRefresh?
 var servicePoll:Timer?
 var discovery:DirectServiceDiscovery?,retiredDiscoveries:[DirectServiceDiscovery]=[]
 func stopDiscovery(){if let query=discovery{query.cancel();if query.hasOutstandingWrite{retiredDiscoveries.append(query)}};discovery=nil;retiredDiscoveries.removeAll{!$0.hasOutstandingWrite}}
 var timer:Timer?,state="disconnected",last:[String:Any]=[:]
 var events:[String]=[],lastError=""
 var rfcommNumber:BluetoothRFCOMMChannelID=0,rfcommStarted:TimeInterval=0
 func trace(_ event:String){events.append(event);if events.count>20{events.removeFirst()}}
 let vault=Vault()
 func failPending(_ message:String){if connectID != nil || testID != nil{lastError=message;trace("error: \(message), phase: \(state)")};if let id=connectID{respond(id,nil,message)};if let id=testID{respond(id,nil,message)};connectID=nil;testID=nil;operation="";expected.removeAll();timer?.invalidate();timer=nil}
 func disconnect(_ reason:String?=nil){connectBudgetTimer?.invalidate();connectBudgetTimer=nil;serviceRefresh?.cancel();serviceRefresh=nil;cachedChannelAttempt=false;if let reason=reason{lastError=reason;trace("disconnected: \(reason)")};walletSupported=false;walletProgress=[:];heartbeatSupported=false;health.pendingID=nil;health.pendingSince=nil;stopDiscovery();idleTimer?.invalidate();idleTimer=nil;sessionReady=false;writeQueue.removeAll();messageRows.removeAll();servicePoll?.invalidate();servicePoll=nil;failPending(reason ?? "DISCONNECTED");if let data=writing{retiredWrites.append(data)};let old=channel;channel=nil;state="disconnected";old?.close();buffer.removeAll();expected.removeAll();writing=nil;device=nil}
 func timeout(_ seconds:Double=45){timer?.invalidate();timer=Timer.scheduledTimer(withTimeInterval:seconds,repeats:false){[weak self] _ in guard let self=self else{return};if self.connectID != nil,self.state=="connecting"{self.trace("RFCOMM callback pending: channel=\(self.rfcommNumber), elapsed=\(Int(Date().timeIntervalSince1970-self.rfcommStarted))s, handle=\(self.channel != nil), open=\(self.channel?.isOpen() == true)")};if self.recoverCachedOpen(){return};let reason=self.connectID != nil ? "CONNECT_TIMEOUT_"+self.state : "SESSION_TIMEOUT_"+self.operation;self.failPending(reason);self.disconnect()}}
 func isHealthFrame(_ body:Data)->Bool{body.count>=5&&body.prefix(4)==Data("NKS2".utf8)&&(body[4]==13||body[4]==14)}
 func monitorConnection(){
  guard state=="connected" else{return}
  let now=Date().timeIntervalSince1970
  switch health.action(now:now,open:linked,ready:sessionReady,supported:heartbeatSupported){
  case .none:break
  case .disconnect(let reason):disconnect(reason)
  case .probe:
   let id=nextNumber();health.sent(id,now:now)
   do{try sendLab(13,id)}catch{disconnect("HEARTBEAT_WRITE_FAILED")}
  }
 }
 func status()->[String:Any]{monitorConnection();return ["state":state,"result":last,"protocol":"NKB1","maxBytes":65536,"error":lastError,"events":events,"sessionReady":state=="connected"&&sessionReady&&linked,"handshakePending":state=="connected"&&operation=="hello" && !sessionReady,"build":"nwallet-wallet-flow-1","walletSupported":walletSupported,"walletProgress":walletProgress,"heartbeatSupported":heartbeatSupported,"heartbeatPending":health.pendingID != nil,"messages":messageRows]}
 func command(_ object:[String:Any]) {
  guard let id=object["id"] as? String,let action=object["action"] as? String else{return}
  do {
   switch action {
   case "status": respond(id,status())
   case "wallet-touch-status":respond(id,WalletBiometrics().status())
   case "wallet-touch-enroll":
    guard let vaultID=object["vaultID"] as? String,let key=object["key"] as? String else{throw LabError("TOUCH_ID_FIELDS")}
    WalletBiometrics().enroll(id:vaultID,key:key){error in DispatchQueue.main.async{respond(id,["enrolled":error==nil],error)}}
   case "wallet-touch-unlock":
    guard let vaultID=object["vaultID"] as? String else{throw LabError("TOUCH_ID_FIELDS")}
    WalletBiometrics().unlock(id:vaultID){key,error in DispatchQueue.main.async{respond(id,key.map{["key":$0]},error)}}
   case "paired":
    let devices=(IOBluetoothDevice.pairedDevices() as? [IOBluetoothDevice] ?? []).map{["name":$0.name ?? "Bluetooth device","address":$0.addressString ?? ""]}
    respond(id,devices)
   case "connect":
    retiredDiscoveries.removeAll{!$0.hasOutstandingWrite}
    guard retiredDiscoveries.count<8,retiredWrites.count<8,connectID==nil && testID==nil && channel==nil,let address=object["address"] as? String,address.range(of:"^[0-9A-Fa-f]{2}([-:][0-9A-Fa-f]{2}){5}$",options:.regularExpression) != nil else {throw LabError("CONNECT_STATE_OR_ADDRESS")}
    guard let d=IOBluetoothDevice(addressString:address),d.isPaired() else {throw LabError("PAIR_IN_MAC_SETTINGS_FIRST")}
    events=[];lastError="";cachedChannelAttempt=false;device=d;connectID=id;state="discovering-service-direct";timeout()
    connectBudgetTimer?.invalidate();connectBudgetTimer=Timer.scheduledTimer(withTimeInterval:60,repeats:false){[weak self] _ in guard let self=self,self.connectID != nil else{return};self.failPending("CONNECT_TIMEOUT_total");self.disconnect()}
    let uuid=serviceBytes.withUnsafeBytes{IOBluetoothSDPUUID(bytes:$0.baseAddress,length:16)}
    trace("Exact wallet service cached: \(d.getServiceRecord(for:uuid) != nil); checking cached RFCOMM descriptor first")
    if openCachedWalletService(d){return}
    trace("Wallet UUID cache miss; starting bounded direct SDP with cache observation")
    let query=DirectServiceDiscovery(uuid:Data(serviceBytes));discovery=query
    query.completion={[weak self,weak query] result in
     guard let self=self,let query=query,self.discovery===query,self.connectID != nil,self.device===d else{return}
     self.stopDiscovery()
     switch result {
     case .success(let channel):self.trace("Live wallet UUID and advertised RFCOMM descriptor verified");self.openAdvertisedChannel(d,number:channel)
     case .failure(let error):if !self.openCachedWalletService(d){self.failPending(error.message);self.disconnect()}
     }
    }
    query.start(d)
    if state=="discovering-service-direct",connectID != nil {
     servicePoll=Timer.scheduledTimer(withTimeInterval:0.5,repeats:true){[weak self] _ in
      guard let self=self,let current=self.device else{return}
      _=self.openCachedWalletService(current)
     }
    }
   case "disconnect":disconnect();respond(id,status())
   case "session":
    try begin(id,"hello");trace("HELLO requested; waiting for NWallet READY and any handset connection consent");try sendLab(1,activeNumber,0,Data("NKSESSION2".utf8))
   case "wallet-info","wallet-check","wallet-open":
    guard testID==nil else{throw LabError("WALLET_BUSY")}
    guard sessionReady,walletSupported else{throw LabError("PHONE_WALLET_UPDATE_REQUIRED")}
    guard let uuid=object["requestID"] as? String,validWalletUUID(uuid) else{throw LabError("WALLET_REQUEST_ID")}
    var fields=[String]();let op=action=="wallet-info" ? "INFO" : action=="wallet-check" ? "CHECK" : "OPEN"
    if action != "wallet-info" {guard let root=object["rootID"] as? String,["fixture-zero-12-v1","fixture-zero-24-v1"].contains(root),let target=object["expected"] as? String,(action=="wallet-open" && target=="-") || walletHash(target) else{throw LabError("WALLET_FIELDS")};fields=[root,target];walletRoot=root;walletExpected=target}
    if action=="wallet-open" {guard let pass=object["passphrase"] as? String,pass.utf8.count<=50,pass.unicodeScalars.allSatisfy({$0.value>=32&&$0.value<=126}) else{throw LabError("WALLET_PASSPHRASE")};fields += [String(pass.utf8.count),pass]}
    let body=try walletBody(op,uuid,fields);try begin(id,action,action=="wallet-open" ? 125 : 10);walletUUID=uuid;walletProgress=["requestID":uuid,"phase":"等待诺基亚确认"];try sendLab(15,activeNumber,0,body)
   case "wallet-cancel":
    guard let uuid=object["requestID"] as? String,operation=="wallet-open",testID != nil,uuid==walletUUID else{throw LabError("NO_ACTIVE_WALLET_REQUEST")}
    try sendLab(15,nextNumber(),0,try walletBody("CANCEL",uuid));respond(id,["requested":true])
   case "message":
    guard let text=object["text"] as? String else{throw LabError("MESSAGE_FORMAT_OR_LIMIT")}
    let body=Data(text.utf8);_=try publicMessage(body);try begin(id,"message");expected=body;try sendLab(3,activeNumber,0,body)
   case "bench":
    guard let size=object["size"] as? Int,[16,1024,8192,32768,65536].contains(size),let direction=object["direction"] as? String,["down","up"].contains(direction) else{throw LabError("BENCH_FIELDS")}
    let seed=UInt32.random(in:0...255);try begin(id,direction);expected=try testPattern(size,seed)
    if direction=="down"{try sendLab(5,activeNumber,seed,expected)}else{try sendLab(7,activeNumber,seed,word(UInt32(size)))}
   case "simulate":
    guard let text=object["text"] as? String,(text.hasPrefix("SIM3\n") || text.hasPrefix("SIM2\n31337\n")),text.utf8.count<=2048 else{throw LabError("SIMULATION_ONLY")}
    try begin(id,"simulate",130);expected=Data(text.utf8);try sendLab(10,activeNumber,0,expected)
   case "test":
    guard let c=channel,c.isOpen(),testID==nil,writing==nil,let text=object["text"] as? String else {throw LabError("NOT_CONNECTED_OR_BUSY")}
    let data=Data(text.utf8);guard data.count>0 && data.count<=65536 else {throw LabError("PAYLOAD_LIMIT")}
    testID=id;operation="legacy";expected=data;last=[:];timeout();try enqueue(data)
   case "vault-load":respond(id,try vault.load())
   case "vault-save":try vault.save(object["records"] ?? []);respond(id,["saved":true])
   default:throw LabError("UNKNOWN_ACTION")
   }
  } catch let error as LabError {if testID==id{failPending(error.message)}else{respond(id,nil,error.message)}} catch {if testID==id{failPending("NATIVE_OPERATION_FAILED")}else{respond(id,nil,"NATIVE_OPERATION_FAILED")}}
 }
 @discardableResult func openCachedWalletService(_ d:IOBluetoothDevice)->Bool {
  guard connectID != nil,device===d,state=="discovering-service-direct" else{return false}
  let uuid=serviceBytes.withUnsafeBytes{IOBluetoothSDPUUID(bytes:$0.baseAddress,length:16)}
  guard let record=d.getServiceRecord(for:uuid) else{return false}
  var number:BluetoothRFCOMMChannelID=0
  guard record.getRFCOMMChannelID(&number)==kIOReturnSuccess,number>0,number<=30 else {
   failPending("WALLET_CACHED_RFCOMM_DESCRIPTOR_INVALID");disconnect();return true
  }
  trace("Exact wallet UUID cached record verified; using advertised RFCOMM channel (legacy proven path)")
  trace("Cached RFCOMM channel: \(number); cache hit is not proof of current remote service")
  stopDiscovery();servicePoll?.invalidate();servicePoll=nil
  openAdvertisedChannel(d,number:number,cached:true)
  return true
 }
 func openAdvertisedChannel(_ d:IOBluetoothDevice,number:BluetoothRFCOMMChannelID,cached:Bool=false) {
  guard connectID != nil,device===d,state=="discovering-service-direct",number>0,number<=30 else{return}
  servicePoll?.invalidate();servicePoll=nil
  state="connecting";cachedChannelAttempt=cached;timeout(20);rfcommNumber=number;rfcommStarted=Date().timeIntervalSince1970;var opened:IOBluetoothRFCOMMChannel?
  openingRFCOMMCall=true
  let result=d.openRFCOMMChannelAsync(&opened,withChannelID:number,delegate:self)
  openingRFCOMMCall=false
  trace("RFCOMM open start result: \(result)")
  trace("RFCOMM returned handle: \(opened != nil); waiting for open callback")
  if result != kIOReturnSuccess {if !recoverCachedOpen(){failPending("RFCOMM_OPEN_FAILED_\(result)");disconnect()}} else if state=="connecting" && channel==nil {channel=opened} else if channel !== opened {_=opened?.setDelegate(nil);opened?.close()}
  return
 }

 // One bounded refresh after a cached descriptor fails; healthy hits never query SDP.
 // Cached open 20s + refresh 20s + fresh open 20s stays within the existing 65s IPC/browser limit.
 @discardableResult func recoverCachedOpen()->Bool {
  guard cachedChannelAttempt,connectID != nil,state=="connecting",let current=device else{return false}
  cachedChannelAttempt=false;timer?.invalidate();timer=nil
  let failed=rfcommNumber,old=channel;channel=nil;state="refreshing-service"
  _=old?.setDelegate(nil);old?.close();stopDiscovery();servicePoll?.invalidate();servicePoll=nil
  trace("Cached RFCOMM channel \(failed) failed; refreshing the exact wallet UUID via system SDP")
  let query=WalletServiceRefresh(device:current,previous:failed);serviceRefresh=query
  query.completion={[weak self,weak query] result in
   guard let self=self,let query=query,self.serviceRefresh===query,self.connectID != nil,self.device===current,self.state=="refreshing-service" else{return}
   self.serviceRefresh=nil
   switch result {
   case .success(let number):self.trace("Fresh exact wallet UUID record verified; advertised RFCOMM channel: \(number)");self.state="discovering-service-direct";self.openAdvertisedChannel(current,number:number)
   case .failure(let error):self.failPending(error.message);self.disconnect()
   }
  }
  query.start();return true
 }
 func rfcommChannelOpenComplete(_ c:IOBluetoothRFCOMMChannel!,status:IOReturn){
  if !Thread.isMainThread{DispatchQueue.main.async{self.rfcommChannelOpenComplete(c,status:status)};return}
  trace("RFCOMM open callback: \(status)")
  guard channel===c || (openingRFCOMMCall && connectID != nil && channel==nil) else{return}
  if status==kIOReturnSuccess {connectBudgetTimer?.invalidate();connectBudgetTimer=nil;cachedChannelAttempt=false;channel=c;state="connected";lastActivity=Date();timer?.invalidate();
   health=SessionHealth(now:Date().timeIntervalSince1970)
   idleTimer?.invalidate();idleTimer=Timer.scheduledTimer(withTimeInterval:1,repeats:true){[weak self] _ in self?.monitorConnection()}

   if let id=connectID{respond(id,self.status())};connectID=nil}
  else {if !recoverCachedOpen(){failPending("RFCOMM_FAILED_\(status)");disconnect()}}
 }
 func writeNext(){
  guard let c=channel,let data=writing else{return}
  if writeOffset>=data.length {writing=nil;startWrite();return}
  writeCount=min(Int(c.getMTU()),data.length-writeOffset)
  guard writeCount>0 else{failPending("INVALID_MTU");disconnect();return}
  let result=c.writeAsync(data.mutableBytes.advanced(by:writeOffset),length:UInt16(writeCount),refcon:nil)
  if result != kIOReturnSuccess{failPending("WRITE_FAILED_\(result)");disconnect()}
 }
 func rfcommChannelWriteComplete(_ c:IOBluetoothRFCOMMChannel!,refcon:UnsafeMutableRawPointer!,status:IOReturn){
  if !Thread.isMainThread{DispatchQueue.main.async{self.rfcommChannelWriteComplete(c,refcon:nil,status:status)};return}
  guard channel===c else{return};if status != kIOReturnSuccess{failPending("WRITE_FAILED_\(status)");disconnect();return};writeOffset+=writeCount;writeNext()
 }
 func rfcommChannelData(_ c:IOBluetoothRFCOMMChannel!,data pointer:UnsafeMutableRawPointer!,length:Int){
  guard length>0,length<=262256,let pointer=pointer else{return}
  // Callback memory belongs to IOBluetooth and is valid only for this call.
  let chunk=Data(bytes:pointer,count:length)
  if !Thread.isMainThread{DispatchQueue.main.async{self.receiveData(c,chunk)};return}
  receiveData(c,chunk)
 }
 func receiveData(_ c:IOBluetoothRFCOMMChannel!,_ chunk:Data){
  guard channel===c else{return}
  guard buffer.count+chunk.count<=262256 else{failPending("FRAME_LIMIT");disconnect();return}
  if buffer.isEmpty{receiveStarted=DispatchTime.now().uptimeNanoseconds}
  buffer.append(chunk)
  do{while let body=try popFrame(&buffer){
   if !isHealthFrame(body){lastActivity=Date()}
   let rxMs=Double(DispatchTime.now().uptimeNanoseconds-receiveStarted)/1000000
   receiveStarted=DispatchTime.now().uptimeNanoseconds
   if let m=try SessionFrame.decode(body){try handle(m,rxMs)}
   else if operation=="legacy"{guard body==expected else{throw LabError("ECHO_MISMATCH")};finish(["bytes":body.count,"crc":String(format:"%08x",crc(body)),"equal":true])}
   else{throw LabError("UNEXPECTED_LEGACY_FRAME")}
  }}catch let e as LabError{failPending(e.message);lastError=e.message;disconnect()}catch{failPending("INVALID_FRAME");disconnect()}
 }
 func handle(_ m:SessionFrame,_ rxMs:Double)throws {
  if (16...18).contains(m.type){
   guard testID != nil,operation.hasPrefix("wallet-"),m.id==activeNumber else{return}
   guard m.param==0 else{throw LabError("WALLET_RESPONSE")};let rows=try walletFields(m.body,walletUUID)
   if m.type==17 {guard operation=="wallet-open",rows.count==4,rows[1]=="PROGRESS",rows[3].utf8.count<=100 else{throw LabError("WALLET_PROGRESS")};walletProgress=["requestID":walletUUID,"phase":rows[3]];return}
   if m.type==18 {let codes=["ROOT_NOT_CREATED","ROOT_MISMATCH","ADDRESS_MISMATCH","REQUEST_CONFLICT","WALLET_BUSY","WALLET_REJECTED","WALLET_CANCELLED","WALLET_TIMEOUT","WALLET_PASSPHRASE","WALLET_UNSUPPORTED","WALLET_FORMAT","WALLET_FAILED"];guard rows.count==4,rows[1]=="ERROR",codes.contains(rows[3]) else{throw LabError("WALLET_RESPONSE")};walletProgress=[:];failPending(rows[3]);return}
   if operation=="wallet-info" {guard rows.count==6,rows[1]=="INFO",let words=Int(rows[4]),[0,12,24].contains(words),let count=Int(rows[5]),(0...8).contains(count),rows[3]==(words==0 ? "NONE":"fixture-zero-\(words)-v1") else{throw LabError("WALLET_INFO")};finish(["rootID":rows[3],"words":words,"cachedWallets":count]);return}
   if operation=="wallet-check" {guard rows.count==6,rows[1]=="CHECK",rows[3]==walletRoot,rows[4]==walletExpected,["READY","MISSING"].contains(rows[5]) else{throw LabError("WALLET_CACHE")};finish(["rootID":rows[3],"fingerprint":rows[4],"ready":rows[5]=="READY"]);return}
   guard rows.count==11,rows[1]=="OPEN",rows[3]==walletRoot,walletHash(rows[4]),["0","1"].contains(rows[5]) else{throw LabError("WALLET_RESULT")};let bundle=rows[6...].joined(separator:"\n");guard walletFingerprint(bundle)==rows[4],walletExpected=="-" || walletExpected==rows[4] else{throw LabError("ADDRESS_MISMATCH")};finish(["rootID":rows[3],"fingerprint":rows[4],"cacheHit":rows[5]=="1","bundle":bundle,"requestID":walletUUID]);return
  }
  if m.type==12,operation=="hello",testID != nil,m.id==activeNumber {
   guard m.param==0,m.body==Data("CONNECTION_REJECTED".utf8) else{throw LabError("SESSION_ERROR_RESPONSE")}
   failPending("CONNECTION_REJECTED");disconnect();return
  }
  if m.type==14{guard sessionReady,heartbeatSupported,m.param==0,m.body.isEmpty else{throw LabError("HEARTBEAT_RESPONSE")};_=health.pong(m.id);return}
  if m.type==13{guard sessionReady,heartbeatSupported,m.param==0,m.body.isEmpty else{throw LabError("HEARTBEAT_REQUEST")};try sendLab(14,m.id);return}

  if m.type==3{guard sessionReady else{throw LabError("HANDSHAKE_REQUIRED")};let text=try publicMessage(m.body);addMessage("phone",text);try sendLab(4,m.id);return}
  if m.type==4 && (testID==nil || m.id != activeNumber){return}
  guard testID != nil,m.id==activeNumber else{return} // Ignore stale responses after cancellation.
  let rtt=Double(DispatchTime.now().uptimeNanoseconds-started)/1000000
  switch (operation,m.type){
   case ("hello",2):guard (m.param==2&&m.body.isEmpty)||(m.param==3&&m.body==Data("PING1".utf8))||(m.param==4&&m.body==Data("PING1|WALLET2".utf8)) else{throw LabError("SESSION_VERSION")};heartbeatSupported=m.param>=3;walletSupported=m.param==4;sessionReady=true;finish(["ready":true,"version":m.param,"heartbeat":heartbeatSupported,"walletSupported":walletSupported])
   case ("hello",1):throw LabError("PHONE_UPDATE_REQUIRED_0_1_7")
   case ("message",4):guard m.body.isEmpty else{throw LabError("MESSAGE_ACK")};let text=try publicMessage(expected);addMessage("computer",text);finish(["delivered":true,"requestId":m.id])
   case ("down",6):guard m.body.isEmpty,m.param<=45000 else{throw LabError("BENCH_ACK")};let elapsed=Double(m.param);finish(["direction":"down","bytes":expected.count,"crc":String(format:"%08x",crc(expected)),"equal":true,"roundTripMs":rtt,"receiveMs":elapsed,"receiveKiBs":elapsed>0 ? (Double(expected.count)/elapsed*1000/1024 as Any) : NSNull()])
   case ("up",8):guard m.body==expected else{throw LabError("UP_PATTERN_MISMATCH")};try sendLab(9,m.id);finish(["direction":"up","bytes":expected.count,"crc":String(format:"%08x",crc(m.body)),"equal":true,"roundTripMs":rtt,"receiveMs":rxMs,"receiveKiBs":rxMs>=1 ? (Double(m.body.count)/rxMs*1000/1024 as Any) : NSNull()])
   case ("simulate",11):guard let text=String(data:m.body,encoding:.utf8),["confirmed","rejected","expired"].contains(String(text.split(separator:":").first ?? "")),text==String(text.split(separator:":")[0])+":"+String(format:"%08x",crc(expected)) else{throw LabError("SIMULATION_RESPONSE_MISMATCH")};finish(["decision":String(text.split(separator:":")[0]),"requestId":m.id,"crc":String(format:"%08x",crc(expected)),"noSignature":true])
   default:throw LabError("UNEXPECTED_SESSION_RESPONSE")
  }
 }

 func rfcommChannelClosed(_ c:IOBluetoothRFCOMMChannel!){if !Thread.isMainThread{DispatchQueue.main.async{self.rfcommChannelClosed(c)};return};if channel===c{if !recoverCachedOpen(){disconnect("REMOTE_CLOSED")}}}

}

if CommandLine.arguments.contains("--wallet-flow-interop") {
 guard let at=CommandLine.arguments.firstIndex(of:"--fixtures"),CommandLine.arguments.count>at+1 else{fatalError("fixtures required")}
 let rows=try String(contentsOfFile:CommandLine.arguments[at+1],encoding:.utf8).split(separator:"\n");var fingerprint=""
 for row in rows{let pair=row.split(separator:"|",maxSplits:1);let m=try SessionFrame.decode(decodePacket(Data(base64Encoded:String(pair[1]))!)!)!;let fields=String(data:m.body,encoding:.utf8)!.components(separatedBy:"\n");let name=String(pair[0]);let lab=Bridge();var sent=[Data]();lab.offlineTransport={sent.append($0)};lab.sessionReady=true;lab.walletSupported=true;lab.requestNumber=m.id-1
  var command:[String:Any]=["id":name,"action":name=="INFO" ? "wallet-info":name=="CHECK" ? "wallet-check":"wallet-open","requestID":fields[2]]
  if name != "INFO"{command["rootID"]=name=="OPEN24" ? "fixture-zero-24-v1":"fixture-zero-12-v1";command["expected"]=name=="CHECK" ? fingerprint:"-"};if name != "INFO" && name != "CHECK"{command["passphrase"]=name=="OPEN24" ? " a ":name=="REJECT" ? "PUBLIC-DEMO":""}
  lab.command(command);precondition(lab.testID != nil);try lab.handle(SessionFrame(type:m.type,id:m.id+99,param:m.param,body:m.body),0);precondition(lab.testID != nil)
  if name=="OPEN"{try lab.handle(SessionFrame(type:17,id:m.id,param:0,body:Data(("NWALLET2\nPROGRESS\n"+fields[2]+"\nPUBLIC-PROGRESS").utf8)),0);precondition(lab.testID != nil && lab.walletProgress["phase"] as? String=="PUBLIC-PROGRESS");lab.command(["id":"cancel","action":"wallet-cancel","requestID":fields[2]]);let cancel=try SessionFrame.decode(decodePacket(sent.last!)!)!;precondition(cancel.type==15 && String(data:cancel.body,encoding:.utf8)!.hasSuffix(fields[2]));}
  try lab.handle(m,0);precondition(lab.testID==nil)
  if name=="REJECT"{precondition(lab.lastError=="WALLET_REJECTED")}else if name=="INFO"{precondition(lab.last["words"] as? Int==12)}else if name=="CHECK"{precondition(lab.last["ready"] as? Bool==true)}else{precondition(lab.last["rootID"] as? String==command["rootID"] as? String);fingerprint=lab.last["fingerprint"] as! String}
 }
 print("PASS final phone JAR wallet INFO/OPEN/CHECK/reject/24-word frames with actual Mac dispatch, request binding, progress and cancellation encoding; offline no radio")
} else if CommandLine.arguments.contains("--cache-refresh-self-test") {
 let stamp=Date(timeIntervalSince1970:100)
 var snapshot=WalletRefreshRecord(channel:14,updated:stamp),queries=0,results=[UInt8](),errors=[String]()
 func make()->WalletServiceRefresh{WalletServiceRefresh(previous:14,before:stamp,query:{queries+=1;return kIOReturnSuccess},read:{snapshot})}
 let refresh=make();refresh.completion={result in switch result{case .success(let n):results.append(n);case .failure(let e):errors.append(e.message)}};refresh.start()
 precondition(queries==1 && results.isEmpty) // old cache must not complete the refresh
 snapshot=WalletRefreshRecord(channel:13,updated:stamp.addingTimeInterval(1));refresh.observe()
 precondition(results==[13]);refresh.expire();refresh.observe();precondition(results==[13] && errors.isEmpty)
 snapshot=WalletRefreshRecord(channel:14,updated:stamp)
 let cancelled=make();cancelled.completion={_ in fatalError("cancelled query completed")};cancelled.start();cancelled.cancel();snapshot=WalletRefreshRecord(channel:13,updated:stamp.addingTimeInterval(2));cancelled.observe();cancelled.expire()
 snapshot=WalletRefreshRecord(channel:14,updated:stamp)
 let timeout=make();timeout.completion={result in if case .failure(let e)=result{errors.append(e.message)}};timeout.start();timeout.expire();precondition(errors==["SERVICE_REFRESH_TIMEOUT"])
 snapshot=WalletRefreshRecord(channel:nil,updated:stamp.addingTimeInterval(1))
 let missing=make();missing.completion={result in if case .failure(let e)=result{errors.append(e.message)}};missing.start();precondition(errors.last=="WALLET_SERVICE_NOT_ADVERTISED")
 precondition(WalletRefreshRecord.eligible(previous:14,before:stamp,current:WalletRefreshRecord(channel:14,updated:stamp))==nil)
 precondition(WalletRefreshRecord.eligible(previous:14,before:stamp,current:WalletRefreshRecord(channel:14,updated:stamp.addingTimeInterval(1)))==14)
 for invalid:UInt8 in [0,31,255]{precondition(WalletRefreshRecord.eligible(previous:14,before:stamp,current:WalletRefreshRecord(channel:invalid,updated:Date()))==nil)}
 print("PASS actual refresh coordinator with injected query/cache: stale 14 blocked, fresh 13 selected, one query, cancellation, late results, bounded timeout, absent service and channel bounds; no radio")
} else if CommandLine.arguments.contains("--wallet-interop") {
 guard let pathAt=CommandLine.arguments.firstIndex(of:"--fixtures"),CommandLine.arguments.count>pathAt+1 else{fatalError("fixtures required")}
 let rows=try String(contentsOfFile:CommandLine.arguments[pathAt+1],encoding:.utf8).split(separator:"\n")
 let lab=Bridge();var sent=[Data]();lab.offlineTransport={sent.append($0)};lab.requestNumber=6
 lab.command(["id":"hello","action":"session"])
 for row in rows {let parts=row.split(separator:"|",maxSplits:1);let frame=Data(base64Encoded:String(parts[1]))!;let body=try decodePacket(frame)!;let m=try SessionFrame.decode(body)!;if parts[0]=="REJECT"{let denied=Bridge();denied.offlineTransport={_ in};denied.requestNumber=6;denied.command(["id":"denied","action":"session"]);try denied.handle(SessionFrame(type:m.type,id:m.id+1,param:m.param,body:m.body),0);precondition(denied.testID != nil && denied.lastError.isEmpty);try denied.handle(m,0);precondition(!denied.sessionReady && denied.testID==nil && denied.state=="disconnected" && denied.lastError=="CONNECTION_REJECTED");continue};if parts[0]=="PONG"{lab.health.sent(m.id,now:Date().timeIntervalSince1970)};try lab.handle(m,0)}
 precondition(lab.sessionReady && lab.heartbeatSupported && lab.health.pendingID==nil && lab.messageRows.count==1)
 precondition((lab.messageRows[0]["text"] as? String)?.hasPrefix("NKW1\n12\nBTC|")==true)
 let ack=try SessionFrame.decode(decodePacket(sent.last!)!)!;precondition(ack.type==4 && ack.id==1000000001)
 print("PASS Java final-JAR NKS2 handshake, NKB1 CRC/frames, public address delivery and matching acknowledgment; offline no radio")
} else if CommandLine.arguments.contains("--session-self-test") {
 let lab=Bridge();var sent:[Data]=[]
 lab.offlineTransport={sent.append($0)}
 func lastSent()throws->SessionFrame{try SessionFrame.decode(decodePacket(sent.last!)!)!}
 lab.command(["id":"hello","action":"session"]);let hello=try lastSent();precondition(hello.type==1)
 try lab.handle(SessionFrame(type:2,id:hello.id,param:2,body:Data()),0);precondition(lab.sessionReady&&lab.testID==nil)
 lab.command(["id":"down","action":"bench","direction":"down","size":65536]);let down=try lastSent();precondition(down.body.count==65536 && down.body==(try! testPattern(65536,down.param)))
 try lab.handle(SessionFrame(type:3,id:999999999,param:0,body:Data("in-flight".utf8)),0);precondition(lab.testID != nil)
 try lab.handle(SessionFrame(type:6,id:down.id+1,param:10,body:Data()),0);precondition(lab.testID != nil)
 try lab.handle(SessionFrame(type:6,id:down.id,param:10,body:Data()),0);precondition(lab.last["equal"] as? Bool==true && lab.testID==nil)
 lab.command(["id":"up","action":"bench","direction":"up","size":65536]);let up=try lastSent();precondition(up.type==7)
 try lab.handle(SessionFrame(type:8,id:up.id,param:0,body:try testPattern(65536,up.param)),30);precondition(lab.last["bytes"] as? Int==65536)
 try lab.handle(SessionFrame(type:3,id:1000000001,param:0,body:Data("来自手机的中文消息".utf8)),0);precondition(lab.messageRows.count==2 && (try! lastSent()).type==4)
 lab.command(["id":"message","action":"message","text":"电脑中文消息"]);let message=try lastSent();try lab.handle(SessionFrame(type:4,id:message.id,param:0,body:Data()),0);precondition(lab.messageRows.count==3)
 if let at=CommandLine.arguments.firstIndex(of:"--simulation"),CommandLine.arguments.count>at+1 {
  let raw=try Data(contentsOf:URL(fileURLWithPath:CommandLine.arguments[at+1]));let text=String(data:raw,encoding:.utf8)!
  for decision in ["confirmed","rejected","expired"] {
   lab.command(["id":decision,"action":"simulate","text":text]);let tx=try lastSent();precondition(tx.type==10 && tx.body==raw)
   do{try lab.handle(SessionFrame(type:11,id:tx.id,param:0,body:Data("confirmed:00000000".utf8)),0);fatalError("mismatched request accepted")}catch{}
   precondition(lab.testID != nil)
   try lab.handle(SessionFrame(type:11,id:tx.id,param:0,body:Data((decision+":"+String(format:"%08x",crc(raw))).utf8)),0);precondition(lab.last["decision"] as? String==decision)
  }
 }
 if let at=CommandLine.arguments.firstIndex(of:"--fixtures"),CommandLine.arguments.count>at+1 {
  for line in try String(contentsOfFile:CommandLine.arguments[at+1],encoding:.utf8).split(separator:"\n") {
   let data=Data(base64Encoded:String(line))!,m=try SessionFrame.decode(data)!;let encoded=try m.encoded();precondition(encoded==data)
   if m.type==8 && m.id==18{precondition(m.body==(try! testPattern(m.body.count,253)))}
   let framed=packet(data);var stream=Data();for byte in framed{stream.append(byte);if stream.count<framed.count{let partial=try popFrame(&stream);precondition(partial==nil)}}
   let complete=try popFrame(&stream);precondition(complete==data && stream.isEmpty)
   stream=framed+framed;let one=try popFrame(&stream),two=try popFrame(&stream);precondition(one==data && two==data && stream.isEmpty)
  }
 }
 let watch=Bridge();var healthFrames=[Data]();watch.offlineTransport={healthFrames.append($0)}
 watch.command(["id":"health-hello","action":"session"])
 let h=try SessionFrame.decode(decodePacket(healthFrames.last!)!)!
 try watch.handle(SessionFrame(type:2,id:h.id,param:3,body:Data("PING1".utf8)),0)
 precondition(watch.heartbeatSupported);watch.state="connected"
 let now=Date().timeIntervalSince1970;watch.health.lastBusiness=now-100;watch.health.lastProbe=now-16
 let businessBefore=watch.health.lastBusiness;watch.monitorConnection()
 let ping=try SessionFrame.decode(decodePacket(healthFrames.last!)!)!;precondition(ping.type==13 && watch.health.pendingID==ping.id && watch.health.lastBusiness==businessBefore)
 try watch.handle(SessionFrame(type:14,id:ping.id+1,param:0,body:Data()),0);precondition(watch.health.pendingID==ping.id)
 try watch.handle(SessionFrame(type:14,id:ping.id,param:0,body:Data()),0);precondition(watch.health.pendingID==nil && watch.health.lastBusiness==businessBefore)
 watch.health.lastBusiness=now-301;watch.monitorConnection();precondition(watch.state=="disconnected" && !watch.sessionReady && watch.lastError=="IDLE_TIMEOUT_5_MIN")
 let stale=Bridge();stale.state="connected";stale.sessionReady=true
 let staleStatus=stale.status();precondition(staleStatus["sessionReady"] as? Bool==false && stale.lastError=="CHANNEL_CLOSED")
 print("PASS: native heartbeat negotiation/nonce, probes do not reset business idle, idle disconnect, stale channel status invalidation; offline")
 print("PASS: native session dispatch, stale IDs, both directions, UTF8 messages, simulation decisions/CRC binding, Java typed interop and fragmented/coalesced stream; offline, no radio/Keychain")
} else if CommandLine.arguments.contains("--self-test") {
 let key=SymmetricKey(size:.bits256),raw=Data("标签：公司；public-address-only".utf8),aad=Data("test".utf8)
 let encrypted=try AES.GCM.seal(raw,using:key,authenticating:aad).combined!
 let opened=try AES.GCM.open(AES.GCM.SealedBox(combined:encrypted),using:key,authenticating:aad);precondition(opened==raw)
 var damaged=encrypted;damaged[damaged.count-1] ^= 1
 do {_=try AES.GCM.open(AES.GCM.SealedBox(combined:damaged),using:key,authenticating:aad);fatalError("tamper accepted")}catch{}
 precondition(crc(Data("123456789".utf8))==0xcbf43926)
 for n in [1,1776,8192,32768,65536]{let d=Data(repeating:65,count:n),p=packet(d);precondition(p.count==n+12 && Int(number(p,4))==n && number(p,8)==crc(d))}
 let dir=FileManager.default.temporaryDirectory.appendingPathComponent("nokia-vault-test-"+UUID().uuidString),file=dir.appendingPathComponent("book.enc")
 defer {try? FileManager.default.removeItem(at:dir)}
 let vault=Vault(testURL:file,testKey:key),record:[String:Any]=["id":"test","chain":"ETH","address":"0x1111111111111111111111111111111111111111","label":"公司钱包","path":NSNull(),"verified":false]
 try vault.save([record]);let loaded=try vault.load() as! [String:Any];precondition((loaded["records"] as! [[String:Any]])[0]["label"] as! String=="公司钱包")
 let disk=try Data(contentsOf:file);precondition(disk.range(of:Data("公司钱包".utf8))==nil)
 var corrupt=disk;corrupt[0] ^= 1;try corrupt.write(to:file)
 do {_=try vault.load();fatalError("damaged vault accepted")}catch{}
 do {try vault.save([record]);fatalError("damaged vault overwritten")}catch{}
 let unchanged=try Data(contentsOf:file);precondition(unchanged==corrupt)
 do {try vault.save([["label":"x","address":"x","passphrase":"forbidden"]]);fatalError("secret field accepted")}catch{}
 if let at=CommandLine.arguments.firstIndex(of:"--fixtures"),CommandLine.arguments.count>at+1 {
  let lines=try String(contentsOfFile:CommandLine.arguments[at+1],encoding:.utf8).split(separator:"\n")
  for line in lines {let parts=line.split(separator:"|");if parts[0]=="UUID"{precondition(serviceBytes.map{String(format:"%02x",$0)}.joined()==String(parts[1]));continue};let body=Data(base64Encoded:String(parts[0]))!,javaFrame=Data(base64Encoded:String(parts[1]))!;precondition(packet(body)==javaFrame);let decoded=try decodePacket(javaFrame);precondition(decoded==body);for cut in [0,1,4,11,12,javaFrame.count-1] {let partial=try decodePacket(javaFrame.prefix(cut));precondition(partial==nil)}}
 }
 print("PASS: NKB1 framing/CRC, 64KiB bounds, encrypted Chinese vault disk roundtrip, corruption/secret rejection; no Bluetooth/Keychain accessed")
} else {
 let bridge=Bridge()
 DispatchQueue.global().async {
  while let line=readLine(){if line.utf8.count>1048576{continue};guard let data=line.data(using:.utf8),let command=(try? JSONSerialization.jsonObject(with:data)) as? [String:Any] else{continue};DispatchQueue.main.async{bridge.command(command)}}
  DispatchQueue.main.async{bridge.disconnect();exit(0)}
 }
 RunLoop.main.run()
}
