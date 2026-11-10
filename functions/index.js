import { getApp, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions";
import { onDocumentCreated } from "firebase-functions/v2/firestore";

const app = getApp() ?? initializeApp();
const db = getFirestore(app);
const messaging = getMessaging(app);
const INVALID_TOKEN_CODES = new Set([
  "messaging/invalid-registration-token",
  "messaging/registration-token-not-registered",
]);

/**
 * Send a privacy-safe push after a chat message is committed to Firestore.
 * The message body itself is deliberately not copied into FCM or the lock screen.
 */
export const notifyOnNewChatMessage = onDocumentCreated(
  "conversations/{conversationId}/messages/{messageId}",
  async (event) => {
    const message = event.data?.data();
    const conversationId = event.params.conversationId;
    const messageId = event.params.messageId;
    const senderId = typeof message?.senderId === "string" ? message.senderId : "";

    if (!senderId) {
      logger.warn("Skipping message notification without a sender id.", { conversationId, messageId });
      return;
    }

    const conversationRef = db.collection("conversations").doc(conversationId);
    const conversation = await conversationRef.get();
    const participants = conversation.get("participants");

    if (!conversation.exists || !Array.isArray(participants) || participants.length !== 2 || !participants.includes(senderId)) {
      logger.warn("Skipping message notification for an invalid conversation.", { conversationId, messageId });
      return;
    }

    const recipientId = participants.find((participant) => participant !== senderId);
    if (typeof recipientId !== "string" || !recipientId) return;

    const [senderProfile, blockedByRecipient, blockedBySender] = await Promise.all([
      db.collection("users").doc(senderId).get(),
      db.collection("users").doc(recipientId).collection("blocked").doc(senderId).get(),
      db.collection("users").doc(senderId).collection("blocked").doc(recipientId).get(),
    ]);

    if (blockedByRecipient.exists || blockedBySender.exists) {
      logger.info("Skipping message notification because one participant has blocked the other.", {
        conversationId,
        messageId,
      });
      return;
    }

    const tokenSnapshot = await db.collection("users").doc(recipientId).collection("fcmTokens").get();
    const targets = new Map();
    for (const tokenDocument of tokenSnapshot.docs) {
      const token = tokenDocument.get("token");
      if (typeof token === "string" && token.length > 0) {
        targets.set(token, tokenDocument.ref);
      }
    }

    if (targets.size === 0) {
      logger.info("No registered FCM devices for message recipient.", { conversationId, recipientId });
      return;
    }

    const profile = senderProfile.data() ?? {};
    const senderName = String(profile.displayName || profile.username || "Una persona de Pawly").slice(0, 80);
    const body = message.imageUrl
      ? "Te ha enviado una foto"
      : message.videoUrl
        ? "Te ha enviado un vídeo"
        : "Tienes un mensaje nuevo";

    const payload = {
      type: "chat_message",
      title: senderName,
      body,
      conversationId,
      messageId,
    };
    const entries = [...targets.entries()];
    let successCount = 0;
    let failureCount = 0;
    const staleTokenRefs = [];

    for (let start = 0; start < entries.length; start += 500) {
      const chunk = entries.slice(start, start + 500);
      const batch = await messaging.sendEach(
        chunk.map(([token]) => ({
          token,
          data: payload,
          android: {
            priority: "high",
            ttl: 60 * 60 * 1000,
          },
        })),
      );

      successCount += batch.successCount;
      failureCount += batch.failureCount;
      batch.responses.forEach((response, index) => {
        const code = response.error?.code;
        if (code && INVALID_TOKEN_CODES.has(code)) {
          staleTokenRefs.push(chunk[index][1]);
        }
      });
    }

    if (staleTokenRefs.length > 0) {
      await Promise.all(staleTokenRefs.map((reference) => reference.delete().catch(() => undefined)));
    }

    logger.info("Message push dispatch completed.", {
      conversationId,
      recipientId,
      successCount,
      failureCount,
      removedInvalidTokens: staleTokenRefs.length,
    });
  },
);
