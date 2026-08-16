//
// Plaid.kt
//
// Port of iOS Core/Plaid/{PlaidModels,PlaidService}.swift — Codable mirrors of
// the JSON returned by the /api/plaid/* routes and the calls that fetch them.
// (Plaid Link presentation is wired in the budgeting UI via the Plaid Android
// SDK; these are the data pieces.)
//

package com.finnacalc.android.core.plaid

import com.finnacalc.android.core.networking.ApiClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * /api/plaid/transactions → { transactions: BankTransaction[] }
 * Plaid convention: positive amount = money out (expense), negative = money in.
 */
@Serializable
data class BankTransaction(
    val date: String,
    val name: String,
    val amount: Double,
    val category: String,
    val currency: String,
    /**
     * Which account it landed in. Plaid puts `account_id` on every
     * transaction; the route does not forward it yet, so this is optional and
     * starts populating the account picker the moment it does.
     */
    val accountId: String? = null,
)

/** One account under a linked institution, as the route would describe it. */
@Serializable
data class PlaidAccount(
    @SerialName("account_id") val id: String,
    val name: String,
    val mask: String? = null,
    @SerialName("type") val kind: String? = null,
)

/**
 * Everything one finished Link flow produced. `accounts` and `institution`
 * are optional: today's route sends transactions only, and an import must
 * keep working until it sends more.
 */
data class PlaidImportResult(
    val transactions: List<BankTransaction>,
    val accounts: List<PlaidAccount> = emptyList(),
    val institution: String? = null,
)

// MARK: - Holdings (/api/plaid/holdings) — used by the portfolio card (Phase 5)

@Serializable
data class PortfolioHolding(
    val securityId: String,
    val name: String,
    val fullName: String,
    val type: String,
    val value: Double,
    val quantity: Double,
    val price: Double,
    val avgCost: Double,
    val costBasis: Double,
    val totalReturn: Double,
    val totalReturnPct: Double? = null,
    val weight: Double,
)

@Serializable
data class AllocationSlice(
    val type: String,
    val value: Double,
)

@Serializable
data class PortfolioResponse(
    val holdings: List<PortfolioHolding>,
    val allocation: List<AllocationSlice>,
    val totalValue: Double,
    val totalCostBasis: Double,
    val totalReturn: Double,
    val totalReturnPct: Double? = null,
    val accountCount: Int,
    val currency: String,
)

// MARK: - Service

enum class PlaidProduct(val raw: String) {
    Transactions("transactions"),
    Investments("investments"),
}

object PlaidService {
    /** POST /api/plaid/create-link-token { product } → link_token */
    suspend fun createLinkToken(product: PlaidProduct): String {
        @Serializable
        data class Response(@SerialName("link_token") val linkToken: String)

        val body = buildJsonObject { put("product", product.raw) }.toString()
        val response: Response = ApiClient.shared.postJson("/api/plaid/create-link-token", body)
        return response.linkToken
    }

    @Serializable
    private data class TransactionsResponse(
        val transactions: List<BankTransaction>,
        val accounts: List<PlaidAccount>? = null,
        val institution: String? = null,
    )

    /**
     * POST /api/plaid/transactions { public_token } → 90 days of
     * transactions. `accounts` and `institution` are decoded if present and
     * skipped if not; nothing here fails if they stay missing.
     */
    suspend fun importTransactions(publicToken: String): PlaidImportResult {
        val body = buildJsonObject { put("public_token", publicToken) }.toString()
        val response: TransactionsResponse = ApiClient.shared.postJson("/api/plaid/transactions", body)
        return PlaidImportResult(
            transactions = response.transactions,
            accounts = response.accounts ?: emptyList(),
            institution = response.institution,
        )
    }

    /** POST /api/plaid/holdings { public_token } → investment portfolio */
    suspend fun importHoldings(publicToken: String): PortfolioResponse {
        val body = buildJsonObject { put("public_token", publicToken) }.toString()
        return ApiClient.shared.postJson("/api/plaid/holdings", body)
    }
}
