import Foundation
import CoreLocation
import Combine
import FirebaseAuth
import FirebaseFirestore

struct PawlyFeedPost: Identifiable {
    var id:String; var authorId:String; var authorName:String; var authorPhoto:String; var petName:String
    var text:String; var imageURL:String; var videoURL:String; var category:String
    var likeCount:Int; var commentCount:Int; var likedByMe:Bool; var createdAt:Date?
    var areaChanged = false
    var lostLatCell: Int?
    var lostLonCell: Int?
    var publishedPoint: CLLocationCoordinate2D? {
        guard !areaChanged, let lat = lostLatCell, let lon = lostLonCell, (-9000...9000).contains(lat), (-18000...18000).contains(lon) else { return nil }
        return .init(latitude: Double(lat)/100, longitude: Double(lon)/100)
    }
}
struct PawlyPublicUser:Identifiable {
    var id:String;var username:String;var displayName:String;var bio:String;var photoURL:String;var followersCount:Int;var following:Bool
}
struct PawlyLostPetSubscription {
    var enabled: Bool
    var radiusKm: Int
    var latitude: Double
    var longitude: Double
}
struct PawlyBlockedUser: Identifiable {
    var id: String
    var username: String
    var displayName: String
}
struct PawlyFeedComment: Identifiable {
    var id:String; var authorId:String; var authorName:String; var text:String; var createdAt:Date?
    var threadID:String? = nil
    var replyToName:String? = nil
    var replyCount:Int = 0
}
private extension PawlyFeedComment {
    init(_ document: DocumentSnapshot) {
        let data = document.data() ?? [:]
        self.init(id: document.documentID, authorId: data["authorId"] as? String ?? "",
                  authorName: data["authorName"] as? String ?? "Pawly", text: data["text"] as? String ?? "",
                  createdAt: (data["createdAt"] as? Timestamp)?.dateValue(), threadID: data["threadId"] as? String,
                  replyToName: data["replyToName"] as? String, replyCount: data["replyCount"] as? Int ?? 0)
    }
}
struct PawlyChat: Identifiable {
    var id:String
    var otherUserID:String
    var otherUserName:String
    var lastMessage:String
    var updatedAt:Date?
    var unreadCount:Int = 0
}
struct PawlyChatMessage: Identifiable {
    var sharedProfileUID: String? = nil
    var id:String; var senderID:String; var text:String; var imageURL:String; var videoURL:String; var readBy:[String]; var createdAt:Date?
}

@MainActor
final class PawlyCommunityStore: ObservableObject {
    @Published var posts:[PawlyFeedPost]=[]
    @Published var chats:[PawlyChat]=[]
    @Published var unreadMessageCounts:[String:Int]=[:]
    @Published var unreadMessageCount=0
    @Published var unreadNotificationCount=0
    @Published var statusMessage:String?
    private let db=Firestore.firestore()
    private let auth=Auth.auth()
    private var feedListener:ListenerRegistration?
    private var blockedFeedListener:ListenerRegistration?
    private var blockedByFeedListener:ListenerRegistration?
    private var chatsListener:ListenerRegistration?
    private var blockedChatsListener:ListenerRegistration?
    private var blockedByChatsListener:ListenerRegistration?
    private var notificationListener:ListenerRegistration?
    private var messageSubscription: UUID?
    private var messageListener:ListenerRegistration?
    private var pendingReadReceipts = Set<DocumentReference>()
    private var cachedFeed:[PawlyFeedPost]=[]
    private var blockedFeedIDs:Set<String>=[]
    private var blockedByFeedIDs:Set<String>=[]
    private var cachedChats:[PawlyChat]=[]
    private var blockedChatIDs:Set<String>=[]
    private var blockedByChatIDs:Set<String>=[]
    var uid:String? { auth.currentUser?.uid }
    var isVerified:Bool { auth.currentUser?.isEmailVerified == true }
    func requireVerified() throws {
        guard uid != nil else { throw PawlyCommunityError.notSignedIn }
        guard isVerified else { throw PawlyCommunityError.verifyEmail }
    }
    private func createActivityNotification(
        targetUID: String,
        type: String,
        relatedID: String = "",
        sourceEventID: String = ""
    ) async {
        guard let actorUID = uid, !targetUID.isEmpty, targetUID != actorUID else { return }

        let message: String
        let notificationID: String
        switch type {
        case "LIKE":
            message = "Le ha gustado tu publicación."
            notificationID = "LIKE-\(actorUID)-\(relatedID)"
        case "COMMENT":
            message = "Ha comentado tu publicación."
            notificationID = "COMMENT-\(actorUID)-\(sourceEventID)"
        case "FOLLOW":
            message = "Ha empezado a seguirte."
            notificationID = "FOLLOW-\(actorUID)-\(targetUID)"
        default:
            return
        }

        do {
            let actor = try await db.collection("users").document(actorUID).getDocument()
            let displayName = actor.get("displayName") as? String ?? ""
            let username = actor.get("username") as? String ?? ""
            let actorName = displayName.isEmpty ? (username.isEmpty ? "Usuario Pawly" : username) : displayName
            let notification = db.collection("users").document(targetUID)
                .collection("notifications").document(notificationID)
            try await notification.setData([
                "actorId": actorUID,
                "actorName": String(actorName.prefix(80)),
                "type": type,
                "message": message,
                "relatedId": String(relatedID.prefix(128)),
                "sourceEventId": String(sourceEventID.prefix(128)),
                "createdAt": FieldValue.serverTimestamp(),
                "read": false
            ])
            await PawlyPushDispatcher.send(
                type: type,
                recipientUID: targetUID,
                relatedID: String(relatedID.prefix(128)),
                sourceEventID: String(sourceEventID.prefix(128))
            )
        } catch {
            // The interaction is already committed. A notification failure is best-effort.
        }
    }
    private func refreshVisibleFeed() {
        let hiddenIDs = blockedFeedIDs.union(blockedByFeedIDs)
        feedPrivacyReady = receivedBlocked && receivedBlockedBy
        posts = feedPrivacyReady ? cachedFeed.filter { !hiddenIDs.contains($0.authorId) } : []
    }

    func loadPost(_ id:String) async throws -> PawlyFeedPost? {
        guard !id.isEmpty,id.count <= 128,let currentUID=uid else { return nil }
        let document=try await db.collection("posts").document(id).getDocument()
        guard document.exists,let data=document.data() else { return nil }
        let authorID=data["authorId"] as? String ?? ""
        guard !authorID.isEmpty else { return nil }
        async let blockedByMe=db.collection("users").document(currentUID).collection("blocked").document(authorID).getDocument()
        async let blockedAuthor=db.collection("users").document(authorID).collection("blocked").document(currentUID).getDocument()
        let (blocked,blockedBack)=try await (blockedByMe,blockedAuthor)
        guard !blocked.exists && !blockedBack.exists,
              !blockedFeedIDs.contains(authorID),
              !blockedByFeedIDs.contains(authorID) else { return nil }
        let likes=data["likedBy"] as? [String] ?? []
        return PawlyFeedPost(
            id:document.documentID,
            authorId:authorID,
            authorName:data["authorName"] as? String ?? "Usuario Pawly",
            authorPhoto:data["authorPhoto"] as? String ?? "",
            petName:data["dogName"] as? String ?? "",
            text:data["text"] as? String ?? "",
            imageURL:data["imageUrl"] as? String ?? "",
            videoURL:data["videoUrl"] as? String ?? "",
            category:data["category"] as? String ?? "STORY",
            likeCount:data["likeCount"] as? Int ?? 0,
            commentCount:data["commentCount"] as? Int ?? 0,
            likedByMe:likes.contains(currentUID),
            createdAt:(data["createdAt"] as? Timestamp)?.dateValue(), areaChanged: data["lostAreaEdited"] as? Bool ?? false,
            lostLatCell: data["lostLatCell"] as? Int, lostLonCell: data["lostLonCell"] as? Int
        )
    }

    var hiddenFeedAuthors: Set<String> { blockedFeedIDs.union(blockedByFeedIDs) }
    @Published private(set) var feedPrivacyReady = false
    private var receivedBlocked = false
    private var receivedBlockedBy = false
    static func feedPost(_ d: DocumentSnapshot, uid: String) -> PawlyFeedPost {
        let x = d.data() ?? [:], likes = x["likedBy"] as? [String] ?? []
        return PawlyFeedPost(id:d.documentID,authorId:x["authorId"] as? String ?? "",
            authorName:x["authorName"] as? String ?? "Usuario Pawly",authorPhoto:x["authorPhoto"] as? String ?? "",
            petName:x["dogName"] as? String ?? "",text:x["text"] as? String ?? "",
            imageURL:x["imageUrl"] as? String ?? "",videoURL:x["videoUrl"] as? String ?? "",
            category:x["category"] as? String ?? "STORY",likeCount:x["likeCount"] as? Int ?? 0,
            commentCount:x["commentCount"] as? Int ?? 0,likedByMe:likes.contains(uid),
            createdAt:(x["createdAt"] as? Timestamp)?.dateValue(), areaChanged: x["lostAreaEdited"] as? Bool ?? false,
            lostLatCell: x["lostLatCell"] as? Int, lostLonCell: x["lostLonCell"] as? Int)
    }
    func listenFeed() {
        guard let uid else { return }
        feedListener?.remove()
        blockedFeedListener?.remove()
        blockedByFeedListener?.remove()

        feedListener = db.collection("posts").order(by:"createdAt",descending:true).limit(to:50).addSnapshotListener { [weak self] snap,error in
            guard let self else { return }
            if let error { self.statusMessage = PawlyServiceMessage.key(for: error); return }
            self.cachedFeed = snap?.documents.compactMap { d in
                Self.feedPost(d, uid: uid)
            } ?? []
            self.refreshVisibleFeed()
        }
        blockedFeedListener = db.collection("users").document(uid).collection("blocked").addSnapshotListener(includeMetadataChanges: true) { [weak self] snap,error in
            guard let self, self.uid == uid else { return }
            if let error { self.receivedBlocked = false; self.refreshVisibleFeed(); self.statusMessage = PawlyServiceMessage.key(for: error); return }
            guard let snap, !snap.metadata.isFromCache else { return }
            self.receivedBlocked = true
            self.blockedFeedIDs = Set(snap.documents.map { $0.documentID })
            self.refreshVisibleFeed()
        }
        blockedByFeedListener = db.collection("users").document(uid).collection("blockedBy").addSnapshotListener(includeMetadataChanges: true) { [weak self] snap,error in
            guard let self, self.uid == uid else { return }
            if let error { self.receivedBlockedBy = false; self.refreshVisibleFeed(); self.statusMessage = PawlyServiceMessage.key(for: error); return }
            guard let snap, !snap.metadata.isFromCache else { return }
            self.receivedBlockedBy = true
            self.blockedByFeedIDs = Set(snap.documents.map { $0.documentID })
            self.refreshVisibleFeed()
        }
    }

    func stopFeed() {
        feedPrivacyReady = false; receivedBlocked = false; receivedBlockedBy = false; posts = []
        feedListener?.remove()
        blockedFeedListener?.remove()
        blockedByFeedListener?.remove()
        feedListener = nil
        blockedFeedListener = nil
        blockedByFeedListener = nil
    }
    func publish(text:String,petName:String,category:String,imageURL:String = "",videoURL:String = "", lostArea: CLLocationCoordinate2D? = nil) async throws -> String {
        guard auth.currentUser?.isAnonymous == false else { throw PawlyCommunityError.notSignedIn }
        let clean=text.trimmingCharacters(in:.whitespacesAndNewlines)
        guard clean.count<=2000,(!clean.isEmpty || !imageURL.isEmpty || !videoURL.isEmpty) else{throw PawlyCommunityError.invalidPost}
        guard let uid else{throw PawlyCommunityError.notSignedIn}
        let profile=try await db.collection("users").document(uid).getDocument()
        let reference = db.collection("posts").document()
        var data: [String: Any] = [
            "authorId":uid,"authorName":profile.get("displayName") as? String ?? profile.get("username") as? String ?? "Usuario Pawly",
            "authorPhoto":profile.get("photoUrl") as? String ?? "","dogName":String(petName.prefix(40)),
            "searchTokens":PawlyFeedFilter.tokens([clean, petName, profile.get("displayName") as? String ?? profile.get("username") as? String ?? "", category]),
            "category":category,"text":clean,"imageUrl":imageURL,"videoUrl":videoURL,"likeCount":0,"commentCount":0,
            "likedBy":[String](),"createdAt":FieldValue.serverTimestamp()
        ]
        if category == "LOST", let point = lostArea, CLLocationCoordinate2DIsValid(point) {
            data["lostLatCell"] = Int((point.latitude * 100).rounded())
            data["lostLonCell"] = Int((point.longitude * 100).rounded())
        }
        if isVerified { try await reference.setData(data) }
        else {
            if !imageURL.isEmpty { data["imageAssetId"] = PawlyCloudinaryUploader.indexID(for: imageURL) }
            if !videoURL.isEmpty { data["videoAssetId"] = PawlyCloudinaryUploader.indexID(for: videoURL) }
            let batch = db.batch()
            batch.setData(data, forDocument: reference)
            batch.setData(["postId": reference.documentID, "at": FieldValue.serverTimestamp()],
                          forDocument: db.collection("postPublishLimits").document(uid))
            try await batch.commit()
        }
        return reference.documentID
    }

    func editLostPost(_ original: PawlyFeedPost, text: String, pin: PawlyLostPinEdit,
                      media: (image: String, video: String)?) async throws -> PawlyFeedPost {
        try requireVerified()
        guard let uid, original.authorId == uid, original.category == "LOST" else { throw PawlyCommunityError.invalidPost }
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, clean.count <= 2000 else { throw PawlyCommunityError.invalidPost }
        let textEdited = clean != original.text
        let post = db.collection("posts").document(original.id)
        let alert = db.collection("lostPetAlerts").document(original.id)
        let result = try await db.runTransaction { transaction, failure -> Any? in
            do {
                let current = try transaction.getDocument(post)
                guard current.exists, current.get("authorId") as? String == uid, current.get("category") as? String == "LOST" else {
                    throw PawlyCommunityError.invalidPost
                }
                let currentText = current.get("text") as? String ?? ""
                if textEdited && currentText != original.text && currentText != clean { throw PawlyLostEditError.changed }
                var fields: [String: Any] = [:]
                if textEdited { fields["text"] = clean }
                if textEdited || current.get("searchTokens") == nil {
                    fields["searchTokens"] = PawlyFeedFilter.tokens([textEdited ? clean : currentText,
                        current.get("authorName") as? String ?? "", current.get("dogName") as? String ?? "", "LOST"])
                }
                if let media {
                    let oldImage = current.get("imageUrl") as? String ?? "", oldVideo = current.get("videoUrl") as? String ?? ""
                    guard (oldImage == original.imageURL && oldVideo == original.videoURL) ||
                            (oldImage == media.image && oldVideo == media.video) else { throw PawlyLostEditError.changed }
                    fields["imageUrl"] = media.image; fields["videoUrl"] = media.video
                    let url = media.image.isEmpty ? media.video : media.image
                    fields["lostMediaAssetId"] = url.isEmpty ? FieldValue.delete() as Any : PawlyCloudinaryUploader.indexID(for: url)
                }
                if pin != .keep {
                    let priorLat = current.get("lostLatCell") as? Int, priorLon = current.get("lostLonCell") as? Int
                    let invalidated = current.get("lostAreaEdited") as? Bool ?? false
                    let samePin = priorLat == original.lostLatCell && priorLon == original.lostLonCell && invalidated == original.areaChanged
                    guard (samePin && PawlyLostNoticeText.area(in: currentText) == original.publishedArea) ||
                          (pin.matches(priorLat, priorLon, invalidated: invalidated) && PawlyLostNoticeText.area(in: currentText) == PawlyLostNoticeText.area(in: clean))
                    else { throw PawlyLostEditError.changed }
                    let existing = try transaction.getDocument(alert)
                    let latitude: Any = pin.latitude.map { $0 as Any } ?? FieldValue.delete()
                    let longitude: Any = pin.longitude.map { $0 as Any } ?? FieldValue.delete()
                    fields["lostLatCell"] = latitude
                    fields["lostLonCell"] = longitude
                    fields["lostAreaEdited"] = pin == .clear
                    if existing.get("status") as? String == "active" {
                        var change: [String: Any] = ["latCell": latitude, "lonCell": longitude]
                        if pin == .clear { change["status"] = "paused" }
                        transaction.updateData(change, forDocument: alert)
                    }
                }
                if !fields.isEmpty { transaction.updateData(fields, forDocument: post) }
                return current
            } catch { failure?.pointee = error as NSError; return nil }
        }
        guard let snapshot = result as? DocumentSnapshot else { throw PawlyCommunityError.invalidPost }
        var updated = Self.feedPost(snapshot, uid: uid)
        if textEdited { updated.text = clean }
        if let media { updated.imageURL = media.image; updated.videoURL = media.video }
        if pin != .keep {
            updated.areaChanged = pin == .clear
            updated.lostLatCell = pin.latitude; updated.lostLonCell = pin.longitude
        }
        return updated
    }

    func loadLostPetSubscription() async throws -> PawlyLostPetSubscription? {
        guard let uid else { throw PawlyCommunityError.notSignedIn }
        let document = try await db.collection("lostPetSubscriptions").document(uid).getDocument()
        guard document.exists,
              let latCell = document.get("latCell") as? NSNumber,
              let lonCell = document.get("lonCell") as? NSNumber else { return nil }
        return PawlyLostPetSubscription(
            enabled: document.get("enabled") as? Bool ?? false,
            radiusKm: document.get("radiusKm") as? Int ?? 10,
            latitude: latCell.doubleValue / 100,
            longitude: lonCell.doubleValue / 100
        )
    }

    func disableLostPetSubscription() async throws {
        guard let uid else { throw PawlyCommunityError.notSignedIn }
        try await db.collection("lostPetSubscriptions").document(uid).delete()
    }

    func saveLostPetSubscription(
        enabled: Bool,
        radiusKm: Int,
        latitude: Double,
        longitude: Double
    ) async throws {
        guard [5, 10, 20, 30, 50, 100].contains(radiusKm),
              (-90...90).contains(latitude), (-180...180).contains(longitude) else {
            throw PawlyLostPetError.invalidSubscription
        }
        guard let uid else { throw PawlyCommunityError.notSignedIn }
        try await db.collection("lostPetSubscriptions").document(uid).setData([
            "uid": uid,
            "enabled": enabled,
            "radiusKm": radiusKm,
            "latCell": Int((latitude * 100).rounded()),
            "lonCell": Int((longitude * 100).rounded()),
            "updatedAt": FieldValue.serverTimestamp()
        ])
    }

    func publishLostPetAlert(postID: String, dogName: String, coordinate: CLLocationCoordinate2D) async throws -> Int {
        try requireVerified()
        guard let uid else { throw PawlyCommunityError.notSignedIn }
        guard (-90...90).contains(coordinate.latitude), (-180...180).contains(coordinate.longitude),
              !postID.isEmpty, postID.count <= 128 else {
            throw PawlyLostPetError.invalidLocation
        }
        try await db.collection("lostPetAlerts").document(postID).setData([
            "postId": postID,
            "authorUid": uid,
            "dogName": String(dogName.prefix(40)),
            "latCell": Int((coordinate.latitude * 100).rounded()),
            "lonCell": Int((coordinate.longitude * 100).rounded()),
            "status": "active",
            "createdAt": FieldValue.serverTimestamp()
        ])
        return try await PawlyBackendClient.dispatchLostPetAlert(alertID: postID)
    }
    func toggleLike(_ post:PawlyFeedPost) async throws {
        try requireVerified()
        guard let uid else { throw PawlyCommunityError.notSignedIn }
        let ref = db.collection("posts").document(post.id)
        if post.likedByMe {
            try await ref.updateData([
                "likedBy": FieldValue.arrayRemove([uid]),
                "likeCount": FieldValue.increment(Int64(-1))
            ])
        } else {
            try await ref.updateData([
                "likedBy": FieldValue.arrayUnion([uid]),
                "likeCount": FieldValue.increment(Int64(1))
            ])
            await createActivityNotification(targetUID: post.authorId, type: "LIKE",
                                             relatedID: post.id, sourceEventID: post.id)
        }
    }
    func listenComments(postID: String, threadID: String? = nil, limit: Int,
                        onUpdate: @escaping (Result<([PawlyFeedComment], Bool), Error>) -> Void) -> ListenerRegistration {
        var query: Query = db.collection("posts").document(postID).collection("comments")
        if let threadID { query = query.whereField("threadId", isEqualTo: threadID) }
        return query.order(by: "createdAt").limit(to: limit + 1).addSnapshotListener { snapshot, error in
            if let error { onUpdate(.failure(error)); return }
            guard let docs = snapshot?.documents else { return }
            onUpdate(.success((docs.prefix(limit).map { PawlyFeedComment($0) }, docs.count > limit)))
        }
    }
    func comment(postID: String, id: String) async throws -> PawlyFeedComment? {
        let document = try await db.collection("posts").document(postID).collection("comments").document(id).getDocument()
        return document.exists ? PawlyFeedComment(document) : nil
    }
    func addComment(postID:String,text:String, replyingTo target: PawlyFeedComment? = nil) async throws {
        try requireVerified()
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, clean.count <= 1000, let uid else {
            throw PawlyCommunityError.invalidComment
        }
        let profile = try await db.collection("users").document(uid).getDocument()
        let post = db.collection("posts").document(postID)
        let postSnapshot = try await post.getDocument()
        guard let postAuthorID = postSnapshot.get("authorId") as? String else {
            throw PawlyCommunityError.userNotFound
        }
        let comment = post.collection("comments").document()
        let batch = db.batch()
        var fields: [String: Any] = [
            "authorId": uid, "authorName": profile.get("displayName") as? String ?? "Usuario Pawly",
            "text": clean, "createdAt": FieldValue.serverTimestamp()
        ]
        if let target {
            let root = target.threadID ?? target.id
            fields["threadId"] = root
            fields["replyToId"] = target.id
            fields["replyToAuthorId"] = target.authorId
            fields["replyToName"] = target.authorName
            batch.updateData(["replyCount": FieldValue.increment(Int64(1)), "lastReplyId": comment.documentID],
                             forDocument: post.collection("comments").document(root))
        }
        batch.setData(fields, forDocument: comment)
        batch.updateData([
            "commentCount": FieldValue.increment(Int64(1)),
            "lastCommentId": comment.documentID
        ], forDocument: post)
        if let target, target.authorId != uid {
            let name = [profile.get("displayName"), profile.get("username")].compactMap { $0 as? String }
                .first { !$0.isEmpty && $0.count <= 80 }
            if let name {
                let inbox = db.collection("users").document(target.authorId).collection("notifications")
                let notice = inbox.document("COMMENT-\(uid)-\(comment.documentID)")
                batch.setData(["actorId": uid, "actorName": name, "type": "COMMENT",
                               "message": "Ha respondido a tu comentario.", "relatedId": postID,
                               "sourceEventId": comment.documentID, "createdAt": FieldValue.serverTimestamp(), "read": false],
                              forDocument: notice)
            }
        }
        try await batch.commit()
        if let target {
            if target.authorId != uid {
                await PawlyPushDispatcher.send(type: "COMMENT", recipientUID: target.authorId, relatedID: postID, sourceEventID: comment.documentID)
            }
        } else {
            await createActivityNotification(targetUID: postAuthorID, type: "COMMENT", relatedID: postID, sourceEventID: comment.documentID)
        }
    }
    func publicUser(_ id: String) async throws -> PawlyPublicUser? {
        guard let me = uid, !id.isEmpty else { throw PawlyCommunityError.notSignedIn }
        let document = try await db.collection("users").document(id).getDocument()
        guard let fields = document.data(), fields["isPublic"] as? Bool == true || id == me else { return nil }
        let following = try await db.collection("users").document(me).collection("following").document(id).getDocument()
        return publicUser(id: id, fields: fields, following: following.exists)
    }

    private func publicUser(id: String, fields: [String: Any], following: Bool) -> PawlyPublicUser {
        let username = fields["username"] as? String ?? ""
        return PawlyPublicUser(id: id, username: username, displayName: fields["displayName"] as? String ?? username,
            bio: fields["bio"] as? String ?? "", photoURL: fields["photoUrl"] as? String ?? "",
            followersCount: fields["followersCount"] as? Int ?? 0, following: following)
    }

    func searchUsers(_ query:String) async throws -> [PawlyPublicUser] {
        guard let me=uid else{throw PawlyCommunityError.notSignedIn}
        let clean=query.trimmingCharacters(in:.whitespacesAndNewlines).replacingOccurrences(of:"@",with:"").lowercased()
        guard clean.count>=2 else{return []}
        let docs=try await db.collection("users").whereField("isPublic",isEqualTo:true).order(by:"usernameLower")
            .start(at:[clean]).end(at:[clean+"\u{f8ff}"]).limit(to:20).getDocuments()
        let blockedDocs = try await db.collection("users").document(me).collection("blocked").getDocuments()
        let blockedByDocs = try await db.collection("users").document(me).collection("blockedBy").getDocuments()
        let blockedIDs = Set(blockedDocs.documents.map { $0.documentID })
            .union(blockedByDocs.documents.map { $0.documentID })
        var results:[PawlyPublicUser]=[]
        for d in docs.documents where d.documentID != me && !blockedIDs.contains(d.documentID) {
            let x=d.data(), following=try await db.collection("users").document(me).collection("following").document(d.documentID).getDocument()
            results.append(publicUser(id: d.documentID, fields: x, following: following.exists))
        }
        return results
    }
    func setFollowing(_ user:PawlyPublicUser,follow shouldFollow:Bool) async throws {
        try requireVerified()
        guard let me=uid,me != user.id else{throw PawlyCommunityError.notParticipant}
        let myFollowing=db.collection("users").document(me).collection("following").document(user.id)
        let theirFollower=db.collection("users").document(user.id).collection("followers").document(me)
        let myProfile=db.collection("users").document(me),theirProfile=db.collection("users").document(user.id)
        let existing=try await myFollowing.getDocument()
        if existing.exists == shouldFollow{return}
        let myBlock=try await db.collection("users").document(me).collection("blocked").document(user.id).getDocument()
        let theirBlock=try await db.collection("users").document(user.id).collection("blocked").document(me).getDocument()
        guard !myBlock.exists && !theirBlock.exists else{throw PawlyCommunityError.blocked}
        let batch=db.batch()
        if shouldFollow {
            batch.setData(["uid":user.id,"createdAt":FieldValue.serverTimestamp()],forDocument:myFollowing)
            batch.setData(["uid":me,"createdAt":FieldValue.serverTimestamp()],forDocument:theirFollower)
            batch.updateData([
                "followingCount":FieldValue.increment(Int64(1)),
                "lastFollowingChangeUid":user.id
            ],forDocument:myProfile)
            batch.updateData(["followersCount":FieldValue.increment(Int64(1))],forDocument:theirProfile)
        } else {
            batch.deleteDocument(myFollowing);batch.deleteDocument(theirFollower)
            batch.updateData([
                "followingCount":FieldValue.increment(Int64(-1)),
                "lastFollowingChangeUid":user.id
            ],forDocument:myProfile)
            batch.updateData(["followersCount":FieldValue.increment(Int64(-1))],forDocument:theirProfile)
        }
        try await batch.commit()
        if shouldFollow {
            await createActivityNotification(targetUID: user.id, type: "FOLLOW")
        }
    }
    func blockUser(_ user:PawlyPublicUser) async throws {
        try requireVerified()
        guard let me = uid, me != user.id else { throw PawlyCommunityError.notParticipant }

        let meRef = db.collection("users").document(me)
        let otherRef = db.collection("users").document(user.id)
        let myFollowing = meRef.collection("following").document(user.id)
        let theirFollowing = otherRef.collection("following").document(me)
        let myFollower = meRef.collection("followers").document(user.id)
        let theirFollower = otherRef.collection("followers").document(me)
        let myBlocked = meRef.collection("blocked").document(user.id)
        let theirBlockedBy = otherRef.collection("blockedBy").document(me)

        // Read relationship state first, then apply the privacy change and remove
        // both sides of any follow relationship in one atomic batch.
        let myFollowingSnapshot = try await myFollowing.getDocument()
        let theirFollowingSnapshot = try await theirFollowing.getDocument()
        let iFollow = myFollowingSnapshot.exists
        let theyFollow = theirFollowingSnapshot.exists

        let batch = db.batch()
        batch.setData([
            "uid": user.id,
            "username": user.username,
            "displayName": user.displayName,
            "createdAt": FieldValue.serverTimestamp()
        ], forDocument: myBlocked)
        batch.setData([
            "uid": me,
            "createdAt": FieldValue.serverTimestamp()
        ], forDocument: theirBlockedBy)

        var otherProfileChanges: [String: Any] = [:]
        var ownChanges: [String: Any] = [:]
        if iFollow {
            batch.deleteDocument(myFollowing)
            batch.deleteDocument(theirFollower)
            ownChanges["followingCount"] = FieldValue.increment(Int64(-1))
            ownChanges["lastFollowingChangeUid"] = user.id
            otherProfileChanges["followersCount"] = FieldValue.increment(Int64(-1))
        }
        if theyFollow {
            batch.deleteDocument(theirFollowing)
            batch.deleteDocument(myFollower)
            otherProfileChanges["followingCount"] = FieldValue.increment(Int64(-1))
            ownChanges["followersCount"] = FieldValue.increment(Int64(-1))
            ownChanges["lastBlockedUid"] = user.id
        }
        if !otherProfileChanges.isEmpty {
            batch.updateData(otherProfileChanges, forDocument: otherRef)
        }
        if !ownChanges.isEmpty { batch.updateData(ownChanges, forDocument: meRef) }
        try await batch.commit()
    }

    func unblockUser(_ userID: String) async throws {
        guard let me = uid, me != userID else { throw PawlyCommunityError.notParticipant }
        let batch = db.batch()
        batch.deleteDocument(db.collection("users").document(me).collection("blocked").document(userID))
        batch.deleteDocument(db.collection("users").document(userID).collection("blockedBy").document(me))
        try await batch.commit()
    }

    func loadBlockedUsers() async throws -> [PawlyBlockedUser] {
        guard let me = uid else { throw PawlyCommunityError.notSignedIn }
        let snapshot = try await db.collection("users").document(me).collection("blocked").getDocuments()
        return snapshot.documents.map { document in
            PawlyBlockedUser(
                id: document.documentID,
                username: document.get("username") as? String ?? "",
                displayName: document.get("displayName") as? String ?? "Usuario"
            )
        }.sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
    }
    func reportUser(_ user:PawlyPublicUser,reason:String) async throws {
        try requireVerified()
        guard let me=uid else{throw PawlyCommunityError.notSignedIn}
        let clean=reason.trimmingCharacters(in:.whitespacesAndNewlines)
        guard !clean.isEmpty,clean.count<=500 else{throw PawlyCommunityError.invalidComment}
        try await db.collection("reports").addDocument(data:[
            "reporterId":me,"targetId":user.id,"targetType":"user",
            "reason":clean,"status":"open","createdAt":FieldValue.serverTimestamp()
        ])
    }

    func startConversation(username:String) async throws -> String {
        try requireVerified()
        guard let me=uid else{throw PawlyCommunityError.notSignedIn}
        let clean=username.trimmingCharacters(in:.whitespacesAndNewlines).replacingOccurrences(of:"@",with:"").lowercased()
        let usernameDoc=try await db.collection("usernames").document(clean).getDocument()
        guard let other=usernameDoc.get("uid") as? String,other != me else{throw PawlyCommunityError.userNotFound}
        return try await startConversation(userID: other)
    }
    func startConversation(userID other: String) async throws -> String {
        try requireVerified()
        guard let me = uid, other != me, !other.isEmpty else { throw PawlyCommunityError.userNotFound }
        let otherProfile=try await db.collection("users").document(other).getDocument()
        guard otherProfile.exists,otherProfile.get("isPublic") as? Bool != false else{throw PawlyCommunityError.userNotFound}
        let a=try await db.collection("users").document(me).collection("blocked").document(other).getDocument()
        let b=try await db.collection("users").document(other).collection("blocked").document(me).getDocument()
        guard !a.exists,!b.exists else{throw PawlyCommunityError.blocked}
        let ids=[me,other].sorted(), cid=ids.joined(separator:"_"), ref=db.collection("conversations").document(cid)
        let existing=try await ref.getDocument()
        if !existing.exists {
            let mine = try await db.collection("users").document(me).getDocument()
            let myName = mine.get("displayName") as? String ?? ""
            let otherName = otherProfile.get("displayName") as? String ?? ""
            let myPhoto = mine.get("photoUrl") as? String ?? ""
            let otherPhoto = otherProfile.get("photoUrl") as? String ?? ""
            guard !myName.isEmpty, !otherName.isEmpty else { throw PawlyCommunityError.userNotFound }
            try await ref.setData([
                "participants": ids,
                "participantNames": [me: myName, other: otherName],
                "participantPhotos": [me: myPhoto, other: otherPhoto],
                "updatedAt": FieldValue.serverTimestamp()
            ])
        }
        return cid
    }
    private func refreshVisibleChats() {
        let hiddenIDs = blockedChatIDs.union(blockedByChatIDs)
        let visibleChats = cachedChats.filter { !hiddenIDs.contains($0.otherUserID) }
        let visibleIDs = Set(visibleChats.map(\.id))
        unreadMessageCounts = unreadMessageCounts.filter { visibleIDs.contains($0.key) }
        unreadMessageCount = unreadMessageCounts.values.reduce(0, +)
        chats = visibleChats.map { chat in
            var updated = chat
            updated.unreadCount = unreadMessageCounts[chat.id] ?? 0
            return updated
        }
    }

    func refreshUnreadMessageCounts() async {
        guard let uid else {
            unreadMessageCounts = [:]
            unreadMessageCount = 0
            refreshVisibleChats()
            return
        }
        let hiddenIDs = blockedChatIDs.union(blockedByChatIDs)
        var updatedCounts:[String:Int] = [:]
        for chat in cachedChats where !hiddenIDs.contains(chat.otherUserID) {
            do {
                let docs = try await db.collection("conversations").document(chat.id)
                    .collection("messages")
                    .whereField("senderId", isEqualTo: chat.otherUserID)
                    .order(by: "createdAt", descending: true)
                    .limit(to: 100)
                    .getDocuments()
                updatedCounts[chat.id] = docs.documents.filter { document in
                    let readBy = document.get("readBy") as? [String] ?? []
                    return !readBy.contains(uid)
                }.count
            } catch {
                updatedCounts[chat.id] = unreadMessageCounts[chat.id] ?? 0
                statusMessage = error.localizedDescription
            }
        }
        unreadMessageCounts = updatedCounts
        unreadMessageCount = updatedCounts.values.reduce(0, +)
        refreshVisibleChats()
    }

    func listenNotifications() {
        guard let uid else { return }
        notificationListener?.remove()
        notificationListener = db.collection("users").document(uid)
            .collection("notifications")
            .whereField("read", isEqualTo: false)
            .limit(to: 60)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let self else { return }
                if let error {
                    self.statusMessage = PawlyServiceMessage.key(for: error)
                    return
                }
                self.unreadNotificationCount = snapshot?.documents.count ?? 0
            }
    }

    func stopNotifications() {
        notificationListener?.remove()
        notificationListener = nil
    }

    func listenChats() {
        guard let uid else { return }
        chatsListener?.remove()
        blockedChatsListener?.remove()
        blockedByChatsListener?.remove()
        chatsListener=db.collection("conversations").whereField("participants",arrayContains:uid).addSnapshotListener { [weak self] snap,error in
            guard let self else{return};if let error{self.statusMessage=PawlyServiceMessage.key(for: error);return}
            self.cachedChats = (snap?.documents.compactMap { d -> PawlyChat? in
                let x=d.data()
                let participants=x["participants"] as? [String] ?? []
                guard let other=participants.first(where: { $0 != uid }) else { return nil }
                let names=x["participantNames"] as? [String:String] ?? [:]
                return PawlyChat(
                    id:d.documentID,
                    otherUserID:other,
                    otherUserName:names[other] ?? "Usuario Pawly",
                    lastMessage:x["lastMessage"] as? String ?? "",
                    updatedAt:(x["updatedAt"] as? Timestamp)?.dateValue()
                )
            } ?? []).sorted {
                ($0.updatedAt ?? .distantPast) > ($1.updatedAt ?? .distantPast)
            }
            self.refreshVisibleChats()
            Task { await self.refreshUnreadMessageCounts() }
        }
        blockedChatsListener = db.collection("users").document(uid).collection("blocked").addSnapshotListener { [weak self] snap,error in
            guard let self else { return }
            if let error { self.statusMessage = PawlyServiceMessage.key(for: error); return }
            self.blockedChatIDs = Set(snap?.documents.map { $0.documentID } ?? [])
            self.refreshVisibleChats()
            Task { await self.refreshUnreadMessageCounts() }
        }
        blockedByChatsListener = db.collection("users").document(uid).collection("blockedBy").addSnapshotListener { [weak self] snap,error in
            guard let self else { return }
            if let error { self.statusMessage = PawlyServiceMessage.key(for: error); return }
            self.blockedByChatIDs = Set(snap?.documents.map { $0.documentID } ?? [])
            self.refreshVisibleChats()
            Task { await self.refreshUnreadMessageCounts() }
        }
    }
    func listenMessages(conversationID: String, limit: Int,
                        onUpdate: @escaping (Result<([PawlyChatMessage], Bool), Error>) -> Void) {
        stopMessages()
        guard let uid else { onUpdate(.failure(PawlyCommunityError.notSignedIn)); return }
        let subscription = UUID()
        messageSubscription = subscription
        messageListener = db.collection("conversations").document(conversationID).collection("messages")
            .order(by: "createdAt", descending: true).limit(to: limit + 1).addSnapshotListener { [weak self] snapshot, error in
                guard let self, self.messageSubscription == subscription else { return }
                guard self.uid == uid else { onUpdate(.failure(PawlyCommunityError.notSignedIn)); return }
                if let error { onUpdate(.failure(error)); return }
                guard let snapshot else { return }
                let documents = snapshot.documents
                let messages = documents.prefix(limit).reversed().map { document in
                    let fields = document.data()
                    return PawlyChatMessage(sharedProfileUID: fields["sharedProfileUid"] as? String, id: document.documentID, senderID: fields["senderId"] as? String ?? "",
                        text: fields["text"] as? String ?? "", imageURL: fields["imageUrl"] as? String ?? "",
                        videoURL: fields["videoUrl"] as? String ?? "", readBy: fields["readBy"] as? [String] ?? [],
                        createdAt: (fields["createdAt"] as? Timestamp)?.dateValue())
                }
                onUpdate(.success((messages, documents.count > limit)))
            }
    }
    func sendMessage(conversationID:String,text:String,imageURL:String = "",videoURL:String = "", sharedProfileUID: String? = nil, messageID: String? = nil) async throws {
        try requireVerified();guard let uid else{throw PawlyCommunityError.notSignedIn}
        let clean=text.trimmingCharacters(in:.whitespacesAndNewlines)
        guard clean.count<=2000,(!clean.isEmpty || !imageURL.isEmpty || !videoURL.isEmpty),imageURL.isEmpty || videoURL.isEmpty else{throw PawlyCommunityError.invalidMessage}
        let ref=db.collection("conversations").document(conversationID), conv=try await ref.getDocument()
        let participants=conv.get("participants") as? [String] ?? []
        guard participants.contains(uid),participants.count==2 else{throw PawlyCommunityError.notParticipant}
        let other=participants.first(where:{$0 != uid})!
        let a=try await db.collection("users").document(uid).collection("blocked").document(other).getDocument()
        let b=try await db.collection("users").document(other).collection("blocked").document(uid).getDocument()
        guard !a.exists,!b.exists else{throw PawlyCommunityError.blocked}
        let messages = ref.collection("messages")
        let msg = messageID.map { messages.document($0) } ?? messages.document()
        if messageID != nil {
            let previous = try await msg.getDocument()
            if previous.exists {
                guard previous.get("senderId") as? String == uid, previous.get("sharedProfileUid") as? String == sharedProfileUID else { throw PawlyCommunityError.invalidMessage }
                return
            }
        }
        var fields: [String: Any] = ["senderId":uid,"text":clean,"imageUrl":imageURL,"videoUrl":videoURL,"readBy":[uid],"createdAt":FieldValue.serverTimestamp()]
        if let sharedProfileUID { fields["sharedProfileUid"] = sharedProfileUID }
        let batch = db.batch()
        batch.setData(fields, forDocument: msg)
        let preview = clean.isEmpty ? (videoURL.isEmpty ? "📷 Fotografía" : "🎥 Vídeo") : String(clean.prefix(120))
        batch.updateData(["lastMessage":preview,"updatedAt":FieldValue.serverTimestamp()],forDocument:ref)
        try await batch.commit()
        Task {
            await PawlyPushDispatcher.send(
                type: "MESSAGE",
                recipientUID: other,
                relatedID: conversationID,
                sourceEventID: msg.documentID
            )
        }
    }
    func markRead(conversationID: String, messages: [PawlyChatMessage]) async {
        guard let uid, isVerified else { return }
        let collection = db.collection("conversations").document(conversationID).collection("messages")
        let pending = messages.filter { $0.senderID != uid && !$0.readBy.contains(uid) }
            .map { collection.document($0.id) }.filter { !pendingReadReceipts.contains($0) }
        guard !pending.isEmpty else { return }
        pendingReadReceipts.formUnion(pending)
        defer { pendingReadReceipts.subtract(pending) }
        do {
            for offset in stride(from: 0, to: pending.count, by: 400) {
                let group = pending[offset..<min(offset + 400, pending.count)]
                _ = try await db.runTransaction { transaction, failure -> Any? in
                    do {
                        // Another device may have acknowledged a message since this snapshot.
                        let snapshots = try group.map { try transaction.getDocument($0) }
                        for document in snapshots where document.exists && !((document.get("readBy") as? [String] ?? []).contains(uid)) {
                            transaction.updateData(["readBy": FieldValue.arrayUnion([uid])], forDocument: document.reference)
                        }
                    } catch { failure?.pointee = error as NSError }
                    return nil
                }
            }
            await refreshUnreadMessageCounts()
        } catch { statusMessage = PawlyServiceMessage.key(for: error) }
    }
    func stopChats() {
        chatsListener?.remove()
        blockedChatsListener?.remove()
        blockedByChatsListener?.remove()
        chatsListener = nil
        blockedChatsListener = nil
        blockedByChatsListener = nil
        stopNotifications()
    }
    func stopMessages() {
        messageSubscription = nil
        messageListener?.remove()
        messageListener = nil
    }
}
private extension String { var nilIfEmpty:String? { isEmpty ? nil : self } }
enum PawlyCommunityError:LocalizedError {
    case notSignedIn,verifyEmail,invalidPost,invalidComment,invalidMessage,userNotFound,blocked,notParticipant
    var errorDescription:String? {
        switch self {
        case .notSignedIn:return "Inicia sesión para continuar."
        case .verifyEmail:return "Verifica tu correo electrónico para utilizar esta función."
        case .invalidPost:return "Escribe una publicación de hasta 2000 caracteres."
        case .invalidComment:return "El comentario debe tener entre 1 y 1000 caracteres."
        case .invalidMessage:return "Escribe un mensaje de hasta 2000 caracteres."
        case .userNotFound:return "No se ha encontrado ese usuario público de Pawly."
        case .blocked:return "No puedes contactar con una cuenta bloqueada."
        case .notParticipant:return "No formas parte de esta conversación."
        }
    }
}

enum PawlyLostPetError: LocalizedError {
    case invalidSubscription
    case invalidLocation
    var errorDescription: String? {
        switch self {
        case .invalidSubscription: return "La configuración de alertas o el radio seleccionado no son válidos."
        case .invalidLocation: return "No se ha podido validar la ubicación aproximada del aviso."
        }
    }
}
