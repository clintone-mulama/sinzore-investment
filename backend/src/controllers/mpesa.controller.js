const { successResponse, errorResponse } = require('../utils/response');
const mpesaService = require('../services/mpesa.service');
const logger = require('../utils/logger');

class MpesaController {
  /**
   * Initiate STK Push
   */
  async initiateStkPush(req, res, next) {
    try {
      const { phoneNumber, amount, accountReference, transactionDesc, type, loanId } = req.body;

      if (!phoneNumber || !amount || Number(amount) <= 0) {
        return errorResponse(res, 'Valid phone number and positive amount are required', 400);
      }

      const result = await mpesaService.processStkPush({
        phoneNumber,
        amount: Number(amount),
        accountReference,
        transactionDesc,
        userId: req.user?.uid || req.body.memberId || 'ANONYMOUS',
        memberName: req.user?.fullName || req.body.memberName || 'Member',
        type: type || 'CONTRIBUTION',
        loanId,
      });

      return successResponse(res, result, 'STK Push initiated successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * B2C Loan Disbursal (Admin / Treasurer only)
   */
  async disburseB2cLoan(req, res, next) {
    try {
      const { phoneNumber, amount, loanId, memberId, memberName } = req.body;

      if (!phoneNumber || !amount || !loanId) {
        return errorResponse(res, 'Phone number, amount, and loanId are required', 400);
      }

      const performedBy = req.user?.email || req.user?.uid || 'ADMIN';
      const result = await mpesaService.processB2cLoanDisbursal({
        phoneNumber,
        amount: Number(amount),
        loanId,
        memberId: memberId || req.user.uid,
        memberName,
        performedBy,
      });

      return successResponse(res, result, 'B2C Loan disbursal request dispatched');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Public Webhook Callback endpoint for STK Push from Safaricom Daraja
   */
  async handleStkCallback(req, res, next) {
    try {
      logger.info('[MPESA STK CALLBACK ENDPOINT HIT]');
      const callbackResult = await mpesaService.processStkCallback(req.body);

      return res.status(200).json({
        ResultCode: 0,
        ResultDesc: 'Callback processed successfully',
        data: callbackResult,
      });
    } catch (error) {
      logger.error(`[MPESA STK CALLBACK ERROR] ${error.message}`);
      // Safaricom expects a 200 OK HTTP response to acknowledge callback delivery
      return res.status(200).json({
        ResultCode: 1,
        ResultDesc: 'Callback received with internal warnings',
      });
    }
  }

  /**
   * Public Webhook Callback endpoint for B2C Loan Disbursal
   */
  async handleB2cCallback(req, res, next) {
    try {
      logger.info('[MPESA B2C CALLBACK ENDPOINT HIT]', req.body);
      const callbackResult = await mpesaService.processB2cCallback(req.body);

      return res.status(200).json({
        ResultCode: 0,
        ResultDesc: 'B2C Callback processed successfully',
        data: callbackResult,
      });
    } catch (error) {
      logger.error(`[MPESA B2C CALLBACK ERROR] ${error.message}`);
      return res.status(200).json({
        ResultCode: 1,
        ResultDesc: 'B2C Callback received with internal warnings',
      });
    }
  }

  /**
   * Safe Retry endpoint
   */
  async retryTransaction(req, res, next) {
    try {
      const { transactionId } = req.body;
      if (!transactionId) {
        return errorResponse(res, 'Target transaction ID is required for retry', 400);
      }

      const result = await mpesaService.retryFailedTransaction(transactionId);
      return successResponse(res, result, 'Transaction retry initiated successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Dev Mode Helper: Simulate STK Callback
   */
  async simulateStkCallback(req, res, next) {
    try {
      const { checkoutRequestId, resultCode, mpesaReceiptNumber } = req.body;
      if (!checkoutRequestId) {
        return errorResponse(res, 'CheckoutRequestID is required for simulation', 400);
      }

      const result = await mpesaService.simulateStkCallback({
        checkoutRequestId,
        resultCode: resultCode !== undefined ? resultCode : 0,
        mpesaReceiptNumber,
      });

      return successResponse(res, result, 'Simulated STK Callback executed successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Dev Mode Helper: Simulate B2C Callback
   */
  async simulateB2cCallback(req, res, next) {
    try {
      const { conversationId, resultCode, mpesaReceiptNumber } = req.body;
      if (!conversationId) {
        return errorResponse(res, 'ConversationID is required for simulation', 400);
      }

      const result = await mpesaService.simulateB2cCallback({
        conversationId,
        resultCode: resultCode !== undefined ? resultCode : 0,
        mpesaReceiptNumber,
      });

      return successResponse(res, result, 'Simulated B2C Callback executed successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Fetch payment transactions list
   */
  async getPaymentTransactions(req, res, next) {
    try {
      const { memberId, status, type, limit } = req.query;
      const transactions = await mpesaService.getPaymentTransactions({
        memberId,
        status,
        type,
        limit,
      });

      return successResponse(res, transactions, 'Payment transactions retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Fetch single payment transaction details
   */
  async getPaymentTransactionById(req, res, next) {
    try {
      const { id } = req.params;
      const transaction = await mpesaService.getPaymentTransactionById(id);
      if (!transaction) {
        return errorResponse(res, 'Payment transaction record not found', 404);
      }

      return successResponse(res, transaction, 'Payment transaction retrieved');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Run payment reconciliation
   */
  async reconcileTransactions(req, res, next) {
    try {
      const report = await mpesaService.reconcileLedgerAndPayments();
      return successResponse(res, report, 'Payment reconciliation completed');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new MpesaController();
