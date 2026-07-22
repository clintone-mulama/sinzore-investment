const { db, initialized, FieldValue, Timestamp } = require('../firebase/admin');
const logger = require('../utils/logger');

class FirebaseService {
  /**
   * Retrieves or initializes the system summary document
   */
  async getSummaryStatistics() {
    if (!initialized || !db) {
      logger.warn('[FIREBASE SERVICE] Database not initialized, returning mock summary document');
      return {
        availableTreasuryCash: 1250000.0,
        outstandingLoanBalance: 320000.0,
        totalAssets: 1570000.0,
        totalContributions: 2100000.0,
        totalLoans: 850000.0,
        totalRepayments: 530000.0,
        pendingLoans: 2,
        approvedLoans: 5,
        clearedLoans: 12,
        totalMembers: 48,
        updatedAt: new Date().toISOString(),
      };
    }

    try {
      const summaryRef = db.collection('summary').doc('statistics');
      const doc = await summaryRef.get();

      if (!doc.exists) {
        const defaultSummary = {
          availableTreasuryCash: 0.0,
          outstandingLoanBalance: 0.0,
          totalAssets: 0.0,
          totalContributions: 0.0,
          totalLoans: 0.0,
          totalRepayments: 0.0,
          pendingLoans: 0,
          approvedLoans: 0,
          clearedLoans: 0,
          totalMembers: 0,
          updatedAt: Timestamp.now(),
        };
        await summaryRef.set(defaultSummary);
        return defaultSummary;
      }

      return doc.data();
    } catch (error) {
      logger.error(`[FIREBASE SERVICE] Error reading summary statistics: ${error.message}`);
      throw error;
    }
  }

  /**
   * Atomically updates summary statistics document and writes a ledger entry inside a Firestore transaction
   */
  async updateSummaryAndLedger({ transactionType, amount, memberId, memberName, referenceId, description }) {
    if (!initialized || !db) {
      logger.info(`[FIREBASE SERVICE] Transaction simulated in dev mode: ${transactionType} - Ksh ${amount}`);
      return { success: true, simulated: true };
    }

    try {
      await db.runTransaction(async (transaction) => {
        const summaryRef = db.collection('summary').doc('statistics');
        const summaryDoc = await transaction.get(summaryRef);

        let currentData = summaryDoc.exists ? summaryDoc.data() : {
          availableTreasuryCash: 0.0,
          outstandingLoanBalance: 0.0,
          totalAssets: 0.0,
          totalContributions: 0.0,
          totalLoans: 0.0,
          totalRepayments: 0.0,
          pendingLoans: 0,
          approvedLoans: 0,
          clearedLoans: 0,
          totalMembers: 0,
        };

        const numericAmount = Number(amount);
        let cashDelta = 0;
        let loanBalanceDelta = 0;
        let totalContributionsDelta = 0;
        let totalRepaymentsDelta = 0;

        switch (transactionType) {
          case 'CONTRIBUTION':
            cashDelta = numericAmount;
            totalContributionsDelta = numericAmount;
            break;

          case 'LOAN_DISBURSAL':
            cashDelta = -numericAmount;
            loanBalanceDelta = numericAmount;
            break;

          case 'LOAN_REPAYMENT':
            cashDelta = numericAmount;
            loanBalanceDelta = -numericAmount;
            totalRepaymentsDelta = numericAmount;
            break;

          case 'TREASURY_UPDATE':
            cashDelta = numericAmount;
            break;

          default:
            break;
        }

        const newCash = (currentData.availableTreasuryCash || 0) + cashDelta;
        const newOutstanding = Math.max(0, (currentData.outstandingLoanBalance || 0) + loanBalanceDelta);
        const newTotalAssets = newCash + newOutstanding;

        // 1. Update summary document
        transaction.set(summaryRef, {
          availableTreasuryCash: newCash,
          outstandingLoanBalance: newOutstanding,
          totalAssets: newTotalAssets,
          totalContributions: (currentData.totalContributions || 0) + totalContributionsDelta,
          totalRepayments: (currentData.totalRepayments || 0) + totalRepaymentsDelta,
          updatedAt: Timestamp.now(),
        }, { merge: true });

        // 2. Write ledger entry
        const ledgerRef = db.collection('ledger').doc();
        transaction.set(ledgerRef, {
          transactionType,
          amount: numericAmount,
          memberId: memberId || 'SYSTEM',
          memberName: memberName || 'System Admin',
          referenceId: referenceId || ledgerRef.id,
          description: description || `${transactionType} transaction`,
          createdAt: Timestamp.now(),
        });
      });

      logger.info(`[FIREBASE TRANSACTION SUCCESS] Type: ${transactionType}, Amount: ${amount}`);
      return { success: true };
    } catch (error) {
      logger.error(`[FIREBASE TRANSACTION ERROR] ${error.message}`);
      throw error;
    }
  }

  /**
   * Writes an audit log entry
   */
  async logAuditEvent({ action, userId, userEmail, details, status = 'SUCCESS' }) {
    if (!initialized || !db) return;

    try {
      await db.collection('audit_logs').add({
        action,
        userId: userId || 'ANONYMOUS',
        userEmail: userEmail || 'N/A',
        details: details || {},
        status,
        timestamp: Timestamp.now(),
      });
    } catch (error) {
      logger.error(`[AUDIT LOG ERROR] ${error.message}`);
    }
  }
}

module.exports = new FirebaseService();
