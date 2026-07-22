const express = require('express');
const router = express.Router();
const healthController = require('../controllers/health.controller');

// Public system health check endpoint
router.get('/', healthController.getSystemHealth);

module.exports = router;
