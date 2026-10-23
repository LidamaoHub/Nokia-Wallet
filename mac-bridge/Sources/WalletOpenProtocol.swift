import Foundation
import CryptoKit
func validWalletUUID(_ value:String)->Bool {value.range(of:"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",options:.regularExpression) != nil}
func walletHash(_ value:String)->Bool {value.range(of:"^[0-9a-f]{64}$",options:.regularExpression) != nil}
func walletFingerprint(_ text:String)->String {SHA256.hash(data:Data(text.utf8)).map{String(format:"%02x",$0)}.joined()}
func walletBody(_ op:String,_ uuid:String,_ fields:[String]=[])throws->Data {guard validWalletUUID(uuid) else{throw LabError("WALLET_REQUEST_ID")};let body=Data((["NWALLET2",op,uuid]+fields).joined(separator:"\n").utf8);guard body.count<=512 else{throw LabError("WALLET_LIMIT")};return body}
func walletFields(_ data:Data,_ uuid:String)throws->[String] {guard data.count<=1024,let text=String(data:data,encoding:.utf8) else{throw LabError("WALLET_RESPONSE")};let rows=text.components(separatedBy:"\n");guard rows.count>=3,rows[0]=="NWALLET2",rows[2]==uuid else{throw LabError("WALLET_RESPONSE_BINDING")};return rows}
