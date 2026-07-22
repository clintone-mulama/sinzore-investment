const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const env = require('./config/env');
const logger = require('./utils/logger');
const requestLogger = require('./middleware/logger');
const { errorHandler, notFoundHandler } = require('./middleware/errorHandler');
const apiRoutes = require('./routes/index');
const jobsService = require('./services/jobs.service');

const app = express();

// Security Middleware
app.use(helmet());

// CORS Configuration
app.use(cors({
  origin: env.corsOrigin,
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization'],
}));

// Body Parsing Middleware
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// Request Logging Middleware
app.use(requestLogger);

// Base Root Route
app.get('/', (req, res) => {
  res.status(200).json({
    message: 'Welcome to Sinzore Investment Platform Backend API',
    documentation: '/api/health',
    version: '1.0.0-production',
    status: 'ACTIVE',
  });
});

// Mount Main API Router
app.use('/api', apiRoutes);

// 404 Handler for undefined routes
app.use(notFoundHandler);

// Centralized Global Error Handler
app.use(errorHandler);

// Start Express Server if executed directly
if (require.main === module) {
  const PORT = env.port;
  const server = app.listen(PORT, () => {
    logger.info(`=======================================================`);
    logger.info(`Sinzore Investment Backend Server Started Successfully`);
    logger.info(`Port: ${PORT}`);
    logger.info(`Environment: ${env.nodeEnv}`);
    logger.info(`API Base URL: ${env.apiBaseUrl}/api`);
logger.info(`Health Endpoint: ${env.apiBaseUrl}/api/health`);
    logger.info(`=======================================================`);

    // Start background job scheduler
    jobsService.startScheduler(60000);
  });

  // Graceful Shutdown
  const shutdown = () => {
    logger.info('Shutting down backend server gracefully...');
    jobsService.stopScheduler();
    server.close(() => {
      logger.info('Backend server process terminated cleanly.');
      process.exit(0);
    });
  };

  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

module.exports = app;
