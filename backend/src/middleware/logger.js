const morgan = require('morgan');
const logger = require('../utils/logger');

// Custom morgan stream to route HTTP access logs through our logger
const stream = {
  write: (message) => logger.info(message.trim()),
};

const requestLogger = morgan(
  ':remote-addr - :method :url :status :res[content-length] - :response-time ms',
  { stream }
);

module.exports = requestLogger;
