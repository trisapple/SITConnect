/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

//const {setGlobalOptions} = require("firebase-functions");
//const {onRequest} = require("firebase-functions/https");
//const logger = require("firebase-functions/logger");

// For cost control, you can set the maximum number of containers that can be
// running at the same time. This helps mitigate the impact of unexpected
// traffic spikes by instead downgrading performance. This limit is a
// per-function limit. You can override the limit for each function using the
// `maxInstances` option in the function's options, e.g.
// `onRequest({ maxInstances: 5 }, (req, res) => { ... })`.
// NOTE: setGlobalOptions does not apply to functions using the v1 API. V1
// functions should each use functions.runWith({ maxInstances: 10 }) instead.
// In the v1 API, each function can only serve one request per container, so
// this will be the maximum concurrent request count.
//setGlobalOptions({ maxInstances: 10 });

// Create and deploy your first functions
// https://firebase.google.com/docs/functions/get-started

// exports.helloWorld = onRequest((request, response) => {
//   logger.info("Hello logs!", {structuredData: true});
//   response.send("Hello from Firebase!");
// });

const functions = require('firebase-functions/v1');
const admin = require('firebase-admin');
admin.initializeApp();

exports.sendWelcomeEmail = functions.auth.user().onCreate((user) => {
  // Your backend logic here, e.g., send a welcome email,
  // or create a user profile in Firestore
  const email = user.email; // The email of the new user
  const uid = user.uid; // The unique user ID

  // Example: Create a user document in Firestore
  return admin.firestore().collection('users').doc(uid).set({
    email: email,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    name: "Test",
    roles: {
        "student": true,
        "lecturer": false,
        "admin": false
    },
  }).then(() => {
    console.log('New user document created in Firestore for:', email);
  }).catch((error) => {
    console.error('Error creating user document:', error);
  });
});

exports.deleteUserData = functions.auth.user().onDelete((user) => {
  const uid = user.uid;
  const email = user.email;

  return admin.firestore().collection('users').doc(uid).delete()
    .then(() => {
      console.log('User document deleted from Firestore for:', email);
    })
    .catch((error) => {
      console.error('Error deleting user document:', error);
    });
});
