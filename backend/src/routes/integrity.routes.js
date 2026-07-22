const express = require('express');
const router = express.Router();
const integrityController = require('../controllers/integrity.controller');
const { verifyToken, requireAdmin } = require('../middleware/auth');

router.post('/verify', verifyToken, requireAdmin, integrityController.verifyTreasury);
router.get('/alerts', verifyToken, requireAdmin, integrityController.getAlerts);
router.get('/discrepancies', verifyToken, requireAdmin, integrityController.getDiscrepancyReports);

module.exports = router;
