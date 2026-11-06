import UIKit
import UserNotifications
import FirebaseCore
import FirebaseAuth
import FirebaseFirestore
import FirebaseMessaging

final class PawlyAppDelegate:NSObject,UIApplicationDelegate,UNUserNotificationCenterDelegate,MessagingDelegate{
 private var authHandle:AuthStateDidChangeListenerHandle?
 func application(_ application:UIApplication,didFinishLaunchingWithOptions launchOptions:[UIApplication.LaunchOptionsKey:Any]? = nil)->Bool{
  if FirebaseApp.app()==nil,Bundle.main.url(forResource:"GoogleService-Info",withExtension:"plist") != nil{FirebaseApp.configure()}
  UNUserNotificationCenter.current().delegate=self;Messaging.messaging().delegate=self
  authHandle=Auth.auth().addStateDidChangeListener{_,user in guard let user else{return};Messaging.messaging().token{token,_ in if let token{Self.save(token:token,uid:user.uid)}}}
  return true
 }
 func application(_ application:UIApplication,didRegisterForRemoteNotificationsWithDeviceToken deviceToken:Data){Messaging.messaging().apnsToken=deviceToken}
 func application(_ application:UIApplication,didFailToRegisterForRemoteNotificationsWithError error:Error){print("Pawly APNs registration failed: \(error.localizedDescription)")}
 func messaging(_ messaging:Messaging,didReceiveRegistrationToken fcmToken:String?){guard let token=fcmToken,let uid=Auth.auth().currentUser?.uid else{return};Self.save(token:token,uid:uid)}
 private static func save(token:String,uid:String){
  let tokenID=token.replacingOccurrences(of:"/",with:"_")
  Firestore.firestore().collection("users").document(uid).collection("fcmTokens").document(tokenID)
   .setData(["token":token,"platform":"ios","updatedAt":FieldValue.serverTimestamp()],merge:true)
 }
 func userNotificationCenter(_ center:UNUserNotificationCenter,willPresent notification:UNNotification,withCompletionHandler completionHandler:@escaping(UNNotificationPresentationOptions)->Void){completionHandler([.banner,.sound,.badge])}
 func userNotificationCenter(_ center:UNUserNotificationCenter,didReceive response:UNNotificationResponse,withCompletionHandler completionHandler:@escaping()->Void){completionHandler()}
 func application(_ app:UIApplication,open url:URL,options:[UIApplication.OpenURLOptionsKey:Any]=[:])->Bool{PawlyGoogleSignIn.handle(url)}
}
