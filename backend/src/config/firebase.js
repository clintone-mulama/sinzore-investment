const env = require('./env');

/**
 * Returns service account configuration object or null if not provided
 */
function getFirebaseCredentials() {
  if (env.firebase.clientEmail && env.firebase.privateKey) {
    return {
      projectId: env.firebase.projectId,
      clientEmail: env.firebase.clientEmail,
      privateKey: env.firebase.privateKey,
    };
  }
  return null;
}

module.exports = {
  getFirebaseCredentials,
  projectId: env.firebase.projectId,
  databaseUrl: env.firebase.databaseUrl,
};
