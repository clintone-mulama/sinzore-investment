const express = require('express');
const router = express.Router();
const statementsController = require('../controllers/statements.controller');
const { verifyToken } = require('../middleware/auth');

router.get('/my-statement', verifyToken, statementsController.getMyStatement);
router.get('/:memberId', verifyToken, statementsController.getMemberStatement);

module.exports = router;
