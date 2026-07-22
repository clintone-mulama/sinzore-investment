const { errorResponse } = require('../utils/response');
const logger = require('../utils/logger');

/**
 * Global error handler middleware for Express
 */
function errorHandler(err, req, res, next) {
  const statusCode = err.statusCode || err.status || 500;
  const message = err.message || 'Internal Server Error';

  logger.error(`[SERVER ERROR] ${req.method} ${req.path} - ${statusCode} - ${message}`, {
    stack: err.stack,
    body: req.body,
  });

  return errorResponse(
    res,
    process.env.NODE_ENV === 'production' && statusCode === 500
      ? 'An internal server error occurred'
      : message,
    statusCode,
    process.env.NODE_ENV === 'development' ? err.stack : null
  );
}

/**
 * Handler for 404 Not Found routes
 */
function notFoundHandler(req, res) {
  return errorResponse(res, `Route non-existent: ${req.method} ${req.path}`, 404);
}

module.exports = {
  errorHandler,
  notFoundHandler,
};
