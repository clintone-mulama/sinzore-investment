const admin = require('firebase-admin');
const firebaseConfig = require('../config/firebase');
const env = require('../config/env');

let initialized = false;
let isLiveConnection = false;

try {
  if (!admin.apps.length) {
    const credentials = firebaseConfig.getFirebaseCredentials();

    if (credentials && credentials.privateKey && credentials.privateKey.trim().length > 30) {
      admin.initializeApp({
        credential: admin.credential.cert(credentials),
        databaseURL: firebaseConfig.databaseUrl,
      });
      initialized = true;
      isLiveConnection = true;
      console.log(`[FIREBASE ADMIN] Initialized with Service Account credentials for project: ${env.firebase.projectId}`);
    } else if (process.env.K_SERVICE || process.env.GOOGLE_APPLICATION_CREDENTIALS || process.env.GAE_INSTANCE) {
      admin.initializeApp({
        credential: admin.credential.applicationDefault(),
        projectId: env.firebase.projectId,
        databaseURL: firebaseConfig.databaseUrl,
      });
      initialized = true;
      isLiveConnection = true;
      console.log(`[FIREBASE ADMIN] Initialized with Application Default Credentials for project: ${env.firebase.projectId}`);
    } else {
      admin.initializeApp({
        projectId: env.firebase.projectId,
        databaseURL: firebaseConfig.databaseUrl,
      });
      initialized = true;
      isLiveConnection = false;
      console.log(`[FIREBASE ADMIN] Initialized in Development Mode (No Private Key provided).`);
    }
  }
} catch (error) {
  console.warn(`[FIREBASE ADMIN] Initialization notice: ${error.message}. Running in development mode.`);
  initialized = false;
  isLiveConnection = false;
}

const db = isLiveConnection ? admin.firestore() : null;
const auth = isLiveConnection ? admin.auth() : null;

module.exports = {
  admin,
  db,
  auth,
  initialized: isLiveConnection,
  isLiveConnection,
  FieldValue: admin.firestore ? admin.firestore.FieldValue : null,
  Timestamp: admin.firestore ? admin.firestore.Timestamp : null,
};
