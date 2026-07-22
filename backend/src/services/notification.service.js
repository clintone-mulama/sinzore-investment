const { db, initialized, Timestamp } = require('../firebase/admin');
const logger = require('../utils/logger');

// In-memory notifications fallback for dev mode
const inMemoryNotifications = new Map();

class NotificationService {
  /**
   * Helper to generate notification ID
   */
  generateNotificationId() {
    return `NT_${Date.now()}_${Math.random().toString(36).substring(2, 6).toUpperCase()}`;
  }

  /**
   * Creates a targeted user or role notification
   */
  async createNotification({
    recipientId, // Specific user UID or 'ALL_MEMBERS' | 'ALL_ADMINS' | 'ALL_TREASURERS'
    title,
    message,
    type = 'SYSTEM_ALERT', // CONTRIBUTION_SUCCESS | REPAYMENT_SUCCESS | LOAN_APPROVED | LOAN_DENIED | LOAN_CLEARED | TREASURY_ADJUSTMENT | SYSTEM_ALERT
    metadata = {},
  }) {
    const notificationId = this.generateNotificationId();
    const notificationDoc = {
      notificationId,
      recipientId: recipientId || 'ALL_MEMBERS',
      title: title || 'Notification',
      message: message || '',
      type,
      metadata,
      isRead: false,
      createdAt: Timestamp ? Timestamp.now() : new Date().toISOString(),
    };

    logger.info(`[NOTIFICATION SERVICE] Dispatching notification #${notificationId} [${type}] to ${recipientId}`);

    if (initialized && db) {
      await db.collection('notifications').doc(notificationId).set(notificationDoc);
    } else {
      inMemoryNotifications.set(notificationId, notificationDoc);
    }

    return notificationDoc;
  }

  /**
   * Broadcast notification to multiple users / roles
   */
  async broadcastNotification({ recipientRole = 'ALL_MEMBERS', title, message, type = 'SYSTEM_ALERT', metadata = {} }) {
    return await this.createNotification({
      recipientId: recipientRole,
      title,
      message,
      type,
      metadata,
    });
  }

  /**
   * Get notifications for a user (including broadcast notifications)
   */
  async getUserNotifications({ userId, role = 'MEMBER', limit = 50 }) {
    logger.info(`[NOTIFICATION SERVICE] Fetching notifications for user: ${userId}, role: ${role}`);

    const recipientTargets = [userId, 'ALL_MEMBERS'];
    if (role === 'ADMIN') recipientTargets.push('ALL_ADMINS');
    if (role === 'TREASURER') recipientTargets.push('ALL_TREASURERS');

    if (initialized && db) {
      const snap = await db.collection('notifications')
        .where('recipientId', 'in', recipientTargets)
        .limit(Number(limit))
        .get();

      const list = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      // Client side sort descending by createdAt
      return list.sort((a, b) => {
        const timeA = a.createdAt?._seconds || new Date(a.createdAt).getTime() || 0;
        const timeB = b.createdAt?._seconds || new Date(b.createdAt).getTime() || 0;
        return timeB - timeA;
      });
    }

    // In-memory fallback
    const filtered = Array.from(inMemoryNotifications.values()).filter(n => recipientTargets.includes(n.recipientId));
    return filtered.slice(0, Number(limit));
  }

  /**
   * Get unread count for user
   */
  async getUnreadCount({ userId, role = 'MEMBER' }) {
    const list = await this.getUserNotifications({ userId, role, limit: 100 });
    return list.filter(n => !n.isRead).length;
  }

  /**
   * Mark notification as read
   */
  async markAsRead(notificationId) {
    if (initialized && db) {
      await db.collection('notifications').doc(notificationId).update({
        isRead: true,
        readAt: Timestamp ? Timestamp.now() : new Date().toISOString(),
      });
    } else {
      const existing = inMemoryNotifications.get(notificationId);
      if (existing) {
        inMemoryNotifications.set(notificationId, { ...existing, isRead: true });
      }
    }
    return { success: true, notificationId };
  }

  /**
   * Mark all notifications as read for a user
   */
  async markAllAsRead({ userId, role = 'MEMBER' }) {
    const list = await this.getUserNotifications({ userId, role, limit: 100 });
    for (const item of list) {
      if (!item.isRead) {
        await this.markAsRead(item.notificationId || item.id);
      }
    }
    return { success: true, markedCount: list.length };
  }

  /**
   * Delete a notification
   */
  async deleteNotification(notificationId) {
    if (initialized && db) {
      await db.collection('notifications').doc(notificationId).delete();
    } else {
      inMemoryNotifications.delete(notificationId);
    }
    return { success: true, notificationId };
  }
}

module.exports = new NotificationService();
