const { successResponse } = require('../utils/response');
const healthService = require('../services/health.service');

class HealthController {
  /**
   * Comprehensive System Health Endpoint
   */
  async getSystemHealth(req, res, next) {
    try {
      const status = await healthService.getSystemHealthStatus();
      return successResponse(res, status, 'System health evaluation completed');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new HealthController();
