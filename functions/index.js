const {onDocumentCreated}=require("firebase-functions/v2/firestore");
const {defineSecret}=require("firebase-functions/params");
const {initializeApp}=require("firebase-admin/app");
const {getFirestore,FieldValue}=require("firebase-admin/firestore");
const {getAuth}=require("firebase-admin/auth");
const {getMessaging}=require("firebase-admin/messaging");
initializeApp();
const db=getFirestore();
const cloudName=defineSecret("CLOUDINARY_CLOUD_NAME"),cloudKey=defineSecret("CLOUDINARY_API_KEY"),cloudSecret=defineSecret("CLOUDINARY_API_SECRET");

async function notify(uid,title,body,data={}){
 const snap=await db.collection("users").doc(uid).collection("fcmTokens").get();
 const tokens=snap.docs.map(d=>d.get("token")).filter(Boolean);if(!tokens.length)return;
 const result=await getMessaging().sendEachForMulticast({tokens,notification:{title,body},data:Object.fromEntries(Object.entries(data).map(([k,v])=>[k,String(v)])),android:{priority:"high"},apns:{payload:{aps:{sound:"default"}}}});
 const stale=[];result.responses.forEach((r,i)=>{if(!r.success&&["messaging/registration-token-not-registered","messaging/invalid-registration-token"].includes(r.error?.code)){const doc=snap.docs.find(d=>d.get("token")===tokens[i]);if(doc)stale.push(doc.ref)}});
 await Promise.all(stale.map(ref=>ref.delete()));
}
exports.notifyNewMessage=onDocumentCreated("conversations/{conversationId}/messages/{messageId}",async event=>{
 const msg=event.data?.data();if(!msg)return;
 const conv=await db.collection("conversations").doc(event.params.conversationId).get();
 const participants=conv.get("participants")||[],recipient=participants.find(uid=>uid!==msg.senderId);if(!recipient)return;
 const name=conv.get("participantNames")?.[msg.senderId]||"Alguien de Pawly";
 const preview=msg.text||(msg.videoUrl?"Te ha enviado un vídeo":"Te ha enviado una fotografía");
 await notify(recipient,name,preview.slice(0,140),{type:"message",conversationId:event.params.conversationId});
});
exports.notifyFollowers=onDocumentCreated("posts/{postId}",async event=>{
 const post=event.data?.data();if(!post?.authorId)return;
 const followers=await db.collection("users").doc(post.authorId).collection("followers").limit(500).get();
 const name=post.authorName||"Una persona de Pawly";
 await Promise.all(followers.docs.map(d=>notify(d.id,name,"Ha compartido una nueva historia",{type:"post",postId:event.params.postId})));
});
async function deleteCloudinary(uid,name,key,secret){
 const auth="Basic "+Buffer.from(key+":"+secret).toString("base64");
 const folders=["profile-photos","dog-photos","pet-photos","post-photos","post-videos","message-photos","message-videos"];
 for(const folder of folders){
  const prefix="pawly/"+folder+"/"+uid;
  for(const resource of ["image","video"]){
   const list=await fetch("https://api.cloudinary.com/v1_1/"+name+"/resources/"+resource+"/upload?prefix="+encodeURIComponent(prefix)+"&max_results=500",{headers:{Authorization:auth}});
   if(!list.ok)throw new Error("Cloudinary listing failed: "+list.status);
   const json=await list.json(),ids=(json.resources||[]).map(x=>x.public_id).filter(Boolean);
   for(let i=0;i<ids.length;i+=100){
    const form=new URLSearchParams();ids.slice(i,i+100).forEach(id=>form.append("public_ids[]",id));
    const del=await fetch("https://api.cloudinary.com/v1_1/"+name+"/resources/"+resource+"/upload",{method:"DELETE",headers:{Authorization:auth,"Content-Type":"application/x-www-form-urlencoded"},body:form});
    if(!del.ok)throw new Error("Cloudinary delete failed: "+del.status);
   }
  }
 }
}
async function deleteQuery(query){const snap=await query.get();for(const d of snap.docs)await db.recursiveDelete(d.ref);}
exports.processAccountDeletion=onDocumentCreated({document:"accountDeletionRequests/{requestId}",secrets:[cloudName,cloudKey,cloudSecret],timeoutSeconds:540,memory:"1GiB"},async event=>{
 const ref=event.data?.ref,request=event.data?.data();if(!ref||!request?.uid||request.status!=="pending")return;
 const uid=request.uid;await ref.update({status:"processing",processingAt:FieldValue.serverTimestamp()});
 try{
  const userRef=db.collection("users").doc(uid),profile=await userRef.get(),username=profile.get("usernameLower");
  const shared=await db.collection("sharedPets").where("ownerUid","==",uid).get();for(const d of shared.docs)await db.recursiveDelete(d.ref);
  const sent=await db.collection("petInvitations").where("ownerUid","==",uid).get(),received=await db.collection("petInvitations").where("inviteeUid","==",uid).get();
  for(const d of [...sent.docs,...received.docs])await d.ref.delete();
  await deleteQuery(db.collection("posts").where("authorId","==",uid));
  const comments=await db.collectionGroup("comments").where("authorId","==",uid).get();for(const d of comments.docs)await d.ref.delete();
  const chats=await db.collection("conversations").where("participants","array-contains",uid).get();for(const d of chats.docs)await db.recursiveDelete(d.ref);
  const users=await db.collection("users").get();
  for(const person of users.docs){if(person.id===uid)continue;for(const sub of ["followers","following","blocked"])await person.ref.collection(sub).doc(uid).delete().catch(()=>{});}
  await deleteQuery(db.collection("reports").where("reporterId","==",uid));
  if(username)await db.collection("usernames").doc(username).delete().catch(()=>{});
  await db.recursiveDelete(userRef);
  const name=cloudName.value(),key=cloudKey.value(),secret=cloudSecret.value();
  if(name&&key&&secret)await deleteCloudinary(uid,name,key,secret);
  await getAuth().deleteUser(uid).catch(e=>{if(e.code!=="auth/user-not-found")throw e;});
  await ref.update({status:"completed",completedAt:FieldValue.serverTimestamp()});
 }catch(error){
  console.error("Pawly deletion failed",uid,error);
  await ref.update({status:"failed",failureReason:String(error.message||error).slice(0,500),failedAt:FieldValue.serverTimestamp()});
  throw error;
 }
});
