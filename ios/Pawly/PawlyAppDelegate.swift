import UIKit
import UserNotifications
import FirebaseCore
import FirebaseAuth
import FirebaseFirestore
import FirebaseMessaging

final class PawlyAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    private var authHandle: AuthStateDidChangeListenerHandle?

    private static let pushPreferenceKey = "pawly.notificationsEnabled"
    private static let registeredUIDKey = "pawly.push.registeredUID"
    private static let registeredTokenKey = "pawly.push.registeredToken"

    private static var pushNotificationsEnabled: Bool {
        UserDefaults.standard.object(forKey: pushPreferenceKey) as? Bool ?? true
    }

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        if FirebaseApp.app() == nil,
           Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil {
            FirebaseApp.configure()
        }

        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self

        if Self.pushNotificationsEnabled {
            UNUserNotificationCenter.current().getNotificationSettings { settings in
                guard settings.authorizationStatus == .authorized ||
                      settings.authorizationStatus == .provisional else { return }
                DispatchQueue.main.async {
                    application.registerForRemoteNotifications()
                }
            }
        }

        authHandle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            self?.reconcilePushRegistration(for: user)
        }
        return true
    }

    deinit {
        if let authHandle {
            Auth.auth().removeStateDidChangeListener(authHandle)
        }
    }

    private func reconcilePushRegistration(for user: User?) {
        let defaults = UserDefaults.standard
        let previousUID = defaults.string(forKey: Self.registeredUIDKey)
        let previousToken = defaults.string(forKey: Self.registeredTokenKey)

        if let previousUID, let previousToken,
           (previousUID != user?.uid || !Self.pushNotificationsEnabled) {
            Self.delete(token: previousToken, uid: previousUID)
        }

        guard let user, Self.pushNotificationsEnabled else { return }
        Messaging.messaging().token { token, error in
            if let error {
                print("Pawly FCM token refresh failed: \(error.localizedDescription)")
                return
            }
            guard let token,
                  Auth.auth().currentUser?.uid == user.uid,
                  Self.pushNotificationsEnabled else { return }
            Self.save(token: token, uid: user.uid)
        }
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        guard Self.pushNotificationsEnabled else { return }
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("Pawly APNs registration failed: \(error.localizedDescription)")
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard Self.pushNotificationsEnabled,
              let token = fcmToken,
              let user = Auth.auth().currentUser else { return }
        Self.save(token: token, uid: user.uid)
    }

    private static func save(token: String, uid: String) {
        let defaults = UserDefaults.standard
        let previousUID = defaults.string(forKey: registeredUIDKey)
        let previousToken = defaults.string(forKey: registeredTokenKey)

        if let previousUID, let previousToken,
           (previousUID != uid || previousToken != token) {
            delete(token: previousToken, uid: previousUID)
        }

        let tokenID = token.replacingOccurrences(of: "/", with: "_")
        Firestore.firestore()
            .collection("users").document(uid)
            .collection("fcmTokens").document(tokenID)
            .setData([
                "token": token,
                "platform": "ios",
                "updatedAt": FieldValue.serverTimestamp()
            ], merge: true) { error in
                if let error {
                    print("Pawly FCM token could not be saved: \(error.localizedDescription)")
                    return
                }
                // The account or preference may have changed while the Firestore write was in flight.
                guard Auth.auth().currentUser?.uid == uid, pushNotificationsEnabled else {
                    delete(token: token, uid: uid)
                    return
                }
                defaults.set(uid, forKey: registeredUIDKey)
                defaults.set(token, forKey: registeredTokenKey)
            }
    }

    private static func delete(token: String, uid: String) {
        let tokenID = token.replacingOccurrences(of: "/", with: "_")
        Firestore.firestore()
            .collection("users").document(uid)
            .collection("fcmTokens").document(tokenID)
            .delete { error in
                if let error {
                    print("Pawly FCM token cleanup failed: \(error.localizedDescription)")
                    return
                }
                let defaults = UserDefaults.standard
                // Do not erase a newer token that may have replaced this one meanwhile.
                if defaults.string(forKey: registeredUIDKey) == uid,
                   defaults.string(forKey: registeredTokenKey) == token {
                    defaults.removeObject(forKey: registeredUIDKey)
                    defaults.removeObject(forKey: registeredTokenKey)
                }
            }
    }

    @MainActor
    static func registerForRemoteNotifications() {
        guard pushNotificationsEnabled else { return }
        UIApplication.shared.registerForRemoteNotifications()

        guard let user = Auth.auth().currentUser else { return }
        Messaging.messaging().token { token, error in
            guard let token,
                  error == nil,
                  Auth.auth().currentUser?.uid == user.uid,
                  pushNotificationsEnabled else { return }
            save(token: token, uid: user.uid)
        }
    }

    @MainActor
    static func disableRemoteNotifications() {
        let defaults = UserDefaults.standard
        defaults.set(false, forKey: pushPreferenceKey)
        UIApplication.shared.unregisterForRemoteNotifications()

        let uid = defaults.string(forKey: registeredUIDKey) ?? Auth.auth().currentUser?.uid
        let token = defaults.string(forKey: registeredTokenKey)
        if let uid, let token {
            // Keep the cached pair until Firestore confirms deletion so a later
            // app launch can retry cleanup after a transient network failure.
            delete(token: token, uid: uid)
        } else {
            defaults.removeObject(forKey: registeredUIDKey)
            defaults.removeObject(forKey: registeredTokenKey)
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let payload = response.notification.request.content.userInfo
        Task { @MainActor in
            PawlyPushRouter.shared.open(userInfo: payload)
        }
        completionHandler()
    }

    func application(
        _ application: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        PawlyGoogleSignIn.handle(url)
    }
}
