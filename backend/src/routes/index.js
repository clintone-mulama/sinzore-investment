const express = require('express');
const router = express.Router();

const authRoutes = require('./auth.routes');
const mpesaRoutes = require('./mpesa.routes');
const loansRoutes = require('./loans.routes');
const repaymentsRoutes = require('./repayments.routes');
const contributionsRoutes = require('./contributions.routes');
const dashboardRoutes = require('./dashboard.routes');
const reportsRoutes = require('./reports.routes');
const statementsRoutes = require('./statements.routes');
const notificationsRoutes = require('./notifications.routes');
const jobsRoutes = require('./jobs.routes');
const integrityRoutes = require('./integrity.routes');
const backupRoutes = require('./backup.routes');
const healthRoutes = require('./health.routes');

// Legacy & Detailed Health Endpoint
router.get('/health', (req, res, next) => {
  const healthController = require('../controllers/health.controller');
  return healthController.getSystemHealth(req, res, next);
});

// Mount modular sub-routes
router.use('/auth', authRoutes);
router.use('/mpesa', mpesaRoutes);
router.use('/loans', loansRoutes);
router.use('/repayments', repaymentsRoutes);
router.use('/contributions', contributionsRoutes);
router.use('/dashboard', dashboardRoutes);
router.use('/reports', reportsRoutes);
router.use('/statements', statementsRoutes);
router.use('/notifications', notificationsRoutes);
router.use('/jobs', jobsRoutes);
router.use('/integrity', integrityRoutes);
router.use('/backup', backupRoutes);
router.use('/system/health', healthRoutes);

module.exports = router;
