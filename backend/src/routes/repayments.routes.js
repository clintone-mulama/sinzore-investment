const express = require('express');
const router = express.Router();
const repaymentsController = require('../controllers/repayments.controller');
const { verifyToken } = require('../middleware/auth');

router.post('/', verifyToken, repaymentsController.recordRepayment);
router.post('/record', verifyToken, repaymentsController.recordRepayment);
router.get('/history', verifyToken, repaymentsController.getRepaymentsHistory);

module.exports = router;
