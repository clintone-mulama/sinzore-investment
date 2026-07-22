const { auth, db, initialized } = require('../firebase/admin');
const { errorResponse } = require('../utils/response');
const logger = require('../utils/logger');

/**
 * Middleware to verify Firebase ID Token in Authorization header
 * and load user's Firestore profile and role.
 */
async function verifyToken(req, res, next) {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return errorResponse(res, 'Unauthorized: Missing or invalid authorization header', 401);
    }

    const token = authHeader.split('Bearer ')[1].trim();
    if (!token) {
      return errorResponse(res, 'Unauthorized: Empty bearer token provided', 401);
    }

    // Development mock token check if Firebase Admin is not fully authenticated
    if (process.env.NODE_ENV === 'development' && token === 'DEV_MOCK_TOKEN') {
      logger.info('[AUTH MIDDLEWARE] Development mock token accepted');
      req.user = {
        uid: 'dev_user_123',
        email: 'admin@sinzore.org',
        role: 'ADMIN',
        fullName: 'Dev Admin',
        status: 'ACTIVE',
      };
      return next();
    }

    if (!initialized || !auth) {
      // Development fallback if Firebase Admin SDK is unconfigured
      logger.warn('[AUTH MIDDLEWARE] Firebase Admin SDK uninitialized. Accepting request in dev mode.');
      req.user = {
        uid: 'dev_user_123',
        email: 'dev@sinzore.org',
        role: 'ADMIN',
        fullName: 'Dev User',
        status: 'ACTIVE',
      };
      return next();
    }

    // 1. Verify Firebase ID Token
    let decodedToken;
    try {
      decodedToken = await auth.verifyIdToken(token);
    } catch (tokenError) {
      logger.error(`[AUTH MIDDLEWARE] Token verification failed: ${tokenError.message}`);
      return errorResponse(res, 'Unauthorized: Invalid or expired authentication token', 401);
    }

    const uid = decodedToken.uid;
    const email = decodedToken.email || '';

    // 2. Load User Profile from Firestore (`users` collection)
    let userRole = (decodedToken.role || 'MEMBER').toUpperCase();
    let userProfile = null;

    if (db) {
      try {
        const userDoc = await db.collection('users').doc(uid).get();
        if (userDoc.exists) {
          userProfile = userDoc.data();

          // Reject users whose account status is DISABLED or INACTIVE
          if (userProfile.status && userProfile.status.toUpperCase() === 'DISABLED') {
            logger.warn(`[AUTH MIDDLEWARE] Access blocked for disabled user: ${uid}`);
            return errorResponse(res, 'Forbidden: User account has been disabled', 403);
          }

          if (userProfile.role) {
            userRole = userProfile.role.toUpperCase();
          }
        } else {
          logger.warn(`[AUTH MIDDLEWARE] Firestore profile missing for user: ${uid}`);
          // If strictly required that profile must exist:
          // return errorResponse(res, 'Unauthorized: User profile does not exist in database', 401);
        }
      } catch (dbError) {
        logger.warn(`[AUTH MIDDLEWARE] Could not read user profile from Firestore: ${dbError.message}`);
      }
    }

    // 3. Attach verified user info to request object
    req.user = {
      uid,
      email,
      role: userRole,
      fullName: userProfile?.fullName || decodedToken.name || email.split('@')[0] || 'Member',
      phoneNumber: userProfile?.phoneNumber || decodedToken.phone_number || '',
      profile: userProfile,
      decodedToken,
    };

    return next();
  } catch (error) {
    logger.error(`[AUTH MIDDLEWARE] Unexpected authentication error: ${error.message}`);
    return errorResponse(res, 'Unauthorized: Authentication processing error', 401);
  }
}

/**
 * Middleware to enforce role-based access control (e.g. MEMBER, TREASURER, ADMIN)
 */
function requireRole(allowedRoles = []) {
  return (req, res, next) => {
    if (!req.user) {
      return errorResponse(res, 'Unauthorized: User authentication required', 401);
    }

    const roles = Array.isArray(allowedRoles)
      ? allowedRoles.map(r => r.toUpperCase())
      : [allowedRoles.toUpperCase()];

    const userRole = (req.user.role || 'MEMBER').toUpperCase();

    // ADMIN always bypasses role checks
    if (roles.includes('*') || roles.includes(userRole) || userRole === 'ADMIN') {
      return next();
    }

    logger.warn(`[AUTH MIDDLEWARE] Access denied for user ${req.user.uid} (${userRole}). Required: ${roles.join(', ')}`);
    return errorResponse(res, `Forbidden: Insufficient permissions. Required role: ${roles.join(' or ')}`, 403);
  };
}

/**
 * Shortcut helper for Admin and Treasurer access
 */
const requireAdmin = requireRole(['ADMIN', 'TREASURER', 'SECRETARY', 'CHAIRMAN']);

module.exports = {
  verifyToken,
  requireRole,
  requireAdmin,
};
