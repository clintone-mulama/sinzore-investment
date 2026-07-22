/**
 * Standardized API response wrappers for Sinzore Investment Backend
 */

/**
 * Send a success response
 */
function successResponse(res, data = null, message = 'Success', statusCode = 200) {
  return res.status(statusCode).json({
    success: true,
    message,
    data,
    timestamp: new Date().toISOString(),
  });
}

/**
 * Send an error response
 */
function errorResponse(res, message = 'An error occurred', statusCode = 500, errors = null) {
  return res.status(statusCode).json({
    success: false,
    message,
    errors,
    timestamp: new Date().toISOString(),
  });
}

/**
 * Send a paginated response
 */
function paginatedResponse(res, items = [], page = 1, limit = 20, total = 0, message = 'Success') {
  return res.status(200).json({
    success: true,
    message,
    data: items,
    pagination: {
      page: Number(page),
      limit: Number(limit),
      total: Number(total),
      totalPages: Math.ceil(total / limit) || 1,
    },
    timestamp: new Date().toISOString(),
  });
}

module.exports = {
  successResponse,
  errorResponse,
  paginatedResponse,
};
