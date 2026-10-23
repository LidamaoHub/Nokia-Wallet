import Foundation
struct SessionFrame {
 let type:UInt8,id:UInt32,param:UInt32,body:Data
 func encoded()throws->Data {
  guard (1...18).contains(type),id>0,id<=UInt32(Int32.max),body.count<=65536 else{throw LabError("NKS2_FIELDS")}
  return Data("NKS2".utf8)+Data([type])+word(id)+word(param)+body
 }
 static func decode(_ data:Data)throws->SessionFrame? {
  guard data.prefix(4)==Data("NKS2".utf8) else{return nil}
  guard data.count>=13,data.count<=65549,(1...18).contains(data[4]) else{throw LabError("NKS2_FIELDS")}
  let id=number(data,5);guard id>0,id<=UInt32(Int32.max) else{throw LabError("NKS2_ID")}
  return SessionFrame(type:data[4],id:id,param:number(data,9),body:data.subdata(in:13..<data.count))
 }
}
func testPattern(_ size:Int,_ seed:UInt32)throws->Data {guard size>0,size<=65536 else{throw LabError("SIZE")};return Data((0..<size).map{UInt8(truncatingIfNeeded:$0*31+Int(seed&255))})}
func publicMessage(_ data:Data)throws->String {
 guard data.count>0,data.count<=512,let text=String(data:data,encoding:.utf8),!text.unicodeScalars.contains(where:{$0.value<32 && $0.value != 10 && $0.value != 9}) else{throw LabError("MESSAGE_FORMAT_OR_LIMIT")};return text
}
/// A stream may fragment headers/payloads or coalesce several complete frames.
func popFrame(_ buffer:inout Data)throws->Data? {
 guard buffer.count>=12 else{return nil};let size=Int(number(buffer,4))
 guard buffer.prefix(4)==Data("NKB1".utf8),size>0,size<=65552 else{throw LabError("INVALID_FRAME")}
 guard buffer.count>=12+size else{return nil}
 let frame=Data(buffer.prefix(12+size));buffer.removeFirst(12+size)
 // Data indices after removeFirst need rebasing for the next parser call.
 buffer=Data(buffer);return try decodePacket(frame)
}
