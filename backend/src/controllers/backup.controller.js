const { successResponse } = require('../utils/response');
const backupService = require('../services/backup.service');

class BackupController {
  /**
   * Create full database snapshot
   */
  async createSnapshot(req, res, next) {
    try {
      const performedBy = req.user?.email || req.user?.uid || 'ADMIN';
      const result = await backupService.createFullSnapshot({ performedBy });
      return successResponse(res, result, 'Full database snapshot generated');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Export ledger
   */
  async exportLedger(req, res, next) {
    try {
      const { limit } = req.query;
      const result = await backupService.exportLedger({ limit });
      return successResponse(res, result, 'Ledger exported successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Export statistics
   */
  async exportStatistics(req, res, next) {
    try {
      const result = await backupService.exportStatistics();
      return successResponse(res, result, 'Statistics exported successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Backup configuration
   */
  async backupConfiguration(req, res, next) {
    try {
      const result = await backupService.backupConfiguration();
      return successResponse(res, result, 'Configuration backed up successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Verify backup integrity
   */
  async verifyBackup(req, res, next) {
    try {
      const { backupId } = req.params;
      const result = await backupService.verifyBackupIntegrity(backupId);
      return successResponse(res, result, 'Backup integrity verification completed');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get backup history
   */
  async getBackupHistory(req, res, next) {
    try {
      const { limit } = req.query;
      const history = await backupService.getBackupHistory({ limit });
      return successResponse(res, history, 'Backup history retrieved');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new BackupController();
