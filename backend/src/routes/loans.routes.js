const express = require('express');
const router = express.Router();
const loansController = require('../controllers/loans.controller');
const { verifyToken, requireAdmin } = require('../middleware/auth');

router.post('/apply', verifyToken, loansController.applyLoan);
router.post('/approve', verifyToken, requireAdmin, loansController.approveLoan);
router.post('/deny', verifyToken, requireAdmin, loansController.denyLoan);
router.post('/manual', verifyToken, requireAdmin, loansController.recordManualLoan);
router.post('/clear', verifyToken, requireAdmin, loansController.clearLoan);
router.get('/my-loans', verifyToken, loansController.getMyLoans);
router.get('/:loanId', verifyToken, loansController.getLoanStatus);

module.exports = router;
