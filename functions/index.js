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
const nodemailer = require('nodemailer');
admin.initializeApp();

// ── Email helpers ───────────────────────────────────────────────────────────
// SMTP credentials come from Google Cloud Secret Manager (no .env files).
// Set them once:
//   firebase functions:secrets:set SMTP_EMAIL
//   firebase functions:secrets:set SMTP_PASSWORD
//
// Functions that send email declare  secrets: ["SMTP_EMAIL", "SMTP_PASSWORD"]
// in their runWith() options so the values are injected at runtime.
// ─────────────────────────────────────────────────────────────────────────────
function getTransporter() {
  return nodemailer.createTransport({
    service: 'gmail',
    auth: {
      user: process.env.SMTP_EMAIL,
      pass: process.env.SMTP_PASSWORD,
    },
  });
}

async function sendEmail(to, subject, html) {
  const transporter = getTransporter();
  const fromAddress = process.env.SMTP_EMAIL;
  const info = await transporter.sendMail({
    from: `"SIT Connect" <${fromAddress}>`,
    to,
    subject,
    html,
  });
  console.log('Email sent to', to, '– messageId:', info.messageId);
  return info;
}

function verificationEmailHtml(name, link) {
  return `<div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto">
    <h2 style="color:#1a73e8">Email Verification</h2>
    ${name ? `<p>Hi ${name},</p>` : ''}
    <p>Please verify your email address by clicking the button below:</p>
    <p style="text-align:center;margin:28px 0">
      <a href="${link}" style="background:#1a73e8;color:#fff;padding:12px 32px;text-decoration:none;border-radius:6px;font-weight:bold">Verify Email Address</a>
    </p>
    <p style="color:#555;font-size:13px">If the button doesn't work, copy and paste this link:<br/>
    <a href="${link}">${link}</a></p>
    <hr style="border:none;border-top:1px solid #eee;margin:24px 0"/>
    <p style="color:#999;font-size:12px">This email was sent by SIT Connect.</p>
  </div>`;
}

function passwordResetEmailHtml(name, link) {
  return `<div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto">
    <h2 style="color:#1a73e8">Password Reset</h2>
    ${name ? `<p>Hi ${name},</p>` : ''}
    <p>We received a request to reset the password for your SIT Connect account.</p>
    <p style="text-align:center;margin:28px 0">
      <a href="${link}" style="background:#1a73e8;color:#fff;padding:12px 32px;text-decoration:none;border-radius:6px;font-weight:bold">Reset Password</a>
    </p>
    <p style="color:#555;font-size:13px">If the button doesn't work, copy and paste this link:<br/>
    <a href="${link}">${link}</a></p>
    <p style="color:#555;font-size:13px">If you didn't request this, you can safely ignore this email.</p>
    <hr style="border:none;border-top:1px solid #eee;margin:24px 0"/>
    <p style="color:#999;font-size:12px">This email was sent by SIT Connect.</p>
  </div>`;
}

/** Look up a user's display name from Firestore. Returns '' on failure. */
async function getUserName(uid) {
  try {
    const doc = await admin.firestore().collection('users').doc(uid).get();
    return doc.exists ? (doc.data().name || '') : '';
  } catch (_) { return ''; }
}

exports.sendWelcomeEmail = functions.auth.user().onCreate(async (user) => {
  const email = user.email;
  const uid = user.uid;

  // Only create the Firestore document if it doesn't already exist.
  // When an admin uses createUser(), that function already writes the document
  // with the correct roles. Writing again here would overwrite those roles with
  // hardcoded student:true.
  const docRef = admin.firestore().collection('users').doc(uid);
  const docSnap = await docRef.get();

  if (docSnap.exists) {
    console.log('User document already exists for:', email, '— skipping onCreate write.');
    return null;
  }

  // Self-registered user (e.g., via email/password sign-up on the login screen)
  return docRef.set({
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
exports.createUser = functions
  .runWith({ secrets: ["SMTP_EMAIL", "SMTP_PASSWORD"] })
  .https.onCall(async (data, context) => {
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
      name: name,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      isOnboarded: false,
      roles: {
        student: roles?.student || false,
        lecturer: roles?.lecturer || false,
        admin: roles?.admin || false,
      },
    });

    console.log('Successfully created user:', email);

    // Send verification email to the newly created user
    try {
      const verifyLink = await admin.auth().generateEmailVerificationLink(email);
      await sendEmail(email, 'Verify your SIT Connect account', verificationEmailHtml(name, verifyLink));
      console.log('Verification email sent to:', email);
    } catch (emailErr) {
      // Don't fail the whole create if the email fails – user still exists
      console.error('Failed to send verification email:', emailErr);
    }

    return {
      success: true,
      uid: userRecord.uid,
      message: 'User created successfully. Verification email sent.',
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
exports.sendPasswordReset = functions
  .runWith({ secrets: ["SMTP_EMAIL", "SMTP_PASSWORD"] })
  .https.onCall(async (data, context) => {
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
    // Generate password reset link and send via SMTP
    const resetLink = await admin.auth().generatePasswordResetLink(email);

    let userName = '';
    try {
      const userRecord = await admin.auth().getUserByEmail(email);
      userName = await getUserName(userRecord.uid);
    } catch (_) {}

    await sendEmail(email, 'Reset your SIT Connect password', passwordResetEmailHtml(userName, resetLink));
    console.log('Password reset email sent to:', email);

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

// ── Self-service: send verification email to the calling user ───────────────
exports.sendVerificationEmail = functions
  .runWith({ secrets: ["SMTP_EMAIL", "SMTP_PASSWORD"] })
  .https.onCall(async (data, context) => {
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'Must be signed in.');
    }

    const uid = context.auth.uid;
    try {
      const userRecord = await admin.auth().getUser(uid);
      if (userRecord.emailVerified) {
        return { success: true, message: 'Email is already verified.' };
      }

      const email = userRecord.email;
      if (!email) {
        throw new functions.https.HttpsError('failed-precondition', 'No email on this account.');
      }

      const userName = await getUserName(uid);
      const verifyLink = await admin.auth().generateEmailVerificationLink(email);
      await sendEmail(email, 'Verify your SIT Connect email', verificationEmailHtml(userName, verifyLink));

      console.log('Verification email sent (self-service) to:', email);
      return { success: true, message: 'Verification email sent. Please check your inbox.' };
    } catch (error) {
      console.error('Error sending verification email:', error);
      throw new functions.https.HttpsError('internal', error.message || 'Failed to send verification email');
    }
  });

// ── Self-service: send password reset email to the calling user ─────────────
exports.sendPasswordResetSelf = functions
  .runWith({ secrets: ["SMTP_EMAIL", "SMTP_PASSWORD"] })
  .https.onCall(async (data, context) => {
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'Must be signed in.');
    }

    const uid = context.auth.uid;
    try {
      const userRecord = await admin.auth().getUser(uid);
      const email = userRecord.email;
      if (!email) {
        throw new functions.https.HttpsError('failed-precondition', 'No email on this account.');
      }

      const userName = await getUserName(uid);
      const resetLink = await admin.auth().generatePasswordResetLink(email);
      await sendEmail(email, 'Reset your SIT Connect password', passwordResetEmailHtml(userName, resetLink));

      console.log('Password reset email sent (self-service) to:', email);
      return { success: true, message: `Password reset email sent to ${email}` };
    } catch (error) {
      console.error('Error sending password reset (self):', error);
      throw new functions.https.HttpsError('internal', error.message || 'Failed to send password reset email');
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