const express = require('express');
const router = express.Router();
const reportsController = require('../controllers/reports.controller');
const { verifyToken, requireAdmin } = require('../middleware/auth');

router.get('/', verifyToken, reportsController.generateFinancialReport);
router.get('/member-statement/:memberId?', verifyToken, reportsController.getMemberStatementReport);
router.get('/treasury', verifyToken, requireAdmin, reportsController.getTreasuryReport);
router.get('/contributions', verifyToken, reportsController.getContributionReport);
router.get('/loans', verifyToken, reportsController.getLoanReport);
router.get('/repayments', verifyToken, reportsController.getRepaymentReport);
router.get('/ledger', verifyToken, requireAdmin, reportsController.getLedgerReport);
router.get('/audit-logs', verifyToken, requireAdmin, reportsController.getAuditLogs);
router.get('/monthly-summary', verifyToken, reportsController.getMonthlySummaryReport);
router.get('/annual-summary', verifyToken, reportsController.getAnnualSummaryReport);

module.exports = router;
