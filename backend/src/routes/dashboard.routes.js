const express = require('express');
const router = express.Router();
const dashboardController = require('../controllers/dashboard.controller');
const { verifyToken } = require('../middleware/auth');

router.get('/', verifyToken, dashboardController.getDashboardSummary);
router.get('/summary', verifyToken, dashboardController.getDashboardSummary);

module.exports = router;
