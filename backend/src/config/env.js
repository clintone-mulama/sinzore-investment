const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../../.env') });

/**
 * Environment configuration reader and validator.
 */
const env = {
  // Server Config
  port: parseInt(process.env.PORT || process.env.BACKEND_PORT || '5000', 10),
  nodeEnv: process.env.NODE_ENV || 'development',
  apiBaseUrl: process.env.API_BASE_URL || 'http://localhost:5000',

  // Firebase Config
  firebase: {
    projectId: process.env.FIREBASE_PROJECT_ID || 'sinzore-investment',
    clientEmail: process.env.FIREBASE_CLIENT_EMAIL || '',
    privateKey: (process.env.FIREBASE_PRIVATE_KEY || '').replace(/\\n/g, '\n'),
    databaseUrl: process.env.FIREBASE_DATABASE_URL || 'https://sinzore-investment.firebaseio.com',
  },

  // Safaricom Daraja M-Pesa Credentials
  mpesa: {
    developmentMode: process.env.MP_DEV_MODE !== undefined ? process.env.MP_DEV_MODE === 'true' : process.env.NODE_ENV !== 'production',
    environment: process.env.MP_ENVIRONMENT || 'sandbox', // sandbox | production
    consumerKey: process.env.MP_CONSUMER_KEY || '',
    consumerSecret: process.env.MP_CONSUMER_SECRET || '',
    shortcode: process.env.MP_SHORTCODE || '174379',
    passkey: process.env.MP_PASSKEY || '',
    initiatorName: process.env.MP_INITIATOR_NAME || '',
    securityCredential: process.env.MP_SECURITY_CREDENTIAL || '',
    callbackUrl: process.env.MP_CALLBACK_URL || '',
  },

  // Security & Logging
  corsOrigin: process.env.CORS_ORIGIN || '*',
  logLevel: process.env.LOG_LEVEL || 'info',
};

// Quick validation on start
const isProd = env.nodeEnv === 'production';
if (isProd && (!env.mpesa.consumerKey || !env.mpesa.consumerSecret)) {
  console.warn('[ENV WARNING] Production environment detected without Daraja API credentials.');
}

module.exports = env;
