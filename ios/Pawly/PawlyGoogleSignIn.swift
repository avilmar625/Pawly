import Foundation
import UIKit
import FirebaseAuth
import FirebaseFirestore
import GoogleSignIn

enum PawlyGoogleSignIn{
 static func handle(_ url:URL)->Bool{GIDSignIn.sharedInstance.handle(url)}
 @MainActor static func signIn()async throws{
  guard let clientID=Bundle.main.object(forInfoDictionaryKey:"GIDClientID") as? String,!clientID.isEmpty else{throw PawlyGoogleError.missingClientID}
  guard let presenter=UIApplication.shared.connectedScenes.compactMap({$0 as? UIWindowScene}).flatMap({$0.windows}).first(where:\.isKeyWindow)?.rootViewController else{throw PawlyGoogleError.noPresenter}
  GIDSignIn.sharedInstance.configuration=GIDConfiguration(clientID:clientID)
  let result=try await GIDSignIn.sharedInstance.signIn(withPresenting:presenter)
  guard let idToken=result.user.idToken?.tokenString else{throw PawlyGoogleError.missingToken}
  let credential=GoogleAuthProvider.credential(withIDToken:idToken,accessToken:result.user.accessToken.tokenString)
  let authResult=try await Auth.auth().signIn(with:credential),user=authResult.user
  let userRef=Firestore.firestore().collection("users").document(user.uid)
  if !(try await userRef.getDocument()).exists{
   let name=user.displayName ?? user.email?.components(separatedBy:"@").first ?? "Usuario Pawly"
   let seed=(user.email?.components(separatedBy:"@").first ?? "pawly").lowercased().replacingOccurrences(of:"[^a-z0-9_.]",with:"",options:.regularExpression)
   let username=String(seed.prefix(18))+"_"+String(user.uid.prefix(5))
   try await Firestore.firestore().collection("usernames").document(username).setData(["uid":user.uid])
   try await userRef.setData(["uid":user.uid,"email":user.email ?? "","username":username,"usernameLower":username,"displayName":name,"bio":"","photoUrl":user.photoURL?.absoluteString ?? "","isPublic":true,"followersCount":0,"followingCount":0,"dogProfiles":[],"createdAt":FieldValue.serverTimestamp(),"platform":"ios"])
  }
 }
}
enum PawlyGoogleError:LocalizedError{
 case missingClientID,noPresenter,missingToken
 var errorDescription:String?{switch self{case .missingClientID:return "Falta GIDClientID en GoogleService-Info.plist.";case .noPresenter:return "No se ha podido abrir la ventana de Google.";case .missingToken:return "Google no ha devuelto un token válido."}}
}
