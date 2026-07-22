const express = require('express');
const router = express.Router();
const contributionsController = require('../controllers/contributions.controller');
const { verifyToken } = require('../middleware/auth');

router.post('/', verifyToken, contributionsController.recordContribution);
router.post('/record', verifyToken, contributionsController.recordContribution);
router.get('/history', verifyToken, contributionsController.getContributionsHistory);

module.exports = router;
