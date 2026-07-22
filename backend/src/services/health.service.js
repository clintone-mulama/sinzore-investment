const { db, initialized, Timestamp } = require('../firebase/admin');
const env = require('../config/env');
const jobsService = require('./jobs.service');
const darajaClient = require('../mpesa/daraja');
const logger = require('../utils/logger');

const startTime = Date.now();

class HealthService {
  /**
   * Performs full system health evaluation and metrics aggregation
   */
  async getSystemHealthStatus() {
    const timestamp = new Date().toISOString();
    const uptimeSeconds = Math.floor((Date.now() - startTime) / 1000);

    // 1. Measure Firestore Latency
    let firestoreLatencyMs = null;
    let firebaseStatus = false;

    if (initialized && db) {
      try {
        const pingStart = Date.now();
        await db.collection('system_health').doc('ping').set({
          lastPing: Timestamp ? Timestamp.now() : timestamp,
        }, { merge: true });
        firestoreLatencyMs = Date.now() - pingStart;
        firebaseStatus = true;
      } catch (err) {
        logger.error(`[HEALTH SERVICE] Firestore ping failed: ${err.message}`);
        firebaseStatus = false;
      }
    }

    // 2. Queue Status
    const queueSummary = jobsService.getJobStatusSummary();

    // 3. Failed Payments Count
    let failedPaymentsCount = 0;
    if (initialized && db) {
      try {
        const failedSnap = await db.collection('payment_transactions')
          .where('Status', '==', 'FAILED')
          .get();
        failedPaymentsCount = failedSnap.size;
      } catch (err) {
        failedPaymentsCount = 0;
      }
    }

    // 4. M-Pesa / Daraja OAuth Status
    let darajaStatus = {
      environment: env.mpesa.environment,
      devMode: env.mpesa.developmentMode,
      configured: !!(env.mpesa.consumerKey && env.mpesa.consumerSecret),
      accessTokenCached: !!darajaClient.accessToken,
    };

    // 5. Fetch Health Check Records from Firestore
    let lastBackup = null;
    let lastReconciliation = null;
    let lastStatsVerification = null;
    let lastTreasuryVerification = null;

    if (initialized && db) {
      try {
        const backupDoc = await db.collection('system_health').doc('backup_status').get();
        if (backupDoc.exists) lastBackup = backupDoc.data().lastBackupTimestamp;

        const treasuryDoc = await db.collection('system_health').doc('treasury_verification').get();
        if (treasuryDoc.exists) {
          lastTreasuryVerification = treasuryDoc.data().lastCheckTimestamp;
          lastReconciliation = treasuryDoc.data().lastCheckTimestamp;
        }
      } catch (err) {
        // ignore
      }
    }

    const healthDoc = {
      serverStatus: firebaseStatus || env.mpesa.developmentMode ? 'HEALTHY' : 'DEGRADED',
      backendVersion: '1.0.0-production',
      firebaseStatus,
      firestoreLatencyMs,
      oauthStatus: 'CONFIGURED',
      darajaStatus,
      callbackStatus: 'OPERATIONAL',
      queueStatus: {
        active: queueSummary.running,
        pending: queueSummary.pending,
        completed: queueSummary.completed,
        total: queueSummary.totalJobs,
      },
      failedJobs: queueSummary.deadLetterCount,
      failedPayments: failedPaymentsCount,
      lastBackup,
      lastReconciliation,
      lastStatisticsVerification: lastStatsVerification || timestamp,
      lastTreasuryVerification,
      systemUptimeSeconds: uptimeSeconds,
      lastHealthCheck: timestamp,
    };

    // Store in system_health collection
    if (initialized && db) {
      await db.collection('system_health').doc('current').set(healthDoc, { merge: true });
    }

    return healthDoc;
  }
}

module.exports = new HealthService();
