import Foundation
import IOBluetooth

// Bluetooth Core Vol 3 Part B: ServiceSearchAttribute, exact 128-bit UUID.
// This does not query/overwrite the system SDP cache and never guesses a channel.
struct SDPElement {
 let kind: UInt8
 let bytes: Data
 let children: [SDPElement]
 var unsigned: UInt64? {
  guard kind == 1, [1,2,4,8].contains(bytes.count) else { return nil }
  return bytes.reduce(0) { ($0 << 8) | UInt64($1) }
 }
 static func decode(_ data: Data) throws -> SDPElement {
  var offset = 0
  let element = try read(data, &offset, 0)
  guard offset == data.count else { throw LabError("SDP_ELEMENT_TRAILING") }
  return element
 }
 private static func read(_ data: Data, _ offset: inout Int, _ depth: Int) throws -> SDPElement {
  guard depth <= 8, offset < data.count else { throw LabError("SDP_ELEMENT_BOUNDS") }
  let header = data[offset]; offset += 1
  let kind = header >> 3, size = header & 7
  var length = kind == 0 ? 0 : 1 << Int(size)
  if size >= 5 {
   let lengthBytes = 1 << Int(size - 5)
   guard offset + lengthBytes <= data.count else { throw LabError("SDP_ELEMENT_LENGTH") }
   var value: UInt64 = 0
   for _ in 0..<lengthBytes { value = (value << 8) | UInt64(data[offset]); offset += 1 }
   guard value <= 16384 else { throw LabError("SDP_ELEMENT_LIMIT") }
   length = Int(value)
  }
  guard offset + length <= data.count else { throw LabError("SDP_ELEMENT_TRUNCATED") }
  let bytes = Data(data[offset..<offset+length]); offset += length
  var children = [SDPElement]()
  if kind == 6 {
   var childOffset = 0
   while childOffset < bytes.count {
    guard children.count < 256 else { throw LabError("SDP_ELEMENT_COUNT") }
    children.append(try read(bytes, &childOffset, depth + 1))
   }
  }
  return SDPElement(kind: kind, bytes: bytes, children: children)
 }
 func uuidMatches(_ uuid: Data) -> Bool {
  guard kind == 3 else { return false }
  if bytes.count == 16 { return bytes == uuid }
  return false
 }
 var isRFCOMM: Bool {
  guard kind == 3 else { return false }
  return bytes == Data([0,3]) || bytes == Data([0,0,0,3]) || bytes == Data([0,0,0,3,0,0,0x10,0,0x80,0,0,0x80,0x5f,0x9b,0x34,0xfb])
 }
}

struct SDPExchange {
 let uuid: Data
 var transaction: UInt16 = 0
 var attributes = Data()
 var pages = 0
 var continuations = Set<Data>()
 init(uuid: Data) { self.uuid = uuid }
 mutating func request(continuation: Data = Data()) throws -> Data {
  guard uuid.count == 16, continuation.count <= 16, pages < 16 else { throw LabError("SDP_REQUEST_LIMIT") }
  transaction += 1
  // Ask for ServiceClassIDList through ProtocolDescriptorList (0x0001..0x0004),
  // then verify that the returned record actually contains the requested UUID.
  let parameters = Data([0x35,17,0x1c]) + uuid + Data([4,0,0x35,5,0x0a,0,1,0,4,UInt8(continuation.count)]) + continuation
  return Data([6,UInt8(transaction >> 8),UInt8(transaction & 255),UInt8(parameters.count >> 8),UInt8(parameters.count & 255)]) + parameters
 }
 mutating func receive(_ pdu: Data) throws -> (continuation: Data?, channel: UInt8?) {
  guard pdu.count >= 8, pdu[1] == UInt8(transaction >> 8), pdu[2] == UInt8(transaction & 255) else { throw LabError("SDP_TRANSACTION") }
  if pdu[0] == 1 { throw LabError("SDP_REMOTE_ERROR") }
  guard pdu[0] == 7, Int(pdu[3])*256+Int(pdu[4]) == pdu.count-5 else { throw LabError("SDP_RESPONSE_LENGTH") }
  let length = Int(pdu[5])*256+Int(pdu[6]), nextAt = 7+length
  guard length <= 1024, nextAt < pdu.count, Int(pdu[nextAt]) <= 16, nextAt+1+Int(pdu[nextAt]) == pdu.count else { throw LabError("SDP_CONTINUATION_BOUNDS") }
  guard attributes.count+length <= 16384, pages < 16 else { throw LabError("SDP_RESPONSE_LIMIT") }
  pages += 1; attributes.append(pdu[7..<nextAt])
  let next = Data(pdu[(nextAt+1)..<pdu.count])
  if !next.isEmpty {
   guard length > 0, continuations.insert(next).inserted else { throw LabError("SDP_CONTINUATION_LOOP") }
   return (next,nil)
  }
  return (nil,try channel())
 }
 func channel() throws -> UInt8 {
  let outer = try SDPElement.decode(attributes)
  guard outer.kind == 6, outer.children.count <= 32 else { throw LabError("SDP_RECORDS") }
  var found = [UInt8]()
  for record in outer.children {
   guard record.kind == 6, record.children.count % 2 == 0 else { throw LabError("SDP_RECORD_FORMAT") }
   var matches = false, protocolList: SDPElement?
   var ids = Set<UInt64>()
   for at in stride(from: 0,to: record.children.count,by: 2) {
    let id = record.children[at], value = record.children[at+1]
    guard id.bytes.count == 2, let number = id.unsigned, ids.insert(number).inserted else { throw LabError("SDP_ATTRIBUTE_ID") }
    if number == 1 { matches = value.kind == 6 && value.children.contains { $0.uuidMatches(uuid) } }
    if number == 4 { protocolList = value }
   }
   guard matches else { continue }
   guard let list = protocolList, list.kind == 6 else { throw LabError("SDP_NO_PROTOCOL_LIST") }
   for protocolItem in list.children {
    guard protocolItem.kind == 6, let first = protocolItem.children.first else { throw LabError("SDP_PROTOCOL_FORMAT") }
    if first.isRFCOMM {
     guard protocolItem.children.count == 2, protocolItem.children[1].bytes.count == 1,
      let value = protocolItem.children[1].unsigned, (1...30).contains(value) else { throw LabError("SDP_RFCOMM_CHANNEL") }
     found.append(UInt8(value))
    }
   }
  }
  guard found.count == 1 else { throw LabError(found.isEmpty ? "WALLET_SERVICE_NOT_ADVERTISED" : "SDP_AMBIGUOUS_SERVICE") }
  return found[0]
 }
}

final class DirectServiceDiscovery: NSObject, IOBluetoothL2CAPChannelDelegate {
 private var channel: IOBluetoothL2CAPChannel?
 private var exchange: SDPExchange
 private var buffer = Data()
 private var writing: NSMutableData?, queued: Data?
 private var timer: Timer?
 private var active = true
 var completion: ((Result<UInt8,LabError>) -> Void)?
 var hasOutstandingWrite: Bool { writing != nil }
 init(uuid: Data) { exchange = SDPExchange(uuid: uuid) }
 func start(_ device: IOBluetoothDevice) {
  timer = Timer.scheduledTimer(withTimeInterval:30,repeats:false) { [weak self] _ in self?.finish(.failure(LabError("SDP_DIRECT_TIMEOUT"))) }
  var opened: IOBluetoothL2CAPChannel?
  let status = device.openL2CAPChannelAsync(&opened,withPSM:1,delegate:self)
  if status == kIOReturnSuccess { if channel == nil && active { channel = opened } }
  else { finish(.failure(LabError("SDP_L2CAP_OPEN_\(status)"))) }
 }
 func cancel() {
  active = false; completion = nil; timer?.invalidate(); timer = nil; queued = nil
  _ = channel?.close(); channel = nil
  // Outstanding async write memory stays owned until write completion or retirement.
 }
 private func finish(_ result: Result<UInt8,LabError>) {
  guard active else { return }
  let callback = completion; cancel(); callback?(result)
 }
 private func send(_ data: Data) {
  guard active else { return }
  if writing != nil { queued = data; return }
  guard let channel = channel, data.count <= Int(channel.outgoingMTU) else { finish(.failure(LabError("SDP_REQUEST_MTU"))); return }
  let retained = NSMutableData(data:data); writing = retained
  let status = channel.writeAsync(retained.mutableBytes,length:UInt16(retained.length),refcon:nil)
  if status != kIOReturnSuccess { finish(.failure(LabError("SDP_WRITE_\(status)"))) }
 }
 func l2capChannelOpenComplete(_ c: IOBluetoothL2CAPChannel!,status: IOReturn) {
  if !Thread.isMainThread { DispatchQueue.main.async { self.l2capChannelOpenComplete(c,status:status) }; return }
  guard active, channel == nil || channel === c else { return }
  channel = c
  guard status == kIOReturnSuccess else { finish(.failure(LabError("SDP_L2CAP_CALLBACK_\(status)"))); return }
  do { send(try exchange.request()) } catch { finish(.failure(LabError("SDP_REQUEST"))) }
 }
 func l2capChannelWriteComplete(_ c: IOBluetoothL2CAPChannel!,refcon: UnsafeMutableRawPointer!,status: IOReturn) {
  if !Thread.isMainThread { DispatchQueue.main.async { self.l2capChannelWriteComplete(c,refcon:nil,status:status) }; return }
  writing = nil
  guard active, channel === c else { return }
  guard status == kIOReturnSuccess else { finish(.failure(LabError("SDP_WRITE_CALLBACK_\(status)"))); return }
  if let data = queued { queued = nil; send(data) }
 }
 func l2capChannelData(_ c: IOBluetoothL2CAPChannel!,data pointer: UnsafeMutableRawPointer!,length: Int) {
  guard length > 0,length <= 4096,let pointer = pointer else { return }
  let data = Data(bytes:pointer,count:length)
  if !Thread.isMainThread { DispatchQueue.main.async { self.receive(c,data) }; return }
  receive(c,data)
 }
 private func receive(_ c: IOBluetoothL2CAPChannel!,_ data: Data) {
  guard active,channel === c else { return }
  guard buffer.count+data.count <= 4096 else { finish(.failure(LabError("SDP_FRAME_LIMIT"))); return }
  buffer.append(data)
  do {
   while buffer.count >= 5 {
    let length = Int(buffer[3])*256+Int(buffer[4])
    guard length <= 2048 else { throw LabError("SDP_PDU_LIMIT") }
    guard buffer.count >= 5+length else { return }
    let pdu = Data(buffer.prefix(5+length)); buffer = Data(buffer.dropFirst(5+length))
    let result = try exchange.receive(pdu)
    if let next = result.continuation { send(try exchange.request(continuation:next)) }
    else if let number = result.channel { finish(.success(number)); return }
   }
  } catch let error as LabError { finish(.failure(error)) }
  catch { finish(.failure(LabError("SDP_INVALID_RESPONSE"))) }
 }
 func l2capChannelClosed(_ c: IOBluetoothL2CAPChannel!) {
  if !Thread.isMainThread { DispatchQueue.main.async { self.l2capChannelClosed(c) }; return }
  if active && channel === c { finish(.failure(LabError("SDP_REMOTE_CLOSED"))) }
 }
}
