//
// TermsScreen.kt
//
// Port of iOS Features/Pages/TermsView.swift — the Terms of Service as a stack
// of titled cards. Copy ported verbatim; rendering is LegalPage.kt.
//

package com.finnacalc.android.features.pages

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val termsSections = listOf(
    LegalSection(
        title = "Terms of Service Agreement",
        icon = Icons.Filled.Description,
        tint = IconColor.blue,
        blocks = listOf(
            LegalBlock.Paragraph(
                "These Terms of Service govern your use of FinnaCalc: the website at " +
                    "finnacalc.com and the FinnaCalc apps for phones and tablets, wherever you " +
                    "installed them from. By accessing or using the services, you agree to be " +
                    "bound by these Terms. If you disagree with any part of these terms, then " +
                    "you may and should not access the services."
            ),
            LegalBlock.Paragraph(
                "The right to update these Terms at any time is reserved. Changes will be " +
                    "effective immediately upon posting. Your continued use of the services " +
                    "after changes are posted constitutes acceptance of the new Terms."
            ),
        ),
    ),
    LegalSection(
        title = "Service Description",
        icon = Icons.Filled.Functions,
        tint = IconColor.green,
        blocks = listOf(
            LegalBlock.Paragraph(
                "FinnaCalc provides free financial tools for personal and business use. The " +
                    "services include but are not limited to:"
            ),
            LegalBlock.Bullets(
                listOf(
                    "Financial calculators (startup costs, break-even, ROI, loans, pricing, " +
                        "margins, and more)",
                    "Budgeting: income/expense tracking, savings goals with alerts, history " +
                        "snapshots, bank statement import, and optional bank connections via Plaid",
                    "Investing: optional brokerage connections via SnapTrade for a live portfolio " +
                        "view, portfolio analysis, investing goals, and, where your brokerage " +
                        "allows it, placing and canceling orders that execute at your brokerage",
                    "Investing research: quotes, charts, key stats, screeners, ETFs, company " +
                        "financials, a trade tracker, and market news from third-party data " +
                        "providers",
                    "A guided federal tax estimator with a live refund estimate (educational; " +
                        "e-filing is not yet enabled and returns are not transmitted)",
                    "FinnaBot, Budget Analysis, and Portfolio Analysis, our AI and analytics " +
                        "tools for finance questions",
                    "Educational videos and articles",
                    "Optional paid subscriptions: Budgeting Plus, Investing Plus, and FinnaCalc Pro",
                )
            ),
            LegalBlock.Callout(
                lead = "Important:",
                body = " The calculators provide estimates for planning purposes only. Results " +
                    "should not be considered as professional financial, tax, or legal advice.",
                tone = CalloutTone.Info,
            ),
        ),
    ),
    LegalSection(
        title = "User Responsibilities",
        icon = Icons.Filled.Shield,
        tint = IconColor.purple,
        blocks = listOf(
            LegalBlock.Paragraph("By using the services, you agree to:"),
            LegalBlock.Bullets(
                listOf(
                    "Use the service only for lawful purposes and in accordance with these Terms",
                    "Provide accurate information when using the calculators",
                    "Not attempt to interfere with or disrupt the services",
                    "Not use automated systems to access the services without permission",
                    "Respect intellectual property rights",
                    "Not share or distribute malicious content",
                    "Comply with all applicable laws and regulations",
                )
            ),
        ),
    ),
    LegalSection(
        title = "Important Disclaimers",
        icon = Icons.Filled.Warning,
        tint = IconColor.orange,
        blocks = listOf(
            LegalBlock.Subheading("Financial Advice Disclaimer"),
            LegalBlock.Paragraph(
                "FinnaCalc does not provide financial, investment, tax, or legal advice. The " +
                    "calculators and tools are for informational and educational purposes only. " +
                    "Results are estimates based on the information you provide and should not be " +
                    "relied upon for making financial decisions without consulting qualified " +
                    "professionals."
            ),
            LegalBlock.Subheading("Accuracy Disclaimer"),
            LegalBlock.Paragraph(
                "While efforts are made for accuracy, no warranties are made about the " +
                    "completeness, reliability, or accuracy of the calculators or information. " +
                    "Financial regulations, tax laws, and market conditions change frequently, " +
                    "and the tools may not reflect the most current information."
            ),
            LegalBlock.Subheading("Market Data Disclaimer"),
            LegalBlock.Paragraph(
                "Quotes, charts, statistics, and news are supplied by third-party providers, may " +
                    "be delayed (typically 15 minutes or more), and may contain errors or gaps. " +
                    "Nothing in the app or on the website is a recommendation to buy or sell any " +
                    "security. FinnaCalc is not a broker-dealer and does not execute trades; any " +
                    "trading happens with your own brokerage under its terms."
            ),
            LegalBlock.Subheading("Investing and Trading Risk"),
            LegalBlock.Paragraph(
                "Investing involves risk, including the possible loss of the money you invest. " +
                    "Past performance, whether of a stock, a fund, or your own portfolio as shown " +
                    "in FinnaCalc, does not predict future results. FinnaCalc cannot and does not " +
                    "promise any return, and cannot refund investment losses. Every order you " +
                    "place is your decision: you review and confirm it, your brokerage executes " +
                    "it under its own terms, and FinnaCalc never holds your money or securities. " +
                    "Some brokerages connect view-only, and what a connection allows is decided " +
                    "by the brokerage and can change."
            ),
            LegalBlock.Subheading("AI-Generated Content"),
            LegalBlock.Paragraph(
                "FinnaBot and Budget Analysis responses are generated by an AI model. They can " +
                    "be incomplete or wrong, and are not financial, tax, or legal advice. Verify " +
                    "anything important with a qualified professional."
            ),
            LegalBlock.Subheading("Tax Estimator"),
            LegalBlock.Paragraph(
                "The tax experience gives you an estimate for educational purposes. It isn't a " +
                    "filed return, and e-filing isn't enabled yet, so your return data is never " +
                    "sent to the IRS or anyone else. FinnaCalc is not a tax preparer or tax " +
                    "professional, tax situations differ, and the estimate can differ from what " +
                    "you actually owe or are refunded. Check anything important with a qualified " +
                    "tax professional before acting on it."
            ),
            LegalBlock.Subheading("We Can Be Wrong"),
            LegalBlock.Paragraph(
                "FinnaCalc's figures, analysis, and explanations can contain mistakes, and data " +
                    "feeds can be stale or wrong. Do your own research before acting on anything " +
                    "here. If a number matters, check it at the source: your bank, your " +
                    "brokerage, or a qualified professional."
            ),
            LegalBlock.Subheading("No Warranty"),
            LegalBlock.Paragraph(
                "The services are provided \"as is\" without any warranty of any kind, either " +
                    "express or implied, including but not limited to warranties of " +
                    "merchantability, fitness for a particular purpose, or non-infringement."
            ),
        ),
    ),
    LegalSection(
        title = "Limitation of Liability",
        icon = Icons.Filled.Build,
        tint = IconColor.red,
        blocks = listOf(
            LegalBlock.Paragraph(
                "To the fullest extent permitted by law, FinnaCalc shall not be liable for any " +
                    "indirect, incidental, special, consequential, or punitive damages, including " +
                    "but not limited to:"
            ),
            LegalBlock.Bullets(
                listOf(
                    "Financial losses resulting from use of the calculators, estimates, or analysis",
                    "Investment or trading losses, including orders placed through a connected " +
                        "brokerage",
                    "Business interruption or loss of profits",
                    "Data loss or corruption",
                    "Third-party claims or damages, including those arising from connected banks, " +
                        "brokerages, or data providers",
                )
            ),
            LegalBlock.Paragraph(
                "Total liability for any claims arising from your use of the services shall not " +
                    "exceed the amount paid for the services (which is $0 for free services)."
            ),
        ),
    ),
    LegalSection(
        title = "Subscriptions and Billing",
        blocks = listOf(
            LegalBlock.Paragraph(
                "Paid plans (Budgeting Plus, Investing Plus, FinnaCalc Pro) are auto-renewing " +
                    "subscriptions. Depending on where you subscribe, payment is processed by the " +
                    "app store you bought through (such as the Apple App Store or Google Play) or " +
                    "by our payment processor on finnacalc.com. They renew until you cancel, and " +
                    "you cancel in the same place you bought: your device or store settings for " +
                    "store purchases, or your account on the website. Canceling stops the next " +
                    "renewal and keeps the plan running through the period you already paid for."
            ),
            LegalBlock.Bullets(
                listOf(
                    "Prices are shown before you subscribe. If a price changes, you are told and " +
                        "asked before the new one is charged",
                    "Plans that include bank connections cover 2 connected bank logins per " +
                        "account; each additional login is $2 per month",
                    "Ad-free applies to the pages the plan covers; FinnaCalc Pro removes ads " +
                        "everywhere",
                    "Refunds follow the policies of whoever processed the payment: the app store " +
                        "for store purchases, or ours for purchases made on the website",
                )
            ),
        ),
    ),
    LegalSection(
        title = "Intellectual Property Rights",
        blocks = listOf(
            LegalBlock.Paragraph(
                "The FinnaCalc website and apps, including their content, features, and " +
                    "functionality, are owned by FinnaCalc and are protected by copyright, " +
                    "trademark, and other intellectual property laws."
            ),
            LegalBlock.Paragraph(
                "You may use the services for personal and business purposes, but you may not:"
            ),
            LegalBlock.Bullets(
                listOf(
                    "Copy, modify, or distribute content without permission",
                    "Use trademarks or branding without authorization",
                    "Create derivative works based on the services",
                    "Reverse engineer or attempt to extract source code",
                )
            ),
        ),
    ),
    LegalSection(
        title = "Privacy and Data Protection",
        blocks = listOf(
            LegalBlock.Paragraph(
                "Your privacy is important. The collection and use of personal information is " +
                    "governed by the Privacy Policy, which is incorporated into these Terms by " +
                    "reference. By using the services, you consent to the collection and use of " +
                    "information as described in the Privacy Policy."
            )
        ),
    ),
    LegalSection(
        title = "Termination",
        blocks = listOf(
            LegalBlock.Paragraph(
                "Access to the services may be terminated or suspended immediately, without " +
                    "prior notice or liability, for any reason, including breach of these Terms. " +
                    "Upon termination, your right to use the services will cease immediately."
            )
        ),
    ),
    LegalSection(
        title = "Governing Law and Jurisdiction",
        blocks = listOf(
            LegalBlock.Paragraph(
                "These Terms shall be governed by and construed in accordance with the laws of " +
                    "the United States, without regard to conflict of law principles. Any " +
                    "disputes arising from these Terms or your use of the services shall be " +
                    "resolved through binding arbitration or in the courts of competent " +
                    "jurisdiction."
            )
        ),
    ),
    LegalSection(
        title = "Severability and Entire Agreement",
        blocks = listOf(
            LegalBlock.Paragraph(
                "If any provision of these Terms is held to be invalid or unenforceable, the " +
                    "remaining provisions will remain in full force and effect."
            ),
            LegalBlock.Paragraph(
                "These Terms, together with the Privacy Policy, constitute the entire agreement " +
                    "between you and FinnaCalc regarding your use of the services."
            ),
        ),
    ),
    LegalSection(
        title = "Contact Information",
        blocks = listOf(
            LegalBlock.Paragraph(
                "If you have any questions about these Terms of Service, please make contact:"
            ),
            LegalBlock.Contact("Help:", "helpfinnacalc@gmail.com"),
            LegalBlock.Contact("Inquiries:", "finnacalc@gmail.com"),
        ),
    ),
)

@Composable
fun TermsScreen() {
    val context = LocalContext.current
    LegalPage("Terms of Service", effectiveDate = null, sections = termsSections) { email ->
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("mailto:$email")))
    }
}
