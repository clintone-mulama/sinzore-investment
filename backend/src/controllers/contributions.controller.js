const { successResponse, errorResponse } = require('../utils/response');
const financialService = require('../services/financial.service');
const { db, initialized } = require('../firebase/admin');

class ContributionsController {
  /**
   * Record a contribution (seeds) using Financial Core
   */
  async recordContribution(req, res, next) {
    try {
      const { amount, paymentMethod, transactionReference, memberId, memberName, notes, isManual } = req.body;

      if (!amount || Number(amount) <= 0) {
        return errorResponse(res, 'Valid contribution amount is required', 400);
      }

      const performedBy = req.user?.email || req.user?.uid || 'MEMBER';
      const result = await financialService.recordContribution({
        memberId: memberId || req.user.uid,
        memberName: memberName || req.user.fullName || 'Member',
        amount: Number(amount),
        paymentMethod: paymentMethod || 'MPESA',
        referenceId: transactionReference,
        notes: notes || 'Seeds Contribution',
        performedBy,
        isManual: Boolean(isManual),
      });

      return successResponse(res, result, 'Contribution recorded successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get contribution history
   */
  async getContributionsHistory(req, res, next) {
    try {
      const memberId = req.query.memberId || req.user.uid;

      if (initialized && db) {
        const snapshot = await db.collection('contributions')
          .where('memberId', '==', memberId)
          .get();

        const items = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
        return successResponse(res, items, 'Contributions history retrieved');
      }

      return successResponse(res, [], 'Contributions history');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new ContributionsController();
