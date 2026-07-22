const { successResponse, errorResponse } = require('../utils/response');
const firebaseService = require('../services/firebase.service');
const financialService = require('../services/financial.service');
const { db, initialized } = require('../firebase/admin');

class ReportsController {
  /**
   * Financial Summary Report
   */
  async generateFinancialReport(req, res, next) {
    try {
      const summary = await firebaseService.getSummaryStatistics();
      return successResponse(res, {
        title: 'Sinzore Investment Financial Summary Report',
        generatedAt: new Date().toISOString(),
        summary,
        reportPeriod: 'Current Financial Year',
      }, 'Financial report generated');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Member Statement Export (PDF Structured Data)
   */
  async getMemberStatementReport(req, res, next) {
    try {
      const { memberId } = req.params;
      const targetMember = memberId || req.user.uid;

      const statement = await financialService.getMemberFinancialStatement(targetMember);
      return successResponse(res, {
        reportType: 'MEMBER_STATEMENT',
        title: `Member Financial Statement - ${statement.memberName || targetMember}`,
        generatedAt: new Date().toISOString(),
        memberId: targetMember,
        statement,
        exportFormatSupported: ['JSON', 'PDF_STRUCTURED'],
      }, 'Member statement report generated');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Treasury Report
   */
  async getTreasuryReport(req, res, next) {
    try {
      const summary = await firebaseService.getSummaryStatistics();
      let recentLedger = [];

      if (initialized && db) {
        const snap = await db.collection('ledger').limit(50).get();
        recentLedger = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      }

      return successResponse(res, {
        reportType: 'TREASURY_REPORT',
        title: 'Sinzore Treasury & Liquidity Report',
        generatedAt: new Date().toISOString(),
        summary,
        recentLedger,
      }, 'Treasury report generated');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Contribution Report
   */
  async getContributionReport(req, res, next) {
    try {
      let contributions = [];
      if (initialized && db) {
        const snap = await db.collection('contributions').limit(100).get();
        contributions = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      }

      const totalAmount = contributions.reduce((sum, c) => sum + Number(c.amount || 0), 0);

      return successResponse(res, {
        reportType: 'CONTRIBUTION_REPORT',
        title: 'Member Contributions Report',
        generatedAt: new Date().toISOString(),
        totalContributions: contributions.length,
        totalAmount,
        contributions,
      }, 'Contribution report generated');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Loan Report
   */
  async getLoanReport(req, res, next) {
    try {
      let loans = [];
      if (initialized && db) {
        const snap = await db.collection('loans').limit(100).get();
        loans = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      }

      return successResponse(res, {
        reportType: 'LOAN_REPORT',
        title: 'Loans & Disbursals Report',
        generatedAt: new Date().toISOString(),
        totalLoansCount: loans.length,
        loans,
      }, 'Loan report generated');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Repayment Report
   */
  async getRepaymentReport(req, res, next) {
    try {
      let repayments = [];
      if (initialized && db) {
        const snap = await db.collection('repayments').limit(100).get();
        repayments = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      }

      const totalAmount = repayments.reduce((sum, r) => sum + Number(r.amount || 0), 0);

      return successResponse(res, {
        reportType: 'REPAYMENT_REPORT',
        title: 'Loan Repayments Report',
        generatedAt: new Date().toISOString(),
        totalRepaymentsCount: repayments.length,
        totalAmount,
        repayments,
      }, 'Repayment report generated');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Ledger Report
   */
  async getLedgerReport(req, res, next) {
    try {
      let ledger = [];
      if (initialized && db) {
        const snap = await db.collection('ledger').limit(200).get();
        ledger = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      }

      return successResponse(res, {
        reportType: 'LEDGER_REPORT',
        title: 'General Ledger Audit Trail Report',
        generatedAt: new Date().toISOString(),
        totalEntries: ledger.length,
        ledger,
      }, 'Ledger report generated');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Audit Logs Report (Admin only)
   */
  async getAuditLogs(req, res, next) {
    try {
      let logs = [];
      if (initialized && db) {
        const snapshot = await db.collection('audit_logs')
          .limit(100)
          .get();

        logs = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      }

      return successResponse(res, {
        reportType: 'AUDIT_REPORT',
        title: 'System Audit Trail Report',
        generatedAt: new Date().toISOString(),
        logs,
      }, 'Audit logs retrieved');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Monthly Summary Report
   */
  async getMonthlySummaryReport(req, res, next) {
    try {
      const summary = await firebaseService.getSummaryStatistics();
      return successResponse(res, {
        reportType: 'MONTHLY_SUMMARY',
        title: `Monthly Financial Summary (${new Date().toLocaleString('en-US', { month: 'long', year: 'numeric' })})`,
        generatedAt: new Date().toISOString(),
        summary,
      }, 'Monthly summary report generated');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Annual Summary Report
   */
  async getAnnualSummaryReport(req, res, next) {
    try {
      const summary = await firebaseService.getSummaryStatistics();
      return successResponse(res, {
        reportType: 'ANNUAL_SUMMARY',
        title: `Annual Financial Performance Report (${new Date().getFullYear()})`,
        generatedAt: new Date().toISOString(),
        summary,
      }, 'Annual summary report generated');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new ReportsController();
