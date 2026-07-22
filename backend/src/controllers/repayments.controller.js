const { successResponse, errorResponse } = require('../utils/response');
const financialService = require('../services/financial.service');
const { db, initialized } = require('../firebase/admin');

class RepaymentsController {
  /**
   * Record a loan repayment using Financial Core
   */
  async recordRepayment(req, res, next) {
    try {
      const { loanId, amount, paymentMethod, transactionReference, memberId, memberName, notes } = req.body;

      if (!loanId || !amount || Number(amount) <= 0) {
        return errorResponse(res, 'Loan ID and a positive repayment amount are required', 400);
      }

      const performedBy = req.user?.email || req.user?.uid || 'MEMBER';
      const result = await financialService.recordLoanRepayment({
        loanId,
        memberId: memberId || req.user.uid,
        memberName: memberName || req.user.fullName || 'Member',
        amount: Number(amount),
        paymentMethod: paymentMethod || 'MPESA',
        referenceId: transactionReference,
        notes: notes || `Repayment for Loan #${loanId}`,
        performedBy,
      });

      return successResponse(res, result, 'Loan repayment recorded successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get repayment history
   */
  async getRepaymentsHistory(req, res, next) {
    try {
      const memberId = req.query.memberId || req.user.uid;

      if (initialized && db) {
        const snapshot = await db.collection('repayments')
          .where('memberId', '==', memberId)
          .get();

        const items = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
        return successResponse(res, items, 'Repayment history retrieved');
      }

      return successResponse(res, [], 'Repayment history');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new RepaymentsController();
