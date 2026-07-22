const { successResponse } = require('../utils/response');
const firebaseService = require('../services/firebase.service');

class DashboardController {
  /**
   * Retrieves summary statistics precomputed document for instant dashboard load
   */
  async getDashboardSummary(req, res, next) {
    try {
      const summaryData = await firebaseService.getSummaryStatistics();
      return successResponse(res, summaryData, 'Dashboard summary statistics retrieved successfully');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new DashboardController();
