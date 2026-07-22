const { successResponse, errorResponse } = require('../utils/response');
const { auth, initialized } = require('../firebase/admin');
const firebaseService = require('../services/firebase.service');
const logger = require('../utils/logger');

/**
 * Controller for Auth verification & User Profile Management
 */
class AuthController {
  /**
   * Verify token and return user identity
   */
  async verifyAuth(req, res, next) {
    try {
      const user = req.user;
      return successResponse(res, {
        uid: user.uid,
        email: user.email,
        role: user.role || 'MEMBER',
        verified: true,
      }, 'Authentication verified successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get authenticated user profile
   */
  async getProfile(req, res, next) {
    try {
      const uid = req.user.uid;
      let profile = {
        uid,
        email: req.user.email,
        fullName: 'Sinzore Member',
        role: req.user.role || 'MEMBER',
        status: 'ACTIVE',
      };

      if (initialized && auth) {
        try {
          const userRecord = await auth.getUser(uid);
          profile = {
            ...profile,
            fullName: userRecord.displayName || profile.fullName,
            phoneNumber: userRecord.phoneNumber || '',
            customClaims: userRecord.customClaims || {},
          };
        } catch (e) {
          logger.warn(`Could not fetch extended profile for ${uid}: ${e.message}`);
        }
      }

      return successResponse(res, profile, 'User profile retrieved');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Update user custom role (Admin only)
   */
  async updateUserRole(req, res, next) {
    try {
      const { targetUid, role } = req.body;
      if (!targetUid || !role) {
        return errorResponse(res, 'Target user ID (targetUid) and role are required', 400);
      }

      if (initialized && auth) {
        await auth.setCustomUserClaims(targetUid, { role: role.toUpperCase() });
      }

      await firebaseService.logAuditEvent({
        action: 'UPDATE_USER_ROLE',
        userId: req.user.uid,
        userEmail: req.user.email,
        details: { targetUid, newRole: role },
      });

      return successResponse(res, { targetUid, role: role.toUpperCase() }, 'User role updated successfully');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new AuthController();
