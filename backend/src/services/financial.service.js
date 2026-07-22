const { db, initialized, Timestamp } = require('../firebase/admin');
const logger = require('../utils/logger');

class FinancialService {
  /**
   * Returns default structure for statistics summary document
   */
  getDefaultSummary() {
    return {
      availableTreasuryCash: 0.0,
      outstandingLoans: 0.0,
      totalAssets: 0.0,
      totalContributions: 0.0,
      totalLoanRepayments: 0.0,
      totalLoansDisbursed: 0.0,
      totalInterestEarned: 0.0,
      pendingLoans: 0,
      approvedLoans: 0,
      deniedLoans: 0,
      clearedLoans: 0,
      totalMembers: 0,
      lastUpdated: Timestamp.now(),
    };
  }

  /**
   * Returns default structure for member financial profile
   */
  getDefaultMemberProfile(memberId, memberName) {
    return {
      memberId,
      memberName: memberName || 'Member',
      totalContributions: 0.0,
      totalLoans: 0.0,
      outstandingBalance: 0.0,
      totalRepaid: 0.0,
      interestPaid: 0.0,
      activeLoanId: null,
      lastActivity: Timestamp.now(),
      createdAt: Timestamp.now(),
      updatedAt: Timestamp.now(),
    };
  }

  /**
   * Helper to format current date and time strings
   */
  getFormattedDateTime() {
    const now = new Date();
    return {
      date: now.toISOString().split('T')[0],
      exactDate: now.toLocaleDateString('en-KE', { year: 'numeric', month: 'long', day: 'numeric' }),
      exactTime: now.toLocaleTimeString('en-KE', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
    };
  }

  /**
   * 1. Initialize Treasury Balance
   */
  async initializeTreasuryBalance({ amount, performedBy = 'ADMIN' }) {
    const initialAmount = Number(amount);
    if (isNaN(initialAmount) || initialAmount < 0) {
      throw new Error('Initial treasury amount must be a non-negative number');
    }

    logger.info(`[FINANCIAL CORE] Initializing Treasury Balance: Ksh ${initialAmount}`);

    if (!initialized || !db) {
      logger.warn('[FINANCIAL CORE] Dev Mode: Simulated Treasury Initialization');
      return { success: true, availableTreasuryCash: initialAmount };
    }

    try {
      return await db.runTransaction(async (transaction) => {
        const summaryRef = db.collection('summary').doc('statistics');
        const summaryDoc = await transaction.get(summaryRef);

        let current = summaryDoc.exists ? summaryDoc.data() : this.getDefaultSummary();
        const before = current.availableTreasuryCash || 0;
        const after = initialAmount;

        current.availableTreasuryCash = after;
        current.totalAssets = after + (current.outstandingLoans || 0);
        current.lastUpdated = Timestamp.now();

        transaction.set(summaryRef, current, { merge: true });

        // Ledger entry
        const ledgerRef = db.collection('ledger').doc();
        const { date, exactDate, exactTime } = this.getFormattedDateTime();
        transaction.set(ledgerRef, {
          ledgerId: ledgerRef.id,
          transactionType: 'INITIALIZE_TREASURY',
          memberId: 'SYSTEM',
          memberName: 'System Treasury',
          performedBy,
          amount: initialAmount,
          treasuryBalanceBefore: before,
          treasuryBalanceAfter: after,
          receiptNumber: `INIT_${Date.now()}`,
          paymentMethod: 'SYSTEM_SETTLEMENT',
          referenceId: 'INIT_TREASURY_REF',
          notes: 'Initial treasury balance setup',
          date,
          exactDate,
          exactTime,
          serverTimestamp: Timestamp.now(),
        });

        // Audit Log
        const auditRef = db.collection('audit_logs').doc();
        transaction.set(auditRef, {
          action: 'INITIALIZE_TREASURY',
          performedBy,
          targetMember: 'SYSTEM',
          targetDocument: 'summary/statistics',
          timestamp: Timestamp.now(),
          result: 'SUCCESS',
          details: { initialAmount, before, after },
        });

        return { success: true, availableTreasuryCash: after };
      });
    } catch (error) {
      logger.error(`[FINANCIAL CORE ERROR] initializeTreasuryBalance failed: ${error.message}`);
      throw error;
    }
  }

  /**
   * 2. Record Contribution / Manual Contribution
   */
  async recordContribution({
    memberId,
    memberName,
    amount,
    paymentMethod = 'MPESA',
    receiptNumber = '',
    referenceId = '',
    notes = 'Investment Contribution',
    performedBy = 'MEMBER',
    isManual = false,
  }) {
    const numericAmount = Number(amount);
    if (!numericAmount || numericAmount <= 0) {
      throw new Error('Contribution amount must be a positive number');
    }

    if (!memberId) {
      throw new Error('Valid Member ID is required for recording contribution');
    }

    const cleanReceipt = (receiptNumber || referenceId || '').trim();

    logger.info(`[FINANCIAL CORE] Recording ${isManual ? 'Manual ' : ''}Contribution: ${memberName} (${memberId}), Amount: Ksh ${numericAmount}`);

    if (!initialized || !db) {
      return { success: true, contributionId: `CT_${Date.now()}`, amount: numericAmount };
    }

    try {
      return await db.runTransaction(async (transaction) => {
        // Check for duplicate receipt/reference lock
        if (cleanReceipt) {
          const receiptLockRef = db.collection('processed_receipts').doc(cleanReceipt);
          const receiptLockDoc = await transaction.get(receiptLockRef);
          if (receiptLockDoc.exists) {
            throw new Error(`Duplicate transaction or receipt number detected: ${cleanReceipt}`);
          }
          // Lock the receipt number
          transaction.set(receiptLockRef, {
            receiptNumber: cleanReceipt,
            memberId,
            amount: numericAmount,
            processedAt: Timestamp.now(),
          });
        }

        // 1. Get summary
        const summaryRef = db.collection('summary').doc('statistics');
        const summaryDoc = await transaction.get(summaryRef);

        let currentSummary = summaryDoc.exists ? summaryDoc.data() : this.getDefaultSummary();
        const before = currentSummary.availableTreasuryCash || 0;
        const after = before + numericAmount;

        currentSummary.availableTreasuryCash = after;
        currentSummary.totalContributions = (currentSummary.totalContributions || 0) + numericAmount;
        currentSummary.totalAssets = after + (currentSummary.outstandingLoans || 0);
        currentSummary.lastUpdated = Timestamp.now();

        transaction.set(summaryRef, currentSummary, { merge: true });

        // 2. Get / Update Member Financial Profile
        const memberProfileRef = db.collection('member_profiles').doc(memberId);
        const memberProfileDoc = await transaction.get(memberProfileRef);
        let profile = memberProfileDoc.exists
          ? memberProfileDoc.data()
          : this.getDefaultMemberProfile(memberId, memberName);

        profile.memberName = memberName || profile.memberName;
        profile.totalContributions = (profile.totalContributions || 0) + numericAmount;
        profile.lastActivity = Timestamp.now();
        profile.updatedAt = Timestamp.now();

        transaction.set(memberProfileRef, profile, { merge: true });

        // 3. Record Contribution Document
        const contributionRef = db.collection('contributions').doc();
        const transactionType = isManual ? 'MANUAL_CONTRIBUTION' : 'CONTRIBUTION';
        const finalReceipt = cleanReceipt || contributionRef.id;

        transaction.set(contributionRef, {
          contributionId: contributionRef.id,
          memberId,
          memberName: profile.memberName,
          amount: numericAmount,
          paymentMethod,
          receiptNumber: finalReceipt,
          referenceId: referenceId || finalReceipt,
          notes,
          performedBy,
          createdAt: Timestamp.now(),
        });

        // 4. Ledger Entry
        const ledgerRef = db.collection('ledger').doc();
        const { date, exactDate, exactTime } = this.getFormattedDateTime();
        transaction.set(ledgerRef, {
          ledgerId: ledgerRef.id,
          transactionType,
          memberId,
          memberName: profile.memberName,
          performedBy,
          amount: numericAmount,
          treasuryBalanceBefore: before,
          treasuryBalanceAfter: after,
          receiptNumber: finalReceipt,
          paymentMethod,
          referenceId: referenceId || finalReceipt,
          notes,
          date,
          exactDate,
          exactTime,
          serverTimestamp: Timestamp.now(),
        });

        // 5. Audit Log
        const auditRef = db.collection('audit_logs').doc();
        transaction.set(auditRef, {
          action: transactionType,
          performedBy,
          targetMember: memberId,
          targetDocument: `contributions/${contributionRef.id}`,
          timestamp: Timestamp.now(),
          result: 'SUCCESS',
          details: { amount: numericAmount, receiptNumber: finalReceipt },
        });

        return {
          success: true,
          contributionId: contributionRef.id,
          receiptNumber: finalReceipt,
          amount: numericAmount,
          treasuryBalanceAfter: after,
          memberTotalContributions: profile.totalContributions,
        };
      });
    } catch (error) {
      logger.error(`[FINANCIAL CORE ERROR] recordContribution failed: ${error.message}`);
      throw error;
    }
  }

  /**
   * 3. Apply for Loan (Member)
   */
  async applyLoan({
    memberId,
    memberName,
    principalAmount,
    durationMonths = 1,
    purpose = 'General Loan',
    performedBy = 'MEMBER',
  }) {
    const principal = Number(principalAmount);
    if (!principal || principal <= 0) {
      throw new Error('Principal amount must be a positive number');
    }

    if (!memberId) {
      throw new Error('Member ID is required to apply for a loan');
    }

    logger.info(`[FINANCIAL CORE] Member applying for loan: ${memberName} (${memberId}), Principal: Ksh ${principal}`);

    if (!initialized || !db) {
      return { success: true, loanId: `LN_${Date.now()}`, status: 'PENDING' };
    }

    // Check if member already has an active or pending loan
    const existingActiveQuery = await db.collection('loans')
      .where('memberId', '==', memberId)
      .where('status', 'in', ['PENDING', 'APPROVED', 'DISBURSED'])
      .get();

    if (!existingActiveQuery.empty) {
      throw new Error('Member already has an active loan application or outstanding loan balance.');
    }

    try {
      return await db.runTransaction(async (transaction) => {
        const interestRate = 10.0;
        const totalRepayment = principal * (1 + interestRate / 100);

        // Update Statistics summary pending count
        const summaryRef = db.collection('summary').doc('statistics');
        const summaryDoc = await transaction.get(summaryRef);
        let currentSummary = summaryDoc.exists ? summaryDoc.data() : this.getDefaultSummary();

        currentSummary.pendingLoans = (currentSummary.pendingLoans || 0) + 1;
        currentSummary.lastUpdated = Timestamp.now();
        transaction.set(summaryRef, currentSummary, { merge: true });

        // Create Loan document
        const loanRef = db.collection('loans').doc();
        transaction.set(loanRef, {
          loanId: loanRef.id,
          memberId,
          memberName,
          principalAmount: principal,
          interestRate,
          totalRepayment,
          balanceRemaining: totalRepayment,
          durationMonths: Number(durationMonths),
          purpose,
          status: 'PENDING',
          appliedAt: Timestamp.now(),
          appliedBy: performedBy,
          createdAt: Timestamp.now(),
        });

        // Audit Log
        const auditRef = db.collection('audit_logs').doc();
        transaction.set(auditRef, {
          action: 'LOAN_APPLICATION_SUBMITTED',
          performedBy,
          targetMember: memberId,
          targetDocument: `loans/${loanRef.id}`,
          timestamp: Timestamp.now(),
          result: 'SUCCESS',
          details: { loanId: loanRef.id, principalAmount: principal, totalRepayment },
        });

        return {
          success: true,
          loanId: loanRef.id,
          status: 'PENDING',
          principalAmount: principal,
          totalRepayment,
        };
      });
    } catch (error) {
      logger.error(`[FINANCIAL CORE ERROR] applyLoan failed: ${error.message}`);
      throw error;
    }
  }

  /**
   * 4. Approve Loan (Treasurer / Admin)
   */
  async approveLoan({ loanId, performedBy = 'ADMIN' }) {
    if (!loanId) throw new Error('Loan ID is required for approval');
    logger.info(`[FINANCIAL CORE] Approving Loan: ${loanId} by ${performedBy}`);

    if (!initialized || !db) {
      return { success: true, loanId, status: 'APPROVED' };
    }

    try {
      return await db.runTransaction(async (transaction) => {
        const loanRef = db.collection('loans').doc(loanId);
        const loanDoc = await transaction.get(loanRef);

        if (!loanDoc.exists) {
          throw new Error(`Loan with ID ${loanId} does not exist`);
        }

        const loan = loanDoc.data();
        if (loan.status === 'APPROVED' || loan.status === 'DISBURSED') {
          throw new Error(`Loan #${loanId} is already approved or disbursed`);
        }
        if (loan.status === 'DENIED') {
          throw new Error(`Cannot approve loan #${loanId} because it was previously denied`);
        }
        if (loan.status === 'CLEARED') {
          throw new Error(`Cannot approve loan #${loanId} because it is already cleared`);
        }

        const principal = Number(loan.principalAmount) || 0;
        const totalRepayment = Number(loan.totalRepayment) || (principal * 1.10);

        // Check Treasury Cash
        const summaryRef = db.collection('summary').doc('statistics');
        const summaryDoc = await transaction.get(summaryRef);

        let currentSummary = summaryDoc.exists ? summaryDoc.data() : this.getDefaultSummary();
        const before = currentSummary.availableTreasuryCash || 0;

        if (before < principal) {
          throw new Error(`Insufficient treasury cash to disburse loan. Available: Ksh ${before}, Requested: Ksh ${principal}`);
        }

        const after = before - principal;

        // Update Summary
        currentSummary.availableTreasuryCash = after;
        currentSummary.outstandingLoans = (currentSummary.outstandingLoans || 0) + totalRepayment;
        currentSummary.totalLoansDisbursed = (currentSummary.totalLoansDisbursed || 0) + principal;
        currentSummary.totalAssets = after + currentSummary.outstandingLoans;
        currentSummary.pendingLoans = Math.max(0, (currentSummary.pendingLoans || 1) - 1);
        currentSummary.approvedLoans = (currentSummary.approvedLoans || 0) + 1;
        currentSummary.lastUpdated = Timestamp.now();

        transaction.set(summaryRef, currentSummary, { merge: true });

        // Update Member Financial Profile
        const memberProfileRef = db.collection('member_profiles').doc(loan.memberId);
        const memberProfileDoc = await transaction.get(memberProfileRef);
        let profile = memberProfileDoc.exists
          ? memberProfileDoc.data()
          : this.getDefaultMemberProfile(loan.memberId, loan.memberName);

        profile.totalLoans = (profile.totalLoans || 0) + principal;
        profile.outstandingBalance = (profile.outstandingBalance || 0) + totalRepayment;
        profile.activeLoanId = loanId;
        profile.lastActivity = Timestamp.now();
        profile.updatedAt = Timestamp.now();

        transaction.set(memberProfileRef, profile, { merge: true });

        // Update Loan Document
        transaction.update(loanRef, {
          status: 'APPROVED',
          balanceRemaining: totalRepayment,
          approvedAt: Timestamp.now(),
          approvedBy: performedBy,
          updatedAt: Timestamp.now(),
        });

        // Ledger Entry
        const ledgerRef = db.collection('ledger').doc();
        const { date, exactDate, exactTime } = this.getFormattedDateTime();
        transaction.set(ledgerRef, {
          ledgerId: ledgerRef.id,
          transactionType: 'LOAN_DISBURSAL',
          memberId: loan.memberId,
          memberName: loan.memberName,
          performedBy,
          amount: principal,
          treasuryBalanceBefore: before,
          treasuryBalanceAfter: after,
          receiptNumber: `DISB_${loanId}`,
          paymentMethod: 'SYSTEM_TRANSFER',
          referenceId: loanId,
          notes: `Loan Disbursal for Loan #${loanId}`,
          date,
          exactDate,
          exactTime,
          serverTimestamp: Timestamp.now(),
        });

        // Audit Log
        const auditRef = db.collection('audit_logs').doc();
        transaction.set(auditRef, {
          action: 'LOAN_APPROVED',
          performedBy,
          targetMember: loan.memberId,
          targetDocument: `loans/${loanId}`,
          timestamp: Timestamp.now(),
          result: 'SUCCESS',
          details: { loanId, principalAmount: principal, totalRepayment, treasuryBalanceAfter: after },
        });

        return {
          success: true,
          loanId,
          status: 'APPROVED',
          principalAmount: principal,
          totalRepayment,
          treasuryBalanceAfter: after,
        };
      });
    } catch (error) {
      logger.error(`[FINANCIAL CORE ERROR] approveLoan failed: ${error.message}`);
      throw error;
    }
  }

  /**
   * 5. Deny Loan (Treasurer / Admin)
   */
  async denyLoan({ loanId, reason = 'Requirements not met', performedBy = 'ADMIN' }) {
    if (!loanId) throw new Error('Loan ID is required for denial');
    logger.info(`[FINANCIAL CORE] Denying Loan: ${loanId} by ${performedBy}`);

    if (!initialized || !db) {
      return { success: true, loanId, status: 'DENIED' };
    }

    try {
      return await db.runTransaction(async (transaction) => {
        const loanRef = db.collection('loans').doc(loanId);
        const loanDoc = await transaction.get(loanRef);

        if (!loanDoc.exists) {
          throw new Error(`Loan with ID ${loanId} does not exist`);
        }

        const loan = loanDoc.data();
        if (loan.status !== 'PENDING') {
          throw new Error(`Cannot deny loan #${loanId} because its status is '${loan.status}'`);
        }

        // Update statistics pending & denied counts
        const summaryRef = db.collection('summary').doc('statistics');
        const summaryDoc = await transaction.get(summaryRef);
        let currentSummary = summaryDoc.exists ? summaryDoc.data() : this.getDefaultSummary();

        currentSummary.pendingLoans = Math.max(0, (currentSummary.pendingLoans || 1) - 1);
        currentSummary.deniedLoans = (currentSummary.deniedLoans || 0) + 1;
        currentSummary.lastUpdated = Timestamp.now();

        transaction.set(summaryRef, currentSummary, { merge: true });

        // Update Loan Document
        transaction.update(loanRef, {
          status: 'DENIED',
          denialReason: reason,
          deniedAt: Timestamp.now(),
          deniedBy: performedBy,
          updatedAt: Timestamp.now(),
        });

        // Audit Log
        const auditRef = db.collection('audit_logs').doc();
        transaction.set(auditRef, {
          action: 'LOAN_DENIED',
          performedBy,
          targetMember: loan.memberId,
          targetDocument: `loans/${loanId}`,
          timestamp: Timestamp.now(),
          result: 'SUCCESS',
          details: { loanId, reason },
        });

        return { success: true, loanId, status: 'DENIED', reason };
      });
    } catch (error) {
      logger.error(`[FINANCIAL CORE ERROR] denyLoan failed: ${error.message}`);
      throw error;
    }
  }

  /**
   * 6. Record Manual Loan (Treasurer / Admin)
   */
  async recordManualLoan({
    memberId,
    memberName,
    principalAmount,
    interestRate = 10.0,
    durationMonths = 1,
    purpose = 'General Loan',
    paymentMethod = 'CASH',
    referenceId = '',
    notes = 'Manual Loan Entry',
    performedBy = 'ADMIN',
  }) {
    const principal = Number(principalAmount);
    if (!principal || principal <= 0) {
      throw new Error('Principal amount must be greater than zero');
    }

    if (!memberId) {
      throw new Error('Member ID is required to issue a manual loan');
    }

    logger.info(`[FINANCIAL CORE] Recording Manual Loan for ${memberName} (${memberId}), Principal: Ksh ${principal}`);

    if (!initialized || !db) {
      return { success: true, loanId: `LN_MN_${Date.now()}` };
    }

    // Check if member already has an active loan
    const existingActiveQuery = await db.collection('loans')
      .where('memberId', '==', memberId)
      .where('status', 'in', ['APPROVED', 'DISBURSED'])
      .get();

    if (!existingActiveQuery.empty) {
      throw new Error('Member already has an active outstanding loan');
    }

    try {
      return await db.runTransaction(async (transaction) => {
        const summaryRef = db.collection('summary').doc('statistics');
        const summaryDoc = await transaction.get(summaryRef);

        let currentSummary = summaryDoc.exists ? summaryDoc.data() : this.getDefaultSummary();
        const before = currentSummary.availableTreasuryCash || 0;

        if (before < principal) {
          throw new Error(`Insufficient treasury cash to issue manual loan. Available: Ksh ${before}, Required: Ksh ${principal}`);
        }

        const after = before - principal;
        const totalRepayment = principal * (1 + Number(interestRate) / 100);

        currentSummary.availableTreasuryCash = after;
        currentSummary.outstandingLoans = (currentSummary.outstandingLoans || 0) + totalRepayment;
        currentSummary.totalLoansDisbursed = (currentSummary.totalLoansDisbursed || 0) + principal;
        currentSummary.totalAssets = after + currentSummary.outstandingLoans;
        currentSummary.approvedLoans = (currentSummary.approvedLoans || 0) + 1;
        currentSummary.lastUpdated = Timestamp.now();

        transaction.set(summaryRef, currentSummary, { merge: true });

        // Record Manual Loan Document
        const loanRef = db.collection('loans').doc();
        transaction.set(loanRef, {
          loanId: loanRef.id,
          memberId,
          memberName,
          principalAmount: principal,
          interestRate: Number(interestRate),
          totalRepayment,
          balanceRemaining: totalRepayment,
          durationMonths: Number(durationMonths),
          purpose,
          status: 'APPROVED',
          isManual: true,
          paymentMethod,
          referenceId: referenceId || loanRef.id,
          notes,
          performedBy,
          createdAt: Timestamp.now(),
        });

        // Update Member Financial Profile
        const memberProfileRef = db.collection('member_profiles').doc(memberId);
        const memberProfileDoc = await transaction.get(memberProfileRef);
        let profile = memberProfileDoc.exists
          ? memberProfileDoc.data()
          : this.getDefaultMemberProfile(memberId, memberName);

        profile.totalLoans = (profile.totalLoans || 0) + principal;
        profile.outstandingBalance = (profile.outstandingBalance || 0) + totalRepayment;
        profile.activeLoanId = loanRef.id;
        profile.lastActivity = Timestamp.now();
        profile.updatedAt = Timestamp.now();

        transaction.set(memberProfileRef, profile, { merge: true });

        // Ledger Entry
        const ledgerRef = db.collection('ledger').doc();
        const { date, exactDate, exactTime } = this.getFormattedDateTime();
        transaction.set(ledgerRef, {
          ledgerId: ledgerRef.id,
          transactionType: 'MANUAL_LOAN_DISBURSAL',
          memberId,
          memberName,
          performedBy,
          amount: principal,
          treasuryBalanceBefore: before,
          treasuryBalanceAfter: after,
          receiptNumber: referenceId || loanRef.id,
          paymentMethod,
          referenceId: loanRef.id,
          notes,
          date,
          exactDate,
          exactTime,
          serverTimestamp: Timestamp.now(),
        });

        // Audit Log
        const auditRef = db.collection('audit_logs').doc();
        transaction.set(auditRef, {
          action: 'MANUAL_LOAN_DISBURSED',
          performedBy,
          targetMember: memberId,
          targetDocument: `loans/${loanRef.id}`,
          timestamp: Timestamp.now(),
          result: 'SUCCESS',
          details: { loanId: loanRef.id, principal, totalRepayment },
        });

        return {
          success: true,
          loanId: loanRef.id,
          totalRepayment,
          treasuryBalanceAfter: after,
        };
      });
    } catch (error) {
      logger.error(`[FINANCIAL CORE ERROR] recordManualLoan failed: ${error.message}`);
      throw error;
    }
  }

  /**
   * 7. Record Loan Repayment (Member / Treasurer)
   */
  async recordLoanRepayment({
    loanId,
    memberId,
    memberName,
    amount,
    paymentMethod = 'MPESA',
    receiptNumber = '',
    referenceId = '',
    notes = 'Loan Repayment',
    performedBy = 'MEMBER',
  }) {
    const repaymentAmount = Number(amount);
    if (!repaymentAmount || repaymentAmount <= 0) {
      throw new Error('Repayment amount must be a positive number');
    }

    if (!loanId) {
      throw new Error('Target Loan ID is required for recording repayment');
    }

    const cleanReceipt = (receiptNumber || referenceId || '').trim();

    logger.info(`[FINANCIAL CORE] Recording Loan Repayment for Loan #${loanId}, Amount: Ksh ${repaymentAmount}`);

    if (!initialized || !db) {
      return { success: true, repaymentId: `RP_${Date.now()}`, amount: repaymentAmount };
    }

    try {
      return await db.runTransaction(async (transaction) => {
        // Check for duplicate receipt/reference lock
        if (cleanReceipt) {
          const receiptLockRef = db.collection('processed_receipts').doc(cleanReceipt);
          const receiptLockDoc = await transaction.get(receiptLockRef);
          if (receiptLockDoc.exists) {
            throw new Error(`Duplicate repayment transaction or receipt number detected: ${cleanReceipt}`);
          }
          // Lock the receipt number
          transaction.set(receiptLockRef, {
            receiptNumber: cleanReceipt,
            loanId,
            amount: repaymentAmount,
            processedAt: Timestamp.now(),
          });
        }

        // Fetch target loan
        const loanRef = db.collection('loans').doc(loanId);
        const loanDoc = await transaction.get(loanRef);

        if (!loanDoc.exists) {
          throw new Error(`Loan #${loanId} does not exist`);
        }

        const loan = loanDoc.data();
        if (loan.status !== 'APPROVED' && loan.status !== 'DISBURSED') {
          throw new Error(`Cannot record repayment on loan #${loanId} because status is '${loan.status}'`);
        }

        const currentBalance = Number(loan.balanceRemaining !== undefined ? loan.balanceRemaining : loan.totalRepayment) || 0;
        if (repaymentAmount > currentBalance) {
          throw new Error(`Repayment amount (Ksh ${repaymentAmount}) exceeds remaining loan balance of Ksh ${currentBalance}`);
        }

        const targetMemberId = memberId || loan.memberId;
        const targetMemberName = memberName || loan.memberName;

        // Fetch Summary
        const summaryRef = db.collection('summary').doc('statistics');
        const summaryDoc = await transaction.get(summaryRef);

        let currentSummary = summaryDoc.exists ? summaryDoc.data() : this.getDefaultSummary();
        const before = currentSummary.availableTreasuryCash || 0;
        const after = before + repaymentAmount;

        const currentOutstanding = currentSummary.outstandingLoans || 0;
        const newOutstanding = Math.max(0, currentOutstanding - repaymentAmount);
        const newBalance = Math.max(0, currentBalance - repaymentAmount);
        const isLoanCleared = newBalance === 0;

        currentSummary.availableTreasuryCash = after;
        currentSummary.outstandingLoans = newOutstanding;
        currentSummary.totalLoanRepayments = (currentSummary.totalLoanRepayments || 0) + repaymentAmount;
        currentSummary.totalAssets = after + newOutstanding;

        if (isLoanCleared) {
          currentSummary.clearedLoans = (currentSummary.clearedLoans || 0) + 1;
          currentSummary.approvedLoans = Math.max(0, (currentSummary.approvedLoans || 1) - 1);
        }
        currentSummary.lastUpdated = Timestamp.now();

        transaction.set(summaryRef, currentSummary, { merge: true });

        // Update Loan Document
        transaction.update(loanRef, {
          balanceRemaining: newBalance,
          status: isLoanCleared ? 'CLEARED' : loan.status,
          updatedAt: Timestamp.now(),
        });

        // Update Member Financial Profile
        const memberProfileRef = db.collection('member_profiles').doc(targetMemberId);
        const memberProfileDoc = await transaction.get(memberProfileRef);
        let profile = memberProfileDoc.exists
          ? memberProfileDoc.data()
          : this.getDefaultMemberProfile(targetMemberId, targetMemberName);

        profile.totalRepaid = (profile.totalRepaid || 0) + repaymentAmount;
        profile.outstandingBalance = Math.max(0, (profile.outstandingBalance || 0) - repaymentAmount);
        if (isLoanCleared) {
          profile.activeLoanId = null;
        }
        profile.lastActivity = Timestamp.now();
        profile.updatedAt = Timestamp.now();

        transaction.set(memberProfileRef, profile, { merge: true });

        // Record Repayment Document
        const repaymentRef = db.collection('repayments').doc();
        const finalReceipt = cleanReceipt || repaymentRef.id;

        transaction.set(repaymentRef, {
          repaymentId: repaymentRef.id,
          loanId,
          memberId: targetMemberId,
          memberName: targetMemberName,
          amount: repaymentAmount,
          paymentMethod,
          receiptNumber: finalReceipt,
          referenceId: referenceId || finalReceipt,
          notes,
          performedBy,
          createdAt: Timestamp.now(),
        });

        // Ledger Entry
        const ledgerRef = db.collection('ledger').doc();
        const { date, exactDate, exactTime } = this.getFormattedDateTime();
        transaction.set(ledgerRef, {
          ledgerId: ledgerRef.id,
          transactionType: 'LOAN_REPAYMENT',
          memberId: targetMemberId,
          memberName: targetMemberName,
          performedBy,
          amount: repaymentAmount,
          treasuryBalanceBefore: before,
          treasuryBalanceAfter: after,
          receiptNumber: finalReceipt,
          paymentMethod,
          referenceId: loanId,
          notes,
          date,
          exactDate,
          exactTime,
          serverTimestamp: Timestamp.now(),
        });

        // Audit Log
        const auditRef = db.collection('audit_logs').doc();
        transaction.set(auditRef, {
          action: 'LOAN_REPAYMENT_RECORDED',
          performedBy,
          targetMember: targetMemberId,
          targetDocument: `repayments/${repaymentRef.id}`,
          timestamp: Timestamp.now(),
          result: 'SUCCESS',
          details: { loanId, repaymentAmount, isLoanCleared, treasuryBalanceAfter: after },
        });

        return {
          success: true,
          repaymentId: repaymentRef.id,
          receiptNumber: finalReceipt,
          amount: repaymentAmount,
          balanceRemaining: newBalance,
          isLoanCleared,
          treasuryBalanceAfter: after,
        };
      });
    } catch (error) {
      logger.error(`[FINANCIAL CORE ERROR] recordLoanRepayment failed: ${error.message}`);
      throw error;
    }
  }

  /**
   * 8. Clear Loan (Treasurer / Admin)
   */
  async clearLoan({ loanId, notes = 'Loan Cleared', performedBy = 'ADMIN' }) {
    if (!loanId) throw new Error('Loan ID is required');
    logger.info(`[FINANCIAL CORE] Clearing Loan #${loanId} by ${performedBy}`);

    if (!initialized || !db) {
      return { success: true, loanId, status: 'CLEARED' };
    }

    try {
      return await db.runTransaction(async (transaction) => {
        const loanRef = db.collection('loans').doc(loanId);
        const loanDoc = await transaction.get(loanRef);

        if (!loanDoc.exists) throw new Error(`Loan #${loanId} not found`);

        const loan = loanDoc.data();
        const remaining = Number(loan.balanceRemaining || 0);

        const summaryRef = db.collection('summary').doc('statistics');
        const summaryDoc = await transaction.get(summaryRef);

        let currentSummary = summaryDoc.exists ? summaryDoc.data() : this.getDefaultSummary();
        const before = currentSummary.availableTreasuryCash || 0;

        currentSummary.outstandingLoans = Math.max(0, (currentSummary.outstandingLoans || 0) - remaining);
        currentSummary.clearedLoans = (currentSummary.clearedLoans || 0) + 1;
        currentSummary.approvedLoans = Math.max(0, (currentSummary.approvedLoans || 1) - 1);
        currentSummary.totalAssets = before + currentSummary.outstandingLoans;
        currentSummary.lastUpdated = Timestamp.now();

        transaction.set(summaryRef, currentSummary, { merge: true });

        // Update Member Financial Profile
        const memberProfileRef = db.collection('member_profiles').doc(loan.memberId);
        const memberProfileDoc = await transaction.get(memberProfileRef);
        if (memberProfileDoc.exists) {
          const profile = memberProfileDoc.data();
          transaction.set(memberProfileRef, {
            outstandingBalance: Math.max(0, (profile.outstandingBalance || 0) - remaining),
            activeLoanId: null,
            lastActivity: Timestamp.now(),
            updatedAt: Timestamp.now(),
          }, { merge: true });
        }

        // Update Loan
        transaction.update(loanRef, {
          status: 'CLEARED',
          balanceRemaining: 0,
          clearedAt: Timestamp.now(),
          clearedBy: performedBy,
          notes,
        });

        // Audit Log
        const auditRef = db.collection('audit_logs').doc();
        transaction.set(auditRef, {
          action: 'LOAN_CLEARED',
          performedBy,
          targetMember: loan.memberId,
          targetDocument: `loans/${loanId}`,
          timestamp: Timestamp.now(),
          result: 'SUCCESS',
          details: { loanId, remainingWrittenOff: remaining },
        });

        return { success: true, loanId, status: 'CLEARED' };
      });
    } catch (error) {
      logger.error(`[FINANCIAL CORE ERROR] clearLoan failed: ${error.message}`);
      throw error;
    }
  }

  /**
   * 9. Manual Treasury Adjustment
   */
  async manualTreasuryAdjustment({ amount, reason = 'Adjustment', type = 'CREDIT', performedBy = 'ADMIN' }) {
    const adjAmount = Math.abs(Number(amount));
    if (!adjAmount) throw new Error('Adjustment amount must be greater than zero');

    logger.info(`[FINANCIAL CORE] Manual Treasury Adjustment (${type}): Ksh ${adjAmount}`);

    if (!initialized || !db) {
      return { success: true, amount: adjAmount };
    }

    try {
      return await db.runTransaction(async (transaction) => {
        const summaryRef = db.collection('summary').doc('statistics');
        const summaryDoc = await transaction.get(summaryRef);

        let currentSummary = summaryDoc.exists ? summaryDoc.data() : this.getDefaultSummary();
        const before = currentSummary.availableTreasuryCash || 0;

        const isCredit = type.toUpperCase() === 'CREDIT';
        if (!isCredit && before < adjAmount) {
          throw new Error(`Insufficient treasury funds for debit adjustment. Available: Ksh ${before}, Requested: Ksh ${adjAmount}`);
        }

        const after = isCredit ? before + adjAmount : before - adjAmount;

        currentSummary.availableTreasuryCash = after;
        currentSummary.totalAssets = after + (currentSummary.outstandingLoans || 0);
        currentSummary.lastUpdated = Timestamp.now();

        transaction.set(summaryRef, currentSummary, { merge: true });

        // Ledger Entry
        const ledgerRef = db.collection('ledger').doc();
        const { date, exactDate, exactTime } = this.getFormattedDateTime();
        transaction.set(ledgerRef, {
          ledgerId: ledgerRef.id,
          transactionType: isCredit ? 'TREASURY_CREDIT_ADJUSTMENT' : 'TREASURY_DEBIT_ADJUSTMENT',
          memberId: 'SYSTEM',
          memberName: 'Treasury Admin',
          performedBy,
          amount: adjAmount,
          treasuryBalanceBefore: before,
          treasuryBalanceAfter: after,
          receiptNumber: `ADJ_${Date.now()}`,
          paymentMethod: 'MANUAL_ADJUSTMENT',
          referenceId: ledgerRef.id,
          notes: reason,
          date,
          exactDate,
          exactTime,
          serverTimestamp: Timestamp.now(),
        });

        // Audit Log
        const auditRef = db.collection('audit_logs').doc();
        transaction.set(auditRef, {
          action: 'MANUAL_TREASURY_ADJUSTMENT',
          performedBy,
          targetMember: 'SYSTEM',
          targetDocument: 'summary/statistics',
          timestamp: Timestamp.now(),
          result: 'SUCCESS',
          details: { amount: adjAmount, type, reason, before, after },
        });

        return { success: true, availableTreasuryCash: after };
      });
    } catch (error) {
      logger.error(`[FINANCIAL CORE ERROR] manualTreasuryAdjustment failed: ${error.message}`);
      throw error;
    }
  }

  /**
   * 10. Get Member Financial Statement
   */
  async getMemberFinancialStatement(memberId) {
    if (!memberId) throw new Error('Member ID is required to retrieve financial statement');

    if (!initialized || !db) {
      return {
        memberId,
        memberName: 'Dev Member',
        totalContributions: 50000.0,
        totalLoans: 20000.0,
        outstandingBalance: 5000.0,
        totalRepaid: 17000.0,
        interestPaid: 2000.0,
        activeLoan: { loanId: 'LN_DEV_123', status: 'APPROVED', balanceRemaining: 5000.0 },
        recentContributions: [],
        recentRepayments: [],
      };
    }

    try {
      // 1. Get profile document
      const profileDoc = await db.collection('member_profiles').doc(memberId).get();
      let profile = profileDoc.exists ? profileDoc.data() : this.getDefaultMemberProfile(memberId, '');

      // 2. Fetch active loan if any
      let activeLoan = null;
      if (profile.activeLoanId) {
        const loanDoc = await db.collection('loans').doc(profile.activeLoanId).get();
        if (loanDoc.exists) {
          activeLoan = { loanId: loanDoc.id, ...loanDoc.data() };
        }
      }

      // 3. Fetch recent contributions (last 10)
      const contribSnap = await db.collection('contributions')
        .where('memberId', '==', memberId)
        .limit(10)
        .get();
      const recentContributions = contribSnap.docs.map(doc => ({ id: doc.id, ...doc.data() }));

      // 4. Fetch recent repayments (last 10)
      const repaymentSnap = await db.collection('repayments')
        .where('memberId', '==', memberId)
        .limit(10)
        .get();
      const recentRepayments = repaymentSnap.docs.map(doc => ({ id: doc.id, ...doc.data() }));

      return {
        ...profile,
        activeLoan,
        recentContributions,
        recentRepayments,
      };
    } catch (error) {
      logger.error(`[FINANCIAL CORE ERROR] getMemberFinancialStatement failed: ${error.message}`);
      throw error;
    }
  }
}

module.exports = new FinancialService();
