const express = require('express');
const router = express.Router();
const backupController = require('../controllers/backup.controller');
const { verifyToken, requireAdmin } = require('../middleware/auth');

router.post('/snapshot', verifyToken, requireAdmin, backupController.createSnapshot);
router.get('/export/ledger', verifyToken, requireAdmin, backupController.exportLedger);
router.get('/export/statistics', verifyToken, requireAdmin, backupController.exportStatistics);
router.get('/export/config', verifyToken, requireAdmin, backupController.backupConfiguration);
router.get('/verify/:backupId', verifyToken, requireAdmin, backupController.verifyBackup);
router.get('/history', verifyToken, requireAdmin, backupController.getBackupHistory);

module.exports = router;
