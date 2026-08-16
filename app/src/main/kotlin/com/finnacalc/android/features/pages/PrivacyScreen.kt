//
// PrivacyScreen.kt
//
// Port of iOS Features/Pages/PrivacyView.swift — the Privacy Policy as a stack
// of titled cards. Copy ported verbatim; rendering is LegalPage.kt.
//

package com.finnacalc.android.features.pages

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val privacySections = listOf(
    LegalSection(
        title = "Introduction",
        icon = Icons.Filled.Visibility,
        tint = IconColor.blue,
        blocks = listOf(
            LegalBlock.Paragraph(
                "Your privacy matters to us. This Privacy Policy explains how we collect, use, " +
                    "share, and protect your information when you use the FinnaCalc app, " +
                    "including its calculators, budgeting tools, investing research, guided tax " +
                    "estimator, education content, and the FinnaBot assistant."
            ),
            LegalBlock.Paragraph(
                "This policy covers FinnaCalc everywhere it runs: the apps for phones and tablets " +
                    "and the website at finnacalc.com. By using FinnaCalc, you agree to how we " +
                    "collect and use information under this policy. If you don't agree with it, " +
                    "please don't use the services."
            ),
        ),
    ),
    LegalSection(
        title = "Information Collected",
        icon = Icons.Filled.Storage,
        tint = IconColor.green,
        blocks = listOf(
            LegalBlock.Subheading("Information You Provide"),
            LegalBlock.Bullets(
                listOf(
                    "Optional account details (name, email) if you sign up, stored with our " +
                        "authentication provider (Supabase)",
                    "Calculator, budgeting, and tax-estimator inputs, which are saved on your " +
                        "device, not on our servers",
                    "Bank statements you import, which are parsed and stored only on your device",
                    "Messages you send to FinnaBot and the Budget Analysis assistant",
                    "Contact information and feedback when you reach out",
                )
            ),
            LegalBlock.Subheading("Automatically Collected Information"),
            LegalBlock.Bullets(
                listOf(
                    "The stock symbols and market pages you request (needed to fetch quotes, " +
                        "charts, and news)",
                    "Basic server logs (IP address, timestamps) when the app or website talks to " +
                        "our services",
                    "Device information such as OS version",
                )
            ),
            LegalBlock.Subheading("Bank & Brokerage Connections"),
            LegalBlock.Bullets(
                listOf(
                    "Bank and brokerage links run through Plaid and SnapTrade. Your bank " +
                        "credentials go to them directly and never touch FinnaCalc's servers",
                    "Imported transactions and holdings are stored on your device",
                )
            ),
            LegalBlock.Callout(
                lead = "Important:",
                body = " Your budget, goals, history, and tax-estimator answers live on your " +
                    "device. Sensitive tax fields (Social Security numbers, bank details) are " +
                    "never saved, and the tax estimator does not transmit your return, since " +
                    "e-filing isn't enabled yet.",
                tone = CalloutTone.Info,
            ),
        ),
    ),
    LegalSection(
        title = "How Information Is Used",
        icon = Icons.Filled.Group,
        tint = IconColor.purple,
        blocks = listOf(
            LegalBlock.LeadBullets(
                listOf(
                    "Service Provision:" to
                        " To provide calculators, budgeting, market data, tax estimation, and the " +
                        "AI assistant",
                    "Personalization:" to
                        " To answer FinnaBot and Budget Analysis questions using the budget " +
                        "snapshot you share in that conversation",
                    "Improvement:" to " To understand usage and improve the apps and website",
                    "Communication:" to " To respond to inquiries and provide support",
                    "Security:" to
                        " To detect, prevent, and address technical issues and security threats",
                    "Legal Compliance:" to " To comply with applicable laws and regulations",
                )
            )
        ),
    ),
    LegalSection(
        title = "Information Sharing and Disclosure",
        icon = Icons.Filled.Lock,
        tint = IconColor.red,
        blocks = listOf(
            LegalBlock.Paragraph(
                "Personal information is not sold, traded, or otherwise transferred to third " +
                    "parties except in the following circumstances:"
            ),
            LegalBlock.LeadBullets(
                listOf(
                    "Service Providers:" to
                        " Supabase (accounts), Plaid and SnapTrade (bank/brokerage links, under " +
                        "their own privacy policies), Google (AI responses for FinnaBot), and " +
                        "market-data providers that receive the ticker symbols you view",
                    "Purchases:" to
                        " Payment is handled by whichever processor you subscribe through: the " +
                        "app store you bought from (such as the Apple App Store or Google Play) " +
                        "or our payment processor on the website. They hold your card details; " +
                        "FinnaCalc only learns which plan is active",
                    "Legal Requirements:" to
                        " When required by law or to protect rights and safety",
                    "Business Transfers:" to
                        " In connection with a merger, acquisition, or sale of assets",
                    "Consent:" to " When you have given explicit consent for sharing",
                )
            ),
        ),
    ),
    LegalSection(
        title = "Data Security",
        icon = Icons.Filled.Shield,
        tint = IconColor.orange,
        blocks = listOf(
            LegalBlock.Paragraph(
                "We use sensible technical and organizational safeguards to protect your " +
                    "information from unauthorized access, alteration, disclosure, or loss."
            ),
            LegalBlock.Bullets(
                listOf(
                    "Personal finance data (budget, goals, tax answers) stays on your device",
                    "SSL/TLS encryption for everything sent to our services",
                    "Bank credentials handled only by Plaid and SnapTrade, never by FinnaCalc",
                    "Limited access to personal information on a need-to-know basis",
                    "Secure hosting infrastructure",
                )
            ),
            LegalBlock.Callout(
                lead = "Note:",
                body = " While efforts are made to protect your information, no method of " +
                    "transmission over the internet or electronic storage is 100% secure. " +
                    "Absolute security cannot be guaranteed.",
                tone = CalloutTone.Caution,
            ),
        ),
    ),
    LegalSection(
        title = "On-Device Storage & Preferences",
        blocks = listOf(
            LegalBlock.Paragraph(
                "The app stores your working data and preferences locally on your device rather " +
                    "than with tracking cookies:"
            ),
            LegalBlock.LeadBullets(
                listOf(
                    "Your data:" to
                        " Budget items, savings and investing goals, history snapshots, " +
                        "watchlist, and tax-estimator answers",
                    "Preferences:" to
                        " Appearance (light/dark), chart settings, and similar choices",
                    "Notifications:" to
                        " Goal alerts and bill reminders are scheduled on your device; nothing " +
                        "about them is sent to a server",
                )
            ),
            LegalBlock.Paragraph(
                "Deleting the app removes this local data (the website keeps its own in your " +
                    "browser). You can also clear budgeting data from the Budgeting page and " +
                    "restart the tax estimator at any time. FinnaCalc does not use advertising " +
                    "trackers."
            ),
        ),
    ),
    LegalSection(
        title = "Your Privacy Rights",
        blocks = listOf(
            LegalBlock.Paragraph("Depending on your location, you may have the following rights:"),
            LegalBlock.LeadBullets(
                listOf(
                    "Access:" to " Request information about the personal data held about you",
                    "Correction:" to " Request correction of inaccurate or incomplete information",
                    "Deletion:" to " Request deletion of your personal information",
                    "Portability:" to " Request a copy of your data in a structured format",
                    "Objection:" to " Object to certain processing of your information",
                )
            ),
        ),
    ),
    LegalSection(
        title = "Children's Privacy",
        blocks = listOf(
            LegalBlock.Paragraph(
                "The services are not intended for children under 13 years of age. Personal " +
                    "information from children under 13 is not knowingly collected. If you are a " +
                    "parent or guardian and believe your child has provided personal information, " +
                    "please make contact immediately."
            )
        ),
    ),
    LegalSection(
        title = "Changes to This Privacy Policy",
        icon = Icons.Filled.Description,
        tint = IconColor.blue,
        blocks = listOf(
            LegalBlock.Paragraph(
                "This Privacy Policy may be updated from time to time. You will be notified of " +
                    "any changes by posting the new Privacy Policy on this page. You are advised " +
                    "to review this Privacy Policy periodically for any changes."
            )
        ),
    ),
    LegalSection(
        title = "Contact Us",
        blocks = listOf(
            LegalBlock.Paragraph(
                "If you have any questions about this Privacy Policy or privacy practices, " +
                    "please make contact:"
            ),
            LegalBlock.Contact("Help:", "helpfinnacalc@gmail.com"),
            LegalBlock.Contact("Inquiries:", "finnacalc@gmail.com"),
        ),
    ),
)

@Composable
fun PrivacyScreen() {
    val context = LocalContext.current
    LegalPage("Privacy Policy", effectiveDate = null, sections = privacySections) { email ->
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("mailto:$email")))
    }
}
