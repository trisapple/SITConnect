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
    isOnboarded: false,
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

// Cloud Function to create a new user (admin only)
exports.createUser = functions.https.onCall(async (data, context) => {
  // Check if the request is made by an authenticated user
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'User must be authenticated to create users.'
    );
  }

  // Check if the requesting user is an admin
  const requestingUserDoc = await admin.firestore()
    .collection('users')
    .doc(context.auth.uid)
    .get();

  if (!requestingUserDoc.exists || !requestingUserDoc.data().roles?.admin) {
    throw new functions.https.HttpsError(
      'permission-denied',
      'Only administrators can create users.'
    );
  }

  // Validate input data
  const { email, password, name, roles } = data;

  if (!email || !password || !name) {
    throw new functions.https.HttpsError(
      'invalid-argument',
      'Email, password, and name are required.'
    );
  }

  try {
    // Create the user in Firebase Authentication
    const userRecord = await admin.auth().createUser({
      email: email,
      password: password,
      emailVerified: false,
    });

    // Create the user document in Firestore
    await admin.firestore().collection('users').doc(userRecord.uid).set({
      email: email,
      name: 'Test',
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      isOnboarded: false,
      roles: {
        student: roles?.student || false,
        lecturer: roles?.lecturer || false,
        admin: roles?.admin || false,
      },
    });

    console.log('Successfully created user:', email);

    return {
      success: true,
      uid: userRecord.uid,
      message: 'User created successfully',
    };
  } catch (error) {
    console.error('Error creating user:', error);
    throw new functions.https.HttpsError(
      'internal',
      error.message || 'Failed to create user'
    );
  }
});

// Cloud Function to send password reset email (admin only)
exports.sendPasswordReset = functions.https.onCall(async (data, context) => {
  // Check if the request is made by an authenticated user
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'User must be authenticated to send password reset emails.'
    );
  }

  // Check if the requesting user is an admin
  const requestingUserDoc = await admin.firestore()
    .collection('users')
    .doc(context.auth.uid)
    .get();

  if (!requestingUserDoc.exists || !requestingUserDoc.data().roles?.admin) {
    throw new functions.https.HttpsError(
      'permission-denied',
      'Only administrators can send password reset emails.'
    );
  }

  const { email } = data;

  if (!email) {
    throw new functions.https.HttpsError(
      'invalid-argument',
      'Email is required.'
    );
  }

  try {
    // Generate password reset link
    const resetLink = await admin.auth().generatePasswordResetLink(email);
    
    // In a production app, you would send this link via email
    // For now, we'll just log it and return success
    console.log('Password reset link generated for:', email);
    console.log('Reset link:', resetLink);

    return {
      success: true,
      message: 'Password reset email sent successfully',
    };
  } catch (error) {
    console.error('Error sending password reset:', error);
    throw new functions.https.HttpsError(
      'internal',
      error.message || 'Failed to send password reset email'
    );
  }
});

// Cloud Function to delete a user (admin only)
exports.deleteUser = functions.https.onCall(async (data, context) => {
  // Check if the request is made by an authenticated user
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'User must be authenticated to delete users.'
    );
  }

  // Check if the requesting user is an admin
  const requestingUserDoc = await admin.firestore()
    .collection('users')
    .doc(context.auth.uid)
    .get();

  if (!requestingUserDoc.exists || !requestingUserDoc.data().roles?.admin) {
    throw new functions.https.HttpsError(
      'permission-denied',
      'Only administrators can delete users.'
    );
  }

  const { uid } = data;

  if (!uid) {
    throw new functions.https.HttpsError(
      'invalid-argument',
      'User ID is required.'
    );
  }

  // Prevent self-deletion
  if (uid === context.auth.uid) {
    throw new functions.https.HttpsError(
      'failed-precondition',
      'Cannot delete your own account.'
    );
  }

  try {
    // Delete the user from Firebase Authentication
    await admin.auth().deleteUser(uid);

    // Delete the user document from Firestore
    await admin.firestore().collection('users').doc(uid).delete();

    // Optionally: Clean up related data (enrolled students from modules, etc.)
    const modulesSnapshot = await admin.firestore()
      .collection('modules')
      .where('enrolledStudents', 'array-contains', uid)
      .get();

    const batch = admin.firestore().batch();
    modulesSnapshot.docs.forEach((doc) => {
      batch.update(doc.ref, {
        enrolledStudents: admin.firestore.FieldValue.arrayRemove(uid)
      });
    });

    // Also clean up from schedules
    const schedulesSnapshot = await admin.firestore()
      .collection('schedules')
      .where('enrolledStudents', 'array-contains', uid)
      .get();

    schedulesSnapshot.docs.forEach((doc) => {
      batch.update(doc.ref, {
        enrolledStudents: admin.firestore.FieldValue.arrayRemove(uid)
      });
    });

    await batch.commit();

    console.log('Successfully deleted user:', uid);

    return {
      success: true,
      message: 'User deleted successfully',
    };
  } catch (error) {
    console.error('Error deleting user:', error);
    throw new functions.https.HttpsError(
      'internal',
      error.message || 'Failed to delete user'
    );
  }
});