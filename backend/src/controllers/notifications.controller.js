const { successResponse, errorResponse } = require('../utils/response');
const notificationService = require('../services/notification.service');

class NotificationsController {
  /**
   * Get user notifications
   */
  async getUserNotifications(req, res, next) {
    try {
      const userId = req.user.uid;
      const role = req.user.role || 'MEMBER';
      const { limit } = req.query;

      const notifications = await notificationService.getUserNotifications({
        userId,
        role,
        limit,
      });

      return successResponse(res, notifications, 'Notifications retrieved');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Get unread count
   */
  async getUnreadCount(req, res, next) {
    try {
      const userId = req.user.uid;
      const role = req.user.role || 'MEMBER';

      const unreadCount = await notificationService.getUnreadCount({ userId, role });
      return successResponse(res, { unreadCount }, 'Unread count retrieved');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Mark single notification as read
   */
  async markAsRead(req, res, next) {
    try {
      const { id } = req.params;
      const result = await notificationService.markAsRead(id);
      return successResponse(res, result, 'Notification marked as read');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Mark all notifications as read
   */
  async markAllAsRead(req, res, next) {
    try {
      const userId = req.user.uid;
      const role = req.user.role || 'MEMBER';

      const result = await notificationService.markAllAsRead({ userId, role });
      return successResponse(res, result, 'All notifications marked as read');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Delete a notification
   */
  async deleteNotification(req, res, next) {
    try {
      const { id } = req.params;
      const result = await notificationService.deleteNotification(id);
      return successResponse(res, result, 'Notification deleted');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Broadcast notification (Admin / Treasurer)
   */
  async broadcastNotification(req, res, next) {
    try {
      const { recipientRole, title, message, type, metadata } = req.body;
      if (!title || !message) {
        return errorResponse(res, 'Title and message are required for broadcast', 400);
      }

      const result = await notificationService.broadcastNotification({
        recipientRole: recipientRole || 'ALL_MEMBERS',
        title,
        message,
        type: type || 'SYSTEM_ALERT',
        metadata,
      });

      return successResponse(res, result, 'Broadcast notification sent');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new NotificationsController();
