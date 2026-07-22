const { successResponse } = require('../utils/response');
const integrityService = require('../services/integrity.service');

class IntegrityController {
  /**
   * Run treasury verification check
   */
  async verifyTreasury(req, res, next) {
    try {
      const performedBy = req.user?.email || req.user?.uid || 'ADMIN';
      const report = await integrityService.verifyTreasuryIntegrity({ performedBy });
      return successResponse(res, report, 'Treasury verification completed');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Fetch integrity alerts
   */
  async getAlerts(req, res, next) {
    try {
      const { limit } = req.query;
      const alerts = await integrityService.getIntegrityAlerts({ limit });
      return successResponse(res, alerts, 'Integrity alerts retrieved');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Fetch discrepancy reports
   */
  async getDiscrepancyReports(req, res, next) {
    try {
      const { limit } = req.query;
      const reports = await integrityService.getDiscrepancyReports({ limit });
      return successResponse(res, reports, 'Discrepancy reports retrieved');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new IntegrityController();
