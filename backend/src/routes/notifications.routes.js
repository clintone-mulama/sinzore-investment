const express = require('express');
const router = express.Router();
const notificationsController = require('../controllers/notifications.controller');
const { verifyToken, requireAdmin } = require('../middleware/auth');

router.get('/', verifyToken, notificationsController.getUserNotifications);
router.get('/unread-count', verifyToken, notificationsController.getUnreadCount);
router.put('/:id/read', verifyToken, notificationsController.markAsRead);
router.put('/read-all', verifyToken, notificationsController.markAllAsRead);
router.delete('/:id', verifyToken, notificationsController.deleteNotification);
router.post('/broadcast', verifyToken, requireAdmin, notificationsController.broadcastNotification);

module.exports = router;
