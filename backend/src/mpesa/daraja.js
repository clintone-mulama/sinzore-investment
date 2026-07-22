const axios = require('axios');
const env = require('../config/env');
const logger = require('../utils/logger');

class DarajaClient {
  constructor() {
    this.environment = env.mpesa.environment;
    this.consumerKey = env.mpesa.consumerKey;
    this.consumerSecret = env.mpesa.consumerSecret;
    this.shortcode = env.mpesa.shortcode;
    this.passkey = env.mpesa.passkey;
    this.callbackUrl = env.mpesa.callbackUrl;

    this.baseUrl = this.environment === 'production'
      ? 'https://api.safaricom.co.ke'
      : 'https://sandbox.safaricom.co.ke';

    // OAuth token caching
    this.accessToken = null;
    this.tokenExpiresAt = 0;
  }

  /**
   * Generates or fetches cached OAuth token from Safaricom Daraja API
   */
  async getAccessToken(correlationId = '') {
    const logPrefix = correlationId ? `[${correlationId}] ` : '';

    // Reuse valid token if available (with 60-second safety margin)
    if (this.accessToken && Date.now() < this.tokenExpiresAt - 60000) {
      logger.info(`${logPrefix}[DARAJA OAUTH] Using cached access token`);
      return this.accessToken;
    }

    try {
      logger.info(`${logPrefix}[DARAJA OAUTH] Fetching new access token from ${this.baseUrl}`);
      const authHeader = Buffer.from(`${this.consumerKey}:${this.consumerSecret}`).toString('base64');
      const response = await axios.get(`${this.baseUrl}/oauth/v1/generate?grant_type=client_credentials`, {
        headers: {
          Authorization: `Basic ${authHeader}`,
        },
      });

      this.accessToken = response.data.access_token;
      const expiresInSeconds = parseInt(response.data.expires_in, 10) || 3599;
      this.tokenExpiresAt = Date.now() + (expiresInSeconds * 1000);

      logger.info(`${logPrefix}[DARAJA OAUTH] New access token cached. Valid for ${expiresInSeconds}s`);
      return this.accessToken;
    } catch (error) {
      logger.error(`${logPrefix}[DARAJA OAUTH ERROR] Failed to fetch access token: ${error.response?.data?.errorMessage || error.message}`);
      throw new Error(`Daraja Authentication Failed: ${error.response?.data?.errorMessage || error.message}`);
    }
  }

  /**
   * Formats a phone number to standard 2547XXXXXXXX format
   */
  formatPhoneNumber(phone) {
    if (!phone) return '';
    let cleaned = phone.toString().replace(/[^0-9]/g, '');
    if (cleaned.startsWith('0')) {
      cleaned = '254' + cleaned.substring(1);
    } else if (cleaned.startsWith('+254')) {
      cleaned = cleaned.substring(1);
    } else if (!cleaned.startsWith('254') && cleaned.length === 9) {
      cleaned = '254' + cleaned;
    }
    return cleaned;
  }

  /**
   * Initiates STK Push request to customer phone
   */
  async initiateStkPush({ phoneNumber, amount, accountReference, transactionDesc, callbackUrl, correlationId = '' }) {
    const logPrefix = correlationId ? `[${correlationId}] ` : '';
    try {
      const accessToken = await this.getAccessToken(correlationId);
      const formattedPhone = this.formatPhoneNumber(phoneNumber);
      const timestamp = new Date().toISOString().replace(/[^0-9]/g, '').slice(0, 14);
      const password = Buffer.from(`${this.shortcode}${this.passkey}${timestamp}`).toString('base64');

      const payload = {
        BusinessShortCode: this.shortcode,
        Password: password,
        Timestamp: timestamp,
        TransactionType: 'CustomerPayBillOnline',
        Amount: Math.round(Number(amount)),
        PartyA: formattedPhone,
        PartyB: this.shortcode,
        PhoneNumber: formattedPhone,
        CallBackURL: callbackUrl || this.callbackUrl,
        AccountReference: accountReference || 'SinzoreFund',
        TransactionDesc: transactionDesc || 'Investment Contribution',
      };

      logger.info(`${logPrefix}[DARAJA STK PUSH] Request payload sent for ${formattedPhone}, Amount: Ksh ${amount}`);

      const response = await axios.post(
        `${this.baseUrl}/mpesa/stkpush/v1/processrequest`,
        payload,
        {
          headers: {
            Authorization: `Bearer ${accessToken}`,
            'Content-Type': 'application/json',
          },
        }
      );

      logger.info(`${logPrefix}[DARAJA STK SUCCESS] Response received: CheckoutRequestID=${response.data.CheckoutRequestID}`);
      return response.data;
    } catch (error) {
      logger.error(`${logPrefix}[DARAJA STK ERROR] STK Push failed: ${error.response?.data?.errorMessage || error.message}`);
      throw new Error(error.response?.data?.errorMessage || error.message || 'M-Pesa STK Push request failed');
    }
  }

  /**
   * Queries status of STK Push request
   */
  async queryStkPushStatus({ checkoutRequestId, correlationId = '' }) {
    const logPrefix = correlationId ? `[${correlationId}] ` : '';
    try {
      const accessToken = await this.getAccessToken(correlationId);
      const timestamp = new Date().toISOString().replace(/[^0-9]/g, '').slice(0, 14);
      const password = Buffer.from(`${this.shortcode}${this.passkey}${timestamp}`).toString('base64');

      const payload = {
        BusinessShortCode: this.shortcode,
        Password: password,
        Timestamp: timestamp,
        CheckoutRequestID: checkoutRequestId,
      };

      logger.info(`${logPrefix}[DARAJA STK QUERY] Querying status for CheckoutRequestID: ${checkoutRequestId}`);

      const response = await axios.post(
        `${this.baseUrl}/mpesa/stkpushquery/v1/query`,
        payload,
        {
          headers: {
            Authorization: `Bearer ${accessToken}`,
            'Content-Type': 'application/json',
          },
        }
      );

      return response.data;
    } catch (error) {
      logger.error(`${logPrefix}[DARAJA STK QUERY ERROR] Query failed: ${error.response?.data?.errorMessage || error.message}`);
      throw new Error(error.response?.data?.errorMessage || error.message || 'M-Pesa STK status query failed');
    }
  }

  /**
   * Initiates B2C Loan Disbursement
   */
  async disburseB2cLoan({ phoneNumber, amount, remarks, callbackUrl, correlationId = '' }) {
    const logPrefix = correlationId ? `[${correlationId}] ` : '';
    try {
      const accessToken = await this.getAccessToken(correlationId);
      const formattedPhone = this.formatPhoneNumber(phoneNumber);

      const payload = {
        InitiatorName: env.mpesa.initiatorName,
        SecurityCredential: env.mpesa.securityCredential,
        CommandID: 'BusinessPayment',
        Amount: Math.round(Number(amount)),
        PartyA: this.shortcode,
        PartyB: formattedPhone,
        Remarks: remarks || 'Loan Disbursement',
        QueueTimeOutURL: callbackUrl || this.callbackUrl,
        ResultURL: callbackUrl || this.callbackUrl,
        Occasion: 'LoanDisbursal',
      };

      logger.info(`${logPrefix}[DARAJA B2C] Initiating B2C disbursal for ${formattedPhone}, Amount: Ksh ${amount}`);

      const response = await axios.post(
        `${this.baseUrl}/mpesa/b2c/v1/paymentrequest`,
        payload,
        {
          headers: {
            Authorization: `Bearer ${accessToken}`,
            'Content-Type': 'application/json',
          },
        }
      );

      logger.info(`${logPrefix}[DARAJA B2C SUCCESS] ConversationID=${response.data.ConversationID}`);
      return response.data;
    } catch (error) {
      logger.error(`${logPrefix}[DARAJA B2C ERROR] B2C Disbursal failed: ${error.response?.data?.errorMessage || error.message}`);
      throw new Error(error.response?.data?.errorMessage || error.message || 'M-Pesa B2C payment failed');
    }
  }

  /**
   * Parses and validates STK Callback response
   */
  parseStkCallback(callbackData) {
    if (!callbackData || !callbackData.Body || !callbackData.Body.stkCallback) {
      throw new Error('Invalid STK Callback payload format');
    }

    const stk = callbackData.Body.stkCallback;
    const resultCode = stk.ResultCode;
    const resultDesc = stk.ResultDesc;
    const merchantRequestId = stk.MerchantRequestID;
    const checkoutRequestId = stk.CheckoutRequestID;

    let mpesaReceiptNumber = null;
    let amount = 0;
    let phoneNumber = null;
    let transactionDate = null;

    if (resultCode === 0 && stk.CallbackMetadata && stk.CallbackMetadata.Item) {
      stk.CallbackMetadata.Item.forEach((item) => {
        if (item.Name === 'MpesaReceiptNumber') mpesaReceiptNumber = item.Value;
        if (item.Name === 'Amount') amount = Number(item.Value);
        if (item.Name === 'PhoneNumber') phoneNumber = item.Value;
        if (item.Name === 'TransactionDate') transactionDate = item.Value;
      });
    }

    return {
      success: resultCode === 0,
      resultCode,
      resultDesc,
      merchantRequestId,
      checkoutRequestId,
      mpesaReceiptNumber,
      amount,
      phoneNumber,
      transactionDate,
    };
  }

  /**
   * Parses and validates B2C Callback response
   */
  parseB2cCallback(callbackData) {
    if (!callbackData || !callbackData.Result) {
      throw new Error('Invalid B2C Callback payload format');
    }

    const res = callbackData.Result;
    const resultCode = res.ResultCode;
    const resultDesc = res.ResultDesc;
    const conversationId = res.ConversationID;
    const originatorConversationId = res.OriginatorConversationID;
    const transactionId = res.TransactionID || null;

    let amount = 0;
    let mpesaReceiptNumber = transactionId;
    let recipientName = null;

    if (res.ResultParameters && res.ResultParameters.ResultParameter) {
      res.ResultParameters.ResultParameter.forEach((param) => {
        if (param.Key === 'TransactionAmount') amount = Number(param.Value) || 0;
        if (param.Key === 'TransactionReceipt') mpesaReceiptNumber = param.Value;
        if (param.Key === 'ReceiverPartyPublicName') recipientName = param.Value;
      });
    }

    return {
      success: resultCode === 0,
      resultCode,
      resultDesc,
      conversationId,
      originatorConversationId,
      mpesaReceiptNumber,
      amount,
      recipientName,
    };
  }
}

module.exports = new DarajaClient();
