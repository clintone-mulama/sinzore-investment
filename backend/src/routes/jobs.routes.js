const express = require('express');
const router = express.Router();
const jobsController = require('../controllers/jobs.controller');
const { verifyToken, requireAdmin } = require('../middleware/auth');

router.get('/status', verifyToken, requireAdmin, jobsController.getQueueStatus);
router.post('/enqueue', verifyToken, requireAdmin, jobsController.enqueueJob);
router.post('/process', verifyToken, requireAdmin, jobsController.processJobs);
router.get('/dead-letter', verifyToken, requireAdmin, jobsController.getDeadLetterQueue);

module.exports = router;
