const { db, initialized, Timestamp } = require('../firebase/admin');
const env = require('../config/env');
const logger = require('../utils/logger');
const crypto = require('crypto');

class BackupService {
  /**
   * Generates MD5 / SHA256 checksum for a data string/object
   */
  generateChecksum(data) {
    const jsonStr = typeof data === 'string' ? data : JSON.stringify(data);
    return crypto.createHash('sha256').update(jsonStr).digest('hex');
  }

  /**
   * 1. Create full Database Snapshot
   */
  async createFullSnapshot({ performedBy = 'ADMIN' } = {}) {
    logger.info('[BACKUP SERVICE] Initiating full database snapshot generation...');

    const backupId = `BKP_${Date.now()}`;
    const timestamp = new Date().toISOString();

    const collectionsToBackup = [
      'users',
      'member_profiles',
      'loans',
      'contributions',
      'repayments',
      'ledger',
      'payment_transactions',
      'summary',
      'audit_logs',
      'notifications',
    ];

    const snapshotData = {};
    let totalDocsCount = 0;

    if (initialized && db) {
      for (const colName of collectionsToBackup) {
        const snap = await db.collection(colName).get();
        snapshotData[colName] = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
        totalDocsCount += snap.size;
      }
    } else {
      snapshotData['mock_summary'] = { availableTreasuryCash: 150000, totalMembers: 5 };
      totalDocsCount = 10;
    }

    const jsonString = JSON.stringify(snapshotData);
    const checksum = this.generateChecksum(jsonString);
    const sizeBytes = Buffer.byteLength(jsonString, 'utf8');

    const backupMetadata = {
      backupId,
      type: 'FULL_SNAPSHOT',
      totalCollections: Object.keys(snapshotData).length,
      totalDocuments: totalDocsCount,
      sizeBytes,
      checksum,
      status: 'COMPLETED',
      performedBy,
      createdAt: Timestamp ? Timestamp.now() : timestamp,
    };

    if (initialized && db) {
      await db.collection('backups').doc(backupId).set({
        ...backupMetadata,
        snapshotData,
      });

      // Update system_health backup timestamp
      await db.collection('system_health').doc('backup_status').set({
        lastBackupTimestamp: timestamp,
        lastBackupId: backupId,
        status: 'HEALTHY',
      }, { merge: true });
    }

    logger.info(`[BACKUP SERVICE SUCCESS] Snapshot #${backupId} created. Size: ${sizeBytes} bytes, Docs: ${totalDocsCount}`);

    return {
      backupId,
      metadata: backupMetadata,
      snapshotData,
    };
  }

  /**
   * 2. Ledger Export
   */
  async exportLedger({ limit = 1000 } = {}) {
    logger.info('[BACKUP SERVICE] Exporting ledger records...');
    let ledgerRecords = [];

    if (initialized && db) {
      const snap = await db.collection('ledger').limit(Number(limit)).get();
      ledgerRecords = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
    } else {
      ledgerRecords = [
        { ledgerId: 'LD_101', transactionType: 'CONTRIBUTION', amount: 5000, date: '2026-07-22' },
      ];
    }

    const checksum = this.generateChecksum(ledgerRecords);

    return {
      exportType: 'LEDGER',
      totalRecords: ledgerRecords.length,
      checksum,
      exportedAt: new Date().toISOString(),
      records: ledgerRecords,
    };
  }

  /**
   * 3. Statistics Export
   */
  async exportStatistics() {
    logger.info('[BACKUP SERVICE] Exporting statistics summary...');
    let stats = {};

    if (initialized && db) {
      const doc = await db.collection('summary').doc('statistics').get();
      if (doc.exists) stats = doc.data();
    } else {
      stats = { availableTreasuryCash: 150000.0, totalMembers: 5, totalContributions: 200000.0 };
    }

    const checksum = this.generateChecksum(stats);

    return {
      exportType: 'STATISTICS',
      checksum,
      exportedAt: new Date().toISOString(),
      statistics: stats,
    };
  }

  /**
   * 4. Configuration Backup
   */
  async backupConfiguration() {
    logger.info('[BACKUP SERVICE] Backing up system configurations...');
    const safeConfig = {
      environment: env.nodeEnv,
      corsOrigin: env.corsOrigin,
      mpesaEnvironment: env.mpesa.environment,
      mpesaShortcode: env.mpesa.shortcode,
      mpesaDevMode: env.mpesa.developmentMode,
      firebaseProject: env.firebase.projectId || 'sinzore-investment-app',
    };

    const checksum = this.generateChecksum(safeConfig);

    return {
      exportType: 'CONFIGURATION',
      checksum,
      backedUpAt: new Date().toISOString(),
      configuration: safeConfig,
    };
  }

  /**
   * 5. Recovery Verification (Checksum validation)
   */
  async verifyBackupIntegrity(backupId) {
    logger.info(`[BACKUP SERVICE] Verifying backup integrity for #${backupId}`);

    if (!initialized || !db) {
      return { backupId, isValid: true, status: 'VERIFIED_DEV_MODE' };
    }

    const doc = await db.collection('backups').doc(backupId).get();
    if (!doc.exists) {
      throw new Error(`Backup #${backupId} not found`);
    }

    const data = doc.data();
    const storedChecksum = data.checksum;
    const recomputedChecksum = this.generateChecksum(data.snapshotData);

    const isValid = storedChecksum === recomputedChecksum;

    return {
      backupId,
      isValid,
      storedChecksum,
      recomputedChecksum,
      totalDocuments: data.totalDocuments,
      createdAt: data.createdAt,
    };
  }

  /**
   * Fetch list of backup metadata
   */
  async getBackupHistory({ limit = 20 } = {}) {
    if (!initialized || !db) return [];

    const snap = await db.collection('backups').limit(Number(limit)).get();
    return snap.docs.map(doc => {
      const data = doc.data();
      return {
        backupId: data.backupId || doc.id,
        type: data.type,
        totalDocuments: data.totalDocuments,
        sizeBytes: data.sizeBytes,
        checksum: data.checksum,
        status: data.status,
        performedBy: data.performedBy,
        createdAt: data.createdAt,
      };
    });
  }
}

module.exports = new BackupService();
