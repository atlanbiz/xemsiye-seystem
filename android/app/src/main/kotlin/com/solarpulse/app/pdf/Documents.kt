package com.solarpulse.app.pdf

import android.content.Context
import com.solarpulse.app.R
import com.solarpulse.app.report.Report
import com.solarpulse.core.format.Fmt
import com.solarpulse.core.model.Database
import com.solarpulse.core.model.Invoice
import com.solarpulse.core.model.InvoiceStatus
import java.io.File

/** Invoice PDF (web `InvoiceDoc`), localized and RTL-correct. [ctx] must carry the UI locale. */
fun invoicePdf(ctx: Context, inv: Invoice, db: Database, fmt: Fmt): File {
    val pdf = PdfBuilder(rtl = fmt.lang.rtl)
    val site = db.site(inv.siteId)
    val status = ctx.getString(
        when (inv.status) {
            InvoiceStatus.PAID -> R.string.status_paid
            InvoiceStatus.PENDING -> R.string.status_pending
            InvoiceStatus.OVERDUE -> R.string.status_overdue
        },
    )
    pdf.newPage()
    pdf.header("SolarPulse", "${db.settings.company}\n${db.settings.email}", "${ctx.getString(R.string.bill_invoice)} ${inv.number}\n$status")
    val kv = mutableListOf(
        ctx.getString(R.string.bill_billTo) to "${inv.customer}\n${site?.name.orEmpty()}",
        ctx.getString(R.string.bill_issued) to fmt.date(inv.issuedAt),
        ctx.getString(R.string.bill_due) to fmt.date(inv.dueAt),
        ctx.getString(R.string.bill_period) to fmt.month(inv.period),
    )
    inv.paidAt?.let { kv += ctx.getString(R.string.bill_paidOn) to fmt.date(it) }
    pdf.keyValues(kv, cols = 3)
    pdf.space(6f)
    pdf.table(
        columns = listOf(
            ctx.getString(R.string.common_description),
            ctx.getString(R.string.bill_energy),
            ctx.getString(R.string.bill_rate),
            ctx.getString(R.string.bill_amount),
        ),
        rows = listOf(
            listOf(
                "${ctx.getString(R.string.rep_energy)} — ${site?.name.orEmpty()}",
                "${fmt.num(inv.energyKwh)} kWh",
                fmt.money2(inv.rate),
                fmt.money2(inv.amount),
            ),
        ),
        weights = listOf(2.4f, 1.1f, 0.9f, 1.1f),
    )
    pdf.totalBox(ctx.getString(R.string.common_total), fmt.money2(inv.amount))
    pdf.footer(ctx.getString(R.string.bill_thankYou))
    return pdf.save(ctx, "${inv.number}.pdf")
}

/** Report PDF for any of the 5 report kinds. */
fun reportPdf(ctx: Context, report: Report, db: Database, fmt: Fmt, generatedAt: String): File {
    val pdf = PdfBuilder(rtl = fmt.lang.rtl)
    pdf.newPage()
    pdf.header(report.title, "${db.settings.company} · ${report.rangeLabel}", report.scopeLabel)
    pdf.tiles(report.summary)
    if (report.chart.size > 1) pdf.areaChart(report.chart.map { it.second }, caption = report.chartCaption)
    if (report.display.isEmpty()) {
        pdf.text(ctx.getString(R.string.common_noData))
    } else {
        pdf.table(report.columns, report.display, report.weights)
    }
    pdf.footer(ctx.getString(R.string.rep_generatedAt, generatedAt))
    return pdf.save(ctx, "${report.kind.key}-report-${report.from}_${report.to}.pdf")
}
