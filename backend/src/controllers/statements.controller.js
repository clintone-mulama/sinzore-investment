const { successResponse, errorResponse } = require('../utils/response');
const financialService = require('../services/financial.service');

class StatementsController {
  /**
   * Get financial statement for authenticated member
   */
  async getMyStatement(req, res, next) {
    try {
      const memberId = req.user.uid;
      const statement = await financialService.getMemberFinancialStatement(memberId);
      return successResponse(res, statement, 'Member financial statement retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get financial statement for a specific member (Admin or self)
   */
  async getMemberStatement(req, res, next) {
    try {
      const { memberId } = req.params;
      const requestingUserId = req.user.uid;
      const role = req.user.role || 'MEMBER';

      if (role !== 'ADMIN' && role !== 'TREASURER' && requestingUserId !== memberId) {
        return errorResponse(res, 'Unauthorized access to member financial statement', 403);
      }

      const statement = await financialService.getMemberFinancialStatement(memberId);
      return successResponse(res, statement, 'Member financial statement retrieved successfully');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new StatementsController();
