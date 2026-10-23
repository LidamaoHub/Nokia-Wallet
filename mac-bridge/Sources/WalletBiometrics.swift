import Foundation
import LocalAuthentication
import Security

/// Optional convenience access to the sample vault's random data key.
/// The long password is never sent to or retained by this component.
final class WalletBiometrics {
 private let service="tech.lidamao.nokia.walletdev.sample-unlock.v1"
 func status()->[String:Any]{let context=LAContext();var error:NSError?;return ["available":context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics,error:&error),"scope":"sample-vault"]}
 private func account(_ id:String)throws->String {guard id.count<=80,!id.isEmpty,id.unicodeScalars.allSatisfy({CharacterSet.alphanumerics.contains($0)||$0=="-"}) else {throw LabError("VAULT_ID_INVALID")};return id}
 func enroll(id:String,key:String,completion:@escaping (String?)->Void){
  do{_ = try account(id);guard let raw=Data(base64Encoded:key),raw.count==32 else{throw LabError("VAULT_KEY_INVALID")}
   let context=LAContext();context.localizedFallbackTitle="";var error:NSError?
   guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics,error:&error) else {throw LabError("TOUCH_ID_UNAVAILABLE")}
   context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics,localizedReason:"为 NWallet 公开样本钱包启用指纹解锁"){[self] success,_ in
    guard success else{completion("TOUCH_ID_CANCELLED_OR_FAILED");return}
    var error:Unmanaged<CFError>?
    guard let access=SecAccessControlCreateWithFlags(nil,kSecAttrAccessibleWhenUnlockedThisDeviceOnly,.biometryCurrentSet,&error) else{completion("TOUCH_ID_ACCESS_CONTROL_FAILED");return}
    let query:[String:Any]=[kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:service,kSecAttrAccount as String:id]
    var item=query;item[kSecAttrAccessControl as String]=access;item[kSecValueData as String]=raw;item[kSecUseAuthenticationContext as String]=context
    // An existing enrolled item is not silently replaced under a weaker ACL.
    let result=SecItemAdd(item as CFDictionary,nil)
    completion(result==errSecSuccess ? nil : result==errSecDuplicateItem ? "TOUCH_ID_ALREADY_ENROLLED" : "TOUCH_ID_KEYCHAIN_SAVE_FAILED")
   }
  }catch{completion((error as? LabError)?.message ?? "TOUCH_ID_SETUP_FAILED")}
 }
 func unlock(id:String,completion:@escaping (String?,String?)->Void){
  do{_ = try account(id);let context=LAContext();context.localizedFallbackTitle="";var error:NSError?;guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics,error:&error) else{throw LabError("TOUCH_ID_UNAVAILABLE")}
   context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics,localizedReason:"解锁 NWallet 公开样本钱包库"){[self] success,_ in
    guard success else{completion(nil,"TOUCH_ID_CANCELLED_OR_FAILED");return}
    let query:[String:Any]=[kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:service,kSecAttrAccount as String:id,kSecReturnData as String:true,kSecMatchLimit as String:kSecMatchLimitOne,kSecUseAuthenticationContext as String:context]
    var result:CFTypeRef?;let status=SecItemCopyMatching(query as CFDictionary,&result)
    guard status==errSecSuccess,let data=result as? Data,data.count==32 else{completion(nil,"TOUCH_ID_KEYCHAIN_LOAD_FAILED");return}
    completion(data.base64EncodedString(),nil)
   }
  }catch{completion(nil,(error as? LabError)?.message ?? "TOUCH_ID_UNLOCK_FAILED")}
 }
}
