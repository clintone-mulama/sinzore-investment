const express = require('express');
const router = express.Router();
const authController = require('../controllers/auth.controller');
const { verifyToken, requireAdmin } = require('../middleware/auth');

router.post('/verify', verifyToken, authController.verifyAuth);
router.get('/profile', verifyToken, authController.getProfile);
router.post('/role', verifyToken, requireAdmin, authController.updateUserRole);

module.exports = router;
