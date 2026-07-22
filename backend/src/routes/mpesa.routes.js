const express = require('express');
const router = express.Router();
const mpesaController = require('../controllers/mpesa.controller');
const { verifyToken, requireAdmin } = require('../middleware/auth');

// Protected transaction actions
router.post('/stkpush', verifyToken, mpesaController.initiateStkPush);
router.post('/b2c', verifyToken, requireAdmin, mpesaController.disburseB2cLoan);
router.post('/retry', verifyToken, mpesaController.retryTransaction);

// Dev Mode simulation routes
router.post('/simulate/stk-callback', verifyToken, mpesaController.simulateStkCallback);
router.post('/simulate/b2c-callback', verifyToken, mpesaController.simulateB2cCallback);

// Transaction History & Reconciliation
router.get('/transactions', verifyToken, mpesaController.getPaymentTransactions);
router.get('/transactions/:id', verifyToken, mpesaController.getPaymentTransactionById);
router.get('/reconcile', verifyToken, requireAdmin, mpesaController.reconcileTransactions);

// Public webhook callback routes (triggered directly by Safaricom Daraja servers)
router.post('/callback', mpesaController.handleStkCallback);
router.post('/b2c/callback', mpesaController.handleB2cCallback);

module.exports = router;
