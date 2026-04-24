const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.sendSOSNotification = functions.database
    .ref("/users/{userId}/sos/active")
    .onUpdate(async (change, context) => {

        const before = change.before.val();
        const after = change.after.val();

        // 🔥 Trigger only when SOS becomes true
        if (!before && after === true) {

            console.log("🚨 SOS Triggered");

            const db = admin.database();

            // 🔴 Get guardian token
            const tokenSnapshot = await db
                .ref("/guardians/guardian_001/fcmToken")
                .once("value");

            const token = tokenSnapshot.val();

            if (!token) {
                console.log("❌ No token found");
                return null;
            }

            const message = {
                notification: {
                    title: "🚨 SOS ALERT",
                    body: "User needs immediate help"
                },
                token: token
            };

            try {
                await admin.messaging().send(message);
                console.log("✅ Notification sent");
            } catch (error) {
                console.log("❌ Error sending notification", error);
            }
        }

        return null;
    });