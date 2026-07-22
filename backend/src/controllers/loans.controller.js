const { successResponse, errorResponse } = require('../utils/response');
const financialService = require('../services/financial.service');
const { db, initialized } = require('../firebase/admin');

class LoansController {
  /**
   * Apply for a new loan
   */
  async applyLoan(req, res, next) {
    try {
      const { principalAmount, durationMonths, purpose, memberId, memberName } = req.body;

      if (!principalAmount || Number(principalAmount) <= 0) {
        return errorResponse(res, 'Valid principal amount is required', 400);
      }

      const performedBy = req.user?.email || req.user?.uid || 'MEMBER';
      const targetMemberId = memberId || req.user.uid;
      const targetMemberName = memberName || req.user.fullName || 'Member';

      const result = await financialService.applyLoan({
        memberId: targetMemberId,
        memberName: targetMemberName,
        principalAmount: Number(principalAmount),
        durationMonths,
        purpose,
        performedBy,
      });

      return successResponse(res, result, 'Loan application submitted successfully', 201);
    } catch (error) {
      next(error);
    }
  }

  /**
   * Approve a loan application (Admin only) using Financial Core
   */
  async approveLoan(req, res, next) {
    try {
      const { loanId } = req.body;
      if (!loanId) {
        return errorResponse(res, 'Loan ID is required', 400);
      }

      const performedBy = req.user?.email || req.user?.uid || 'ADMIN';
      const result = await financialService.approveLoan({
        loanId,
        performedBy,
      });

      return successResponse(res, result, 'Loan approved successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Record a manual loan disbursal (Admin only) using Financial Core
   */
  async recordManualLoan(req, res, next) {
    try {
      const { memberId, memberName, principalAmount, interestRate, durationMonths, purpose, paymentMethod, referenceId, notes } = req.body;

      if (!principalAmount || Number(principalAmount) <= 0) {
        return errorResponse(res, 'Valid principal amount is required', 400);
      }

      const performedBy = req.user?.email || req.user?.uid || 'ADMIN';
      const result = await financialService.recordManualLoan({
        memberId: memberId || req.user.uid,
        memberName: memberName || 'Member',
        principalAmount: Number(principalAmount),
        interestRate,
        durationMonths,
        purpose,
        paymentMethod,
        referenceId,
        notes,
        performedBy,
      });

      return successResponse(res, result, 'Manual loan recorded successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Deny a loan application (Admin only)
   */
  async denyLoan(req, res, next) {
    try {
      const { loanId, reason } = req.body;
      if (!loanId) {
        return errorResponse(res, 'Loan ID is required', 400);
      }

      const performedBy = req.user?.email || req.user?.uid || 'ADMIN';
      const result = await financialService.denyLoan({
        loanId,
        reason,
        performedBy,
      });

      return successResponse(res, result, 'Loan application denied');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Clear a loan (Admin only) using Financial Core
   */
  async clearLoan(req, res, next) {
    try {
      const { loanId, notes } = req.body;
      if (!loanId) {
        return errorResponse(res, 'Loan ID is required', 400);
      }

      const performedBy = req.user?.email || req.user?.uid || 'ADMIN';
      const result = await financialService.clearLoan({
        loanId,
        notes,
        performedBy,
      });

      return successResponse(res, result, 'Loan cleared successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get loans for authenticated member
   */
  async getMyLoans(req, res, next) {
    try {
      const memberId = req.user.uid;

      if (initialized && db) {
        const snap = await db.collection('loans')
          .where('memberId', '==', memberId)
          .get();
        const loans = snap.docs.map(doc => ({ loanId: doc.id, ...doc.data() }));
        return successResponse(res, loans, 'User loans retrieved successfully');
      }

      return successResponse(res, [], 'User loans');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get loan details
   */
  async getLoanStatus(req, res, next) {
    try {
      const { loanId } = req.params;

      if (initialized && db) {
        const doc = await db.collection('loans').doc(loanId).get();
        if (!doc.exists) {
          return errorResponse(res, 'Loan not found', 404);
        }
        return successResponse(res, { loanId: doc.id, ...doc.data() }, 'Loan record retrieved');
      }

      return successResponse(res, {
        loanId,
        status: 'PENDING',
        principalAmount: 50000,
        totalRepayment: 55000,
      }, 'Loan details');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new LoansController();
