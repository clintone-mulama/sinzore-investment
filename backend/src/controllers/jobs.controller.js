const { successResponse, errorResponse } = require('../utils/response');
const jobsService = require('../services/jobs.service');

class JobsController {
  /**
   * Get queue status summary
   */
  async getQueueStatus(req, res, next) {
    try {
      const status = jobsService.getJobStatusSummary();
      return successResponse(res, status, 'Background job queue status retrieved');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Enqueue a new background job
   */
  async enqueueJob(req, res, next) {
    try {
      const { name, payload, delayMs, maxRetries } = req.body;
      if (!name) {
        return errorResponse(res, 'Job name is required', 400);
      }

      const job = await jobsService.enqueueJob({ name, payload, delayMs, maxRetries });
      return successResponse(res, job, 'Job enqueued successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Trigger manual execution of ready jobs
   */
  async processJobs(req, res, next) {
    try {
      await jobsService.processPendingJobs();
      const status = jobsService.getJobStatusSummary();
      return successResponse(res, status, 'Jobs processing triggered');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get dead letter queue
   */
  async getDeadLetterQueue(req, res, next) {
    try {
      const deadLetterJobs = jobsService.getDeadLetterQueue();
      return successResponse(res, deadLetterJobs, 'Dead letter queue retrieved');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new JobsController();
