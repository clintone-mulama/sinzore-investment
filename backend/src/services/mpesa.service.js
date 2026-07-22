const darajaClient = require('../mpesa/daraja');
const financialService = require('./financial.service');
const firebaseService = require('./firebase.service');
const { db, initialized, Timestamp } = require('../firebase/admin');
const env = require('../config/env');
const logger = require('../utils/logger');

// In-memory fallback for dev mode when Firebase DB is not initialized
const inMemoryTransactions = new Map();

class MpesaService {
  /**
   * Generates formatted Date and Time strings
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
   * Helper to generate unique correlation IDs
   */
  generateCorrelationId(prefix = 'MP') {
    const rand = Math.random().toString(36).substring(2, 8).toUpperCase();
    return `${prefix}_${Date.now()}_${rand}`;
  }

  /**
   * 1. INITIATE STK PUSH (Production & Dev Mode)
   */
  async processStkPush({
    phoneNumber,
    amount,
    accountReference,
    transactionDesc,
    userId,
    memberName,
    type = 'CONTRIBUTION',
    loanId = null,
    correlationId = null,
  }) {
    const cid = correlationId || this.generateCorrelationId('STK');
    const logPrefix = `[${cid}]`;

    const numericAmount = Number(amount);
    if (!phoneNumber || isNaN(numericAmount) || numericAmount <= 0) {
      throw new Error('Valid phone number and positive amount are required');
    }

    const isDevMode = env.mpesa.developmentMode;
    const formattedPhone = darajaClient.formatPhoneNumber(phoneNumber);
    const { date, exactDate, exactTime } = this.getFormattedDateTime();

    logger.info(`${logPrefix} [MPESA SERVICE] Initiating STK Push (${isDevMode ? 'DEV MODE' : 'PRODUCTION MODE'}) for Member: ${userId}, Phone: ${formattedPhone}, Amount: Ksh ${numericAmount}, Type: ${type}`);

    // Create unique payment_transactions document ID
    const transactionId = `TX_${Date.now()}_${Math.random().toString(36).substring(2, 6).toUpperCase()}`;

    let merchantRequestId = `MR_${Date.now()}`;
    let checkoutRequestId = `WS_${Date.now()}`;

    // Construct base payment_transactions record
    const transactionData = {
      transactionId,
      memberId: userId || 'ANONYMOUS',
      memberName: memberName || 'Member',
      phoneNumber: formattedPhone,
      transactionType: type, // CONTRIBUTION | REPAYMENT
      loanId: loanId || null,
      amount: numericAmount,
      MerchantRequestID: merchantRequestId,
      CheckoutRequestID: checkoutRequestId,
      ConversationID: null,
      OriginatorConversationID: null,
      MpesaReceiptNumber: null,
      ResultCode: null,
      ResultDescription: null,
      Status: 'PENDING',
      retryCount: 0,
      isCallbackReceived: false,
      callbackPayload: null,
      isDevMode,
      correlationId: cid,
      createdAt: Timestamp ? Timestamp.now() : new Date().toISOString(),
      updatedAt: Timestamp ? Timestamp.now() : new Date().toISOString(),
      exactDate,
      exactTime,
      serverTimestamp: Timestamp ? Timestamp.now() : new Date().toISOString(),
    };

    // Save initial PENDING document to Firestore
    if (initialized && db) {
      await db.collection('payment_transactions').doc(transactionId).set(transactionData);
    } else {
      inMemoryTransactions.set(transactionId, transactionData);
    }

    logger.info(`${logPrefix} [PAYMENT TRANSACTIONS] Recorded PENDING transaction #${transactionId}`);

    // Execute STK Request based on mode
    if (isDevMode) {
      logger.info(`${logPrefix} [STK DEV MODE] Returning simulated pending STK response`);
      return {
        success: true,
        transactionId,
        checkoutRequestId,
        merchantRequestId,
        responseCode: '0',
        responseDescription: 'Success. Request accepted for processing (Dev Mode)',
        customerMessage: `Success. Request accepted for processing. Check your phone (${formattedPhone}) for M-Pesa PIN prompt.`,
        isDevMode: true,
        correlationId: cid,
      };
    }

    // PRODUCTION MODE: Call real Safaricom Daraja API
    try {
      const darajaRes = await darajaClient.initiateStkPush({
        phoneNumber: formattedPhone,
        amount: numericAmount,
        accountReference: accountReference || 'SinzoreFund',
        transactionDesc: transactionDesc || `${type} Payment`,
        correlationId: cid,
      });

      merchantRequestId = darajaRes.MerchantRequestID || merchantRequestId;
      checkoutRequestId = darajaRes.CheckoutRequestID || checkoutRequestId;

      // Update payment_transactions record with real Daraja IDs
      const updateData = {
        MerchantRequestID: merchantRequestId,
        CheckoutRequestID: checkoutRequestId,
        updatedAt: Timestamp ? Timestamp.now() : new Date().toISOString(),
      };

      if (initialized && db) {
        await db.collection('payment_transactions').doc(transactionId).update(updateData);
      } else {
        const existing = inMemoryTransactions.get(transactionId);
        inMemoryTransactions.set(transactionId, { ...existing, ...updateData });
      }

      logger.info(`${logPrefix} [STK PRODUCTION] Dispatch successful. CheckoutRequestID=${checkoutRequestId}`);

      return {
        success: true,
        transactionId,
        checkoutRequestId,
        merchantRequestId,
        responseCode: darajaRes.ResponseCode,
        responseDescription: darajaRes.ResponseDescription,
        customerMessage: darajaRes.CustomerMessage,
        isDevMode: false,
        correlationId: cid,
      };
    } catch (error) {
      logger.error(`${logPrefix} [STK PRODUCTION ERROR] STK Push failed: ${error.message}`);

      const failData = {
        Status: 'FAILED',
        ResultDescription: error.message,
        updatedAt: Timestamp ? Timestamp.now() : new Date().toISOString(),
      };

      if (initialized && db) {
        await db.collection('payment_transactions').doc(transactionId).update(failData);
      } else {
        const existing = inMemoryTransactions.get(transactionId);
        inMemoryTransactions.set(transactionId, { ...existing, ...failData });
      }

      throw error;
    }
  }

  /**
   * 2. PROCESS STK CALLBACK (Webhook handler)
   */
  async processStkCallback(callbackPayload, correlationId = null) {
    const cid = correlationId || this.generateCorrelationId('CB');
    const logPrefix = `[${cid}]`;

    logger.info(`${logPrefix} [MPESA STK CALLBACK] Processing incoming callback payload`);

    const parsed = darajaClient.parseStkCallback(callbackPayload);
    const { checkoutRequestId, merchantRequestId, resultCode, resultDesc, mpesaReceiptNumber, amount } = parsed;

    logger.info(`${logPrefix} [CALLBACK PARSED] CheckoutID: ${checkoutRequestId}, ResultCode: ${resultCode}, Desc: ${resultDesc}`);

    // Find corresponding transaction doc in Firestore or in-memory
    let txDocRef = null;
    let txData = null;

    if (initialized && db) {
      // Query by CheckoutRequestID or MerchantRequestID
      const snap = await db.collection('payment_transactions')
        .where('CheckoutRequestID', '==', checkoutRequestId)
        .limit(1)
        .get();

      if (!snap.empty) {
        txDocRef = snap.docs[0].ref;
        txData = snap.docs[0].data();
      } else {
        // Fallback query by MerchantRequestID
        const merchantSnap = await db.collection('payment_transactions')
          .where('MerchantRequestID', '==', merchantRequestId)
          .limit(1)
          .get();
        if (!merchantSnap.empty) {
          txDocRef = merchantSnap.docs[0].ref;
          txData = merchantSnap.docs[0].data();
        }
      }
    } else {
      // In-memory fallback lookup
      for (const [id, record] of inMemoryTransactions.entries()) {
        if (record.CheckoutRequestID === checkoutRequestId || record.MerchantRequestID === merchantRequestId) {
          txData = record;
          break;
        }
      }
    }

    if (!txData) {
      logger.warn(`${logPrefix} [CALLBACK WARNING] No matching payment_transactions document found for CheckoutRequestID: ${checkoutRequestId}`);
    }

    // IDEMPOTENCY / REPLAY PROTECTION CHECK
    if (txData && txData.isCallbackReceived) {
      logger.warn(`${logPrefix} [IDEMPOTENCY] Callback already processed for transaction #${txData.transactionId}. Skipping double-processing.`);
      return {
        alreadyProcessed: true,
        transactionId: txData.transactionId,
        status: txData.Status,
      };
    }

    // Determine Final Transaction Status
    let finalStatus = 'FAILED';
    if (resultCode === 0) {
      finalStatus = 'SUCCESS';
    } else if (resultCode === 1032) {
      finalStatus = 'CANCELLED';
    } else if (resultCode === 2001) {
      finalStatus = 'FAILED'; // Wrong PIN
    } else if (resultCode === 1037) {
      finalStatus = 'TIMEOUT';
    }

    const updatePayload = {
      Status: finalStatus,
      ResultCode: resultCode,
      ResultDescription: resultDesc,
      MpesaReceiptNumber: mpesaReceiptNumber || txData?.MpesaReceiptNumber || null,
      isCallbackReceived: true,
      callbackPayload,
      updatedAt: Timestamp ? Timestamp.now() : new Date().toISOString(),
    };

    if (txDocRef) {
      await txDocRef.update(updatePayload);
    } else if (txData) {
      inMemoryTransactions.set(txData.transactionId, { ...txData, ...updatePayload });
    }

    logger.info(`${logPrefix} [PAYMENT TRANSACTIONS] Updated transaction status to '${finalStatus}'`);

    // IF PAYMENT WAS SUCCESSFUL: Invoke FinancialService atomically
    if (finalStatus === 'SUCCESS') {
      const memberId = txData?.memberId || parsed.phoneNumber || 'UNKNOWN_MEMBER';
      const memberName = txData?.memberName || `M-Pesa User (${parsed.phoneNumber})`;
      const txAmount = amount || txData?.amount || 0;
      const receipt = mpesaReceiptNumber || `MP_${Date.now()}`;
      const txType = txData?.transactionType || 'CONTRIBUTION';

      if (txType === 'REPAYMENT' && txData?.loanId) {
        logger.info(`${logPrefix} [FINANCIAL CORE] Recording Loan Repayment for Loan #${txData.loanId}, Amount: Ksh ${txAmount}`);
        await financialService.recordLoanRepayment({
          loanId: txData.loanId,
          memberId,
          memberName,
          amount: txAmount,
          paymentMethod: 'MPESA_STK',
          receiptNumber: receipt,
          referenceId: txData?.transactionId || receipt,
          notes: `M-Pesa STK Repayment #${receipt}`,
          performedBy: 'MPESA_STK_CALLBACK',
        });
      } else {
        logger.info(`${logPrefix} [FINANCIAL CORE] Recording Investment Contribution for ${memberName}, Amount: Ksh ${txAmount}`);
        await financialService.recordContribution({
          memberId,
          memberName,
          amount: txAmount,
          paymentMethod: 'MPESA_STK',
          receiptNumber: receipt,
          referenceId: txData?.transactionId || receipt,
          notes: `M-Pesa STK Contribution #${receipt}`,
          performedBy: 'MPESA_STK_CALLBACK',
        });
      }
    } else {
      logger.warn(`${logPrefix} [STK PAYMENT FAILED/CANCELLED] Status=${finalStatus}, Reason=${resultDesc}`);
      await firebaseService.logAuditEvent({
        action: 'MPESA_PAYMENT_FAILED',
        userId: txData?.memberId || parsed.phoneNumber || 'SYSTEM',
        details: { finalStatus, resultCode, resultDesc, checkoutRequestId },
        status: finalStatus,
      });
    }

    return {
      success: finalStatus === 'SUCCESS',
      status: finalStatus,
      transactionId: txData?.transactionId || null,
      mpesaReceiptNumber,
    };
  }

  /**
   * 3. DISBURSE B2C LOAN (Production & Dev Mode)
   */
  async processB2cLoanDisbursal({
    phoneNumber,
    amount,
    loanId,
    memberId,
    memberName,
    performedBy = 'ADMIN',
    correlationId = null,
  }) {
    const cid = correlationId || this.generateCorrelationId('B2C');
    const logPrefix = `[${cid}]`;

    const numericAmount = Number(amount);
    if (!phoneNumber || !loanId || isNaN(numericAmount) || numericAmount <= 0) {
      throw new Error('Valid phone number, loan ID, and positive amount are required for B2C disbursal');
    }

    const isDevMode = env.mpesa.developmentMode;
    const formattedPhone = darajaClient.formatPhoneNumber(phoneNumber);
    const { exactDate, exactTime } = this.getFormattedDateTime();

    logger.info(`${logPrefix} [MPESA SERVICE] Triggering B2C Loan Disbursal (${isDevMode ? 'DEV MODE' : 'PRODUCTION MODE'}). Loan #${loanId}, Member: ${memberId}, Amount: Ksh ${numericAmount}`);

    const transactionId = `TX_B2C_${Date.now()}_${Math.random().toString(36).substring(2, 6).toUpperCase()}`;

    let conversationId = `AG_${Date.now()}`;
    let originatorConversationId = `OG_${Date.now()}`;

    const transactionData = {
      transactionId,
      memberId,
      memberName: memberName || 'Member',
      phoneNumber: formattedPhone,
      transactionType: 'LOAN_DISBURSEMENT',
      loanId,
      amount: numericAmount,
      MerchantRequestID: null,
      CheckoutRequestID: null,
      ConversationID: conversationId,
      OriginatorConversationID: originatorConversationId,
      MpesaReceiptNumber: null,
      ResultCode: null,
      ResultDescription: null,
      Status: 'PENDING',
      retryCount: 0,
      isCallbackReceived: false,
      callbackPayload: null,
      isDevMode,
      correlationId: cid,
      createdAt: Timestamp ? Timestamp.now() : new Date().toISOString(),
      updatedAt: Timestamp ? Timestamp.now() : new Date().toISOString(),
      exactDate,
      exactTime,
      serverTimestamp: Timestamp ? Timestamp.now() : new Date().toISOString(),
    };

    if (initialized && db) {
      await db.collection('payment_transactions').doc(transactionId).set(transactionData);
    } else {
      inMemoryTransactions.set(transactionId, transactionData);
    }

    logger.info(`${logPrefix} [PAYMENT TRANSACTIONS] Recorded PENDING B2C transaction #${transactionId}`);

    // If DEV MODE: Simulate or return pending
    if (isDevMode) {
      logger.info(`${logPrefix} [B2C DEV MODE] Returning simulated B2C dispatch response`);
      return {
        success: true,
        transactionId,
        conversationId,
        originatorConversationId,
        responseCode: '0',
        responseDescription: 'Accept the service request successfully (Dev Mode)',
        isDevMode: true,
        correlationId: cid,
      };
    }

    // PRODUCTION MODE: Call Daraja B2C API
    try {
      const darajaRes = await darajaClient.disburseB2cLoan({
        phoneNumber: formattedPhone,
        amount: numericAmount,
        remarks: `Loan Disbursal #${loanId}`,
        correlationId: cid,
      });

      conversationId = darajaRes.ConversationID || conversationId;
      originatorConversationId = darajaRes.OriginatorConversationID || originatorConversationId;

      const updateData = {
        ConversationID: conversationId,
        OriginatorConversationID: originatorConversationId,
        updatedAt: Timestamp ? Timestamp.now() : new Date().toISOString(),
      };

      if (initialized && db) {
        await db.collection('payment_transactions').doc(transactionId).update(updateData);
      } else {
        const existing = inMemoryTransactions.get(transactionId);
        inMemoryTransactions.set(transactionId, { ...existing, ...updateData });
      }

      logger.info(`${logPrefix} [B2C PRODUCTION] Disbursal request sent. ConversationID=${conversationId}`);

      return {
        success: true,
        transactionId,
        conversationId,
        originatorConversationId,
        responseCode: darajaRes.ResponseCode,
        responseDescription: darajaRes.ResponseDescription,
        isDevMode: false,
        correlationId: cid,
      };
    } catch (error) {
      logger.error(`${logPrefix} [B2C PRODUCTION ERROR] Disbursal failed: ${error.message}`);

      const failData = {
        Status: 'FAILED',
        ResultDescription: error.message,
        updatedAt: Timestamp ? Timestamp.now() : new Date().toISOString(),
      };

      if (initialized && db) {
        await db.collection('payment_transactions').doc(transactionId).update(failData);
      } else {
        const existing = inMemoryTransactions.get(transactionId);
        inMemoryTransactions.set(transactionId, { ...existing, ...failData });
      }

      throw error;
    }
  }

  /**
   * 4. PROCESS B2C CALLBACK (Webhook handler)
   */
  async processB2cCallback(callbackPayload, correlationId = null) {
    const cid = correlationId || this.generateCorrelationId('B2C_CB');
    const logPrefix = `[${cid}]`;

    logger.info(`${logPrefix} [MPESA B2C CALLBACK] Processing incoming B2C callback payload`);

    const parsed = darajaClient.parseB2cCallback(callbackPayload);
    const { conversationId, originatorConversationId, resultCode, resultDesc, mpesaReceiptNumber } = parsed;

    logger.info(`${logPrefix} [B2C CALLBACK PARSED] ConversationID: ${conversationId}, ResultCode: ${resultCode}, Desc: ${resultDesc}`);

    let txDocRef = null;
    let txData = null;

    if (initialized && db) {
      const snap = await db.collection('payment_transactions')
        .where('ConversationID', '==', conversationId)
        .limit(1)
        .get();

      if (!snap.empty) {
        txDocRef = snap.docs[0].ref;
        txData = snap.docs[0].data();
      } else {
        const origSnap = await db.collection('payment_transactions')
          .where('OriginatorConversationID', '==', originatorConversationId)
          .limit(1)
          .get();
        if (!origSnap.empty) {
          txDocRef = origSnap.docs[0].ref;
          txData = origSnap.docs[0].data();
        }
      }
    } else {
      for (const [id, record] of inMemoryTransactions.entries()) {
        if (record.ConversationID === conversationId || record.OriginatorConversationID === originatorConversationId) {
          txData = record;
          break;
        }
      }
    }

    if (!txData) {
      logger.warn(`${logPrefix} [B2C CALLBACK WARNING] No matching payment_transactions document for ConversationID: ${conversationId}`);
    }

    // Idempotency check
    if (txData && txData.isCallbackReceived) {
      logger.warn(`${logPrefix} [IDEMPOTENCY] B2C Callback already processed for transaction #${txData.transactionId}. Skipping.`);
      return {
        alreadyProcessed: true,
        transactionId: txData.transactionId,
        status: txData.Status,
      };
    }

    const finalStatus = resultCode === 0 ? 'SUCCESS' : 'FAILED';

    const updatePayload = {
      Status: finalStatus,
      ResultCode: resultCode,
      ResultDescription: resultDesc,
      MpesaReceiptNumber: mpesaReceiptNumber || txData?.MpesaReceiptNumber || null,
      isCallbackReceived: true,
      callbackPayload,
      updatedAt: Timestamp ? Timestamp.now() : new Date().toISOString(),
    };

    if (txDocRef) {
      await txDocRef.update(updatePayload);
    } else if (txData) {
      inMemoryTransactions.set(txData.transactionId, { ...txData, ...updatePayload });
    }

    // ON SUCCESSFUL PAYOUT: Invoke FinancialService to approve loan and adjust financial core records
    if (finalStatus === 'SUCCESS' && txData?.loanId) {
      logger.info(`${logPrefix} [FINANCIAL CORE] B2C Disbursal Succeeded. Approving Loan #${txData.loanId}`);
      try {
        await financialService.approveLoan({
          loanId: txData.loanId,
          performedBy: 'MPESA_B2C_CALLBACK',
        });
        logger.info(`${logPrefix} [FINANCIAL CORE] Loan #${txData.loanId} successfully approved and treasury updated.`);
      } catch (err) {
        logger.error(`${logPrefix} [FINANCIAL CORE ERROR] Failed to approve loan after B2C payout: ${err.message}`);
      }
    } else {
      logger.warn(`${logPrefix} [B2C PAYOUT FAILED] Loan #${txData?.loanId} remains PENDING. Reason=${resultDesc}`);
    }

    return {
      success: finalStatus === 'SUCCESS',
      status: finalStatus,
      transactionId: txData?.transactionId || null,
      mpesaReceiptNumber,
    };
  }

  /**
   * 5. SIMULATED STK CALLBACK (Dev Mode Helper)
   */
  async simulateStkCallback({ checkoutRequestId, resultCode = 0, mpesaReceiptNumber = null, correlationId = null }) {
    const cid = correlationId || this.generateCorrelationId('SIM_STK');
    const receipt = mpesaReceiptNumber || `QWE${Date.now()}`;

    const simulatedPayload = {
      Body: {
        stkCallback: {
          MerchantRequestID: `MR_SIM_${Date.now()}`,
          CheckoutRequestID: checkoutRequestId,
          ResultCode: Number(resultCode),
          ResultDesc: resultCode === 0 ? 'The service request is processed successfully.' : 'Request cancelled by user',
          CallbackMetadata: resultCode === 0 ? {
            Item: [
              { Name: 'Amount', Value: 5000 },
              { Name: 'MpesaReceiptNumber', Value: receipt },
              { Name: 'TransactionDate', Value: 20260722103000 },
              { Name: 'PhoneNumber', Value: 254712345678 },
            ],
          } : null,
        },
      },
    };

    return await this.processStkCallback(simulatedPayload, cid);
  }

  /**
   * 6. SIMULATED B2C CALLBACK (Dev Mode Helper)
   */
  async simulateB2cCallback({ conversationId, resultCode = 0, mpesaReceiptNumber = null, correlationId = null }) {
    const cid = correlationId || this.generateCorrelationId('SIM_B2C');
    const receipt = mpesaReceiptNumber || `B2C_${Date.now()}`;

    const simulatedPayload = {
      Result: {
        ResultType: 0,
        ResultCode: Number(resultCode),
        ResultDesc: resultCode === 0 ? 'The service request has been processed successfully.' : 'Failed',
        OriginatorConversationID: `OG_SIM_${Date.now()}`,
        ConversationID: conversationId,
        TransactionID: receipt,
        ResultParameters: resultCode === 0 ? {
          ResultParameter: [
            { Key: 'TransactionAmount', Value: 10000 },
            { Key: 'TransactionReceipt', Value: receipt },
            { Key: 'ReceiverPartyPublicName', Value: '254712345678 - Test Member' },
          ],
        } : null,
      },
    };

    return await this.processB2cCallback(simulatedPayload, cid);
  }

  /**
   * 7. SAFE RETRY LOGIC
   */
  async retryFailedTransaction(transactionId, correlationId = null) {
    const cid = correlationId || this.generateCorrelationId('RETRY');
    const logPrefix = `[${cid}]`;

    logger.info(`${logPrefix} [RETRY STRATEGY] Evaluating transaction #${transactionId} for retry`);

    let txDocRef = null;
    let txData = null;

    if (initialized && db) {
      const doc = await db.collection('payment_transactions').doc(transactionId).get();
      if (doc.exists) {
        txDocRef = doc.ref;
        txData = doc.data();
      }
    } else {
      txData = inMemoryTransactions.get(transactionId);
    }

    if (!txData) {
      throw new Error(`Payment transaction #${transactionId} not found`);
    }

    // NEVER retry: SUCCESS, CANCELLED (1032), WRONG_PIN (2001), or already completed callbacks
    if (txData.Status === 'SUCCESS') {
      throw new Error('Cannot retry a successful transaction.');
    }
    if (txData.Status === 'CANCELLED' || txData.ResultCode === 1032) {
      throw new Error('Cannot retry a user-cancelled transaction.');
    }
    if (txData.ResultCode === 2001) {
      throw new Error('Cannot retry a transaction failed due to wrong PIN.');
    }

    const currentRetries = txData.retryCount || 0;
    if (currentRetries >= 3) {
      throw new Error('Maximum retry limit (3) exceeded for this transaction.');
    }

    logger.info(`${logPrefix} [RETRY EXECUTING] Attempt ${currentRetries + 1} for transaction #${transactionId}`);

    const updateRetry = {
      retryCount: currentRetries + 1,
      updatedAt: Timestamp ? Timestamp.now() : new Date().toISOString(),
    };

    if (txDocRef) {
      await txDocRef.update(updateRetry);
    } else {
      inMemoryTransactions.set(transactionId, { ...txData, ...updateRetry });
    }

    // Re-initiate STK push or status query
    if (txData.transactionType === 'LOAN_DISBURSEMENT') {
      return await this.processB2cLoanDisbursal({
        phoneNumber: txData.phoneNumber,
        amount: txData.amount,
        loanId: txData.loanId,
        memberId: txData.memberId,
        memberName: txData.memberName,
        correlationId: cid,
      });
    } else {
      return await this.processStkPush({
        phoneNumber: txData.phoneNumber,
        amount: txData.amount,
        accountReference: 'SinzoreFund',
        transactionDesc: `${txData.transactionType} Retry`,
        userId: txData.memberId,
        memberName: txData.memberName,
        type: txData.transactionType,
        loanId: txData.loanId,
        correlationId: cid,
      });
    }
  }

  /**
   * 8. RECONCILIATION & TRANSACTION FETCHERS
   */
  async getPaymentTransactions({ memberId = null, status = null, type = null, limit = 50 } = {}) {
    if (initialized && db) {
      let query = db.collection('payment_transactions');
      if (memberId) query = query.where('memberId', '==', memberId);
      if (status) query = query.where('Status', '==', status);
      if (type) query = query.where('transactionType', '==', type);

      const snap = await query.limit(Number(limit)).get();
      return snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
    }

    // In-memory fallback
    let results = Array.from(inMemoryTransactions.values());
    if (memberId) results = results.filter(r => r.memberId === memberId);
    if (status) results = results.filter(r => r.Status === status);
    if (type) results = results.filter(r => r.transactionType === type);
    return results.slice(0, Number(limit));
  }

  async getPaymentTransactionById(transactionId) {
    if (initialized && db) {
      const doc = await db.collection('payment_transactions').doc(transactionId).get();
      if (!doc.exists) return null;
      return { id: doc.id, ...doc.data() };
    }
    return inMemoryTransactions.get(transactionId) || null;
  }

  /**
   * Reconciles payment transactions against ledger entries
   */
  async reconcileLedgerAndPayments() {
    logger.info('[RECONCILIATION ENGINE] Running automated payment transaction <-> ledger reconciliation check');

    const paymentTxs = await this.getPaymentTransactions({ status: 'SUCCESS', limit: 100 });
    const matchResults = {
      totalSuccessfulPayments: paymentTxs.length,
      reconciled: 0,
      unreconciled: [],
    };

    if (!initialized || !db) {
      return {
        ...matchResults,
        reconciled: paymentTxs.length,
        status: 'COMPLETE (DEV MODE)',
      };
    }

    for (const pTx of paymentTxs) {
      const receipt = pTx.MpesaReceiptNumber;
      if (!receipt) {
        matchResults.unreconciled.push({ transactionId: pTx.transactionId, reason: 'Missing MpesaReceiptNumber' });
        continue;
      }

      // Query ledger doc by receipt number
      const ledgerSnap = await db.collection('ledger').where('receiptNumber', '==', receipt).get();
      if (!ledgerSnap.empty) {
        matchResults.reconciled += 1;
      } else {
        matchResults.unreconciled.push({ transactionId: pTx.transactionId, receiptNumber: receipt, reason: 'Ledger entry missing' });
      }
    }

    return matchResults;
  }
}

module.exports = new MpesaService();
