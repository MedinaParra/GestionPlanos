package com.example.data.excel

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.local.entity.WorkOrderEntity
import com.example.data.local.entity.BlueprintEntity
import com.example.data.local.entity.ApproverUserEntity
import com.example.data.local.entity.SignatureLogEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object ExcelExporter {

    fun generateControlDocumentalExcelHtml(
        workOrders: List<WorkOrderEntity>,
        blueprintsMap: Map<String, List<BlueprintEntity>>,
        approvers: List<ApproverUserEntity>,
        signatureLogs: List<SignatureLogEntity>
    ): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val html = StringBuilder()

        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">")
        html.append("<style>")
        html.append("body { font-family: Arial, sans-serif; margin: 20px; background-color: #f8fafc; }")
        html.append("h2 { color: #0f172a; border-bottom: 3px solid #2563eb; padding-bottom: 8px; }")
        html.append(".summary { background: #eff6ff; padding: 12px; border-radius: 8px; border: 1px solid #bfdbfe; margin-bottom: 20px; }")
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 10px; background: white; font-size: 13px; }")
        html.append("th { background-color: #1e3a8a; color: white; text-align: left; padding: 10px; border: 1px solid #cbd5e1; }")
        html.append("td { padding: 8px 10px; border: 1px solid #cbd5e1; color: #1e293b; }")
        html.append("tr:nth-child(even) { background-color: #f1f5f9; }")
        html.append(".aprobado { background-color: #d1fae5; color: #065f46; font-weight: bold; text-align: center; }")
        html.append(".pendiente { background-color: #fee2e2; color: #991b1b; font-weight: bold; text-align: center; }")
        html.append("</style></head><body>")

        html.append("<h2>SKM INDUSTRIAL • CONTROL DOCUMENTAL Y BASE DE DATOS EXCEL EN LA NUBE</h2>")
        html.append("<div class=\"summary\">")
        html.append("<strong>Módulo:</strong> Control Documental y Base de Datos de Planos Biométricos<br>")
        html.append("<strong>Fecha Sincronización Nube Drive:</strong> ${dateFormat.format(Date())}<br>")
        html.append("<strong>Total Ordenes de Trabajo:</strong> ${workOrders.size} | <strong>Total Planos Registrados:</strong> ${blueprintsMap.values.flatten().size}")
        html.append("</div>")

        html.append("<table>")
        html.append("<thead><tr>")
        html.append("<th>Código OT</th>")
        html.append("<th>Plano / Archivo PDF</th>")
        html.append("<th>Revisión</th>")
        html.append("<th>Categoría Taller</th>")
        html.append("<th>Estado Control Documental</th>")
        html.append("<th>Usuario Firmante</th>")
        html.append("<th>RUT Firmante</th>")
        html.append("<th>Cargo / Rol</th>")
        html.append("<th>Hash Firma Biométrica</th>")
        html.append("<th>Fecha Firma</th>")
        html.append("<th>Carpeta Google Drive</th>")
        html.append("</tr></thead><tbody>")

        for (ot in workOrders) {
            val blueprints = blueprintsMap[ot.id] ?: emptyList()
            if (blueprints.isEmpty()) {
                html.append("<tr>")
                html.append("<td><b>${ot.id}</b></td>")
                html.append("<td>General OT (${ot.title})</td>")
                html.append("<td>Rev. A</td>")
                html.append("<td>${ot.categoryDisplayName}</td>")
                html.append("<td class=\"${if (ot.status == "APROBADO") "aprobado" else "pendiente"}\">${if (ot.status == "APROBADO") "APTO PARA FABRICACIÓN" else "PENDIENTE CONTROL"}</td>")
                html.append("<td>Sin Registrar</td>")
                html.append("<td>--</td>")
                html.append("<td>--</td>")
                html.append("<td>--</td>")
                html.append("<td>--</td>")
                html.append("<td><a href=\"${ot.driveFolderUrl}\">Drive Link</a></td>")
                html.append("</tr>")
            } else {
                for (bp in blueprints) {
                    val log = signatureLogs.find { it.blueprintId == bp.id }
                    val user = approvers.find { it.name == log?.approverName }
                    val isSigned = bp.isSigned

                    html.append("<tr>")
                    html.append("<td><b>${ot.id}</b></td>")
                    html.append("<td>${bp.fileName}</td>")
                    html.append("<td>${bp.revision}</td>")
                    html.append("<td>${ot.categoryDisplayName}</td>")
                    html.append("<td class=\"${if (isSigned) "aprobado" else "pendiente"}\">${if (isSigned) "APTO PARA FABRICACIÓN" else "NO APTO / PENDIENTE"}</td>")
                    html.append("<td>${log?.approverName ?: (user?.name?.ifBlank { "Pendiente" } ?: "Pendiente")}</td>")
                    html.append("<td>${log?.approverRut ?: (user?.rut?.ifBlank { "--" } ?: "--")}</td>")
                    html.append("<td>${log?.approverRole ?: (user?.roleTitle ?: "Aprobador Taller")}</td>")
                    html.append("<td><code>${bp.signatureHash ?: log?.signatureHash ?: "--"}</code></td>")
                    html.append("<td>${bp.signatureDate ?: "--"}</td>")
                    html.append("<td><a href=\"${ot.driveFolderUrl}\">Drive Link</a></td>")
                    html.append("</tr>")
                }
            }
        }

        html.append("</tbody></table></body></html>")
        return html.toString()
    }

    fun generateCsvReport(
        workOrders: List<WorkOrderEntity>,
        blueprintsMap: Map<String, List<BlueprintEntity>>,
        approvers: List<ApproverUserEntity>,
        signatureLogs: List<SignatureLogEntity>
    ): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val csv = StringBuilder()

        // CSV Header
        csv.append("REPORTE DE TRAZABILIDAD Y FIRMA DE PLANOS OT - FABRICACIÓN\n")
        csv.append("Fecha de Generación;${dateFormat.format(Date())}\n\n")

        // Table Header
        csv.append("Código OT;Categoría;Título / Componente;Área Destino;Estado OT;Planos Firmados;Planos Pendientes;Avance Firma %;Tiempo Transcurrido (Horas);Fecha Límite;Alerta Vencimiento;Revisores Pendientes;Revisores Apoblados;Enlace Google Drive\n")

        for (ot in workOrders) {
            val blueprints = blueprintsMap[ot.id] ?: emptyList()
            val signedBlueprintsCount = blueprints.count { it.isSigned }
            val pendingBlueprintsCount = blueprints.size - signedBlueprintsCount
            val progressPercent = if (blueprints.isNotEmpty()) (signedBlueprintsCount * 100 / blueprints.size) else (ot.signedCount * 100 / ot.totalApproversNeeded)

            // Elapsed time calculation
            val nowMs = System.currentTimeMillis()
            val elapsedMs = if (ot.status == "APROBADO") {
                // If approved, time until last log or now
                val lastLog = signatureLogs.filter { it.workOrderId == ot.id }.maxByOrNull { it.timestamp }
                (lastLog?.timestamp ?: nowMs) - ot.createdAt
            } else {
                nowMs - ot.createdAt
            }
            val elapsedHours = elapsedMs / (1000 * 60 * 60)

            // Approver status
            val otLogs = signatureLogs.filter { it.workOrderId == ot.id }
            val signedApproverNames = otLogs.map { it.approverName }.distinct()
            val pendingApproverNames = approvers.filter { approver -> !signedApproverNames.contains(approver.name) }.map { "${it.name} (${it.roleTitle})" }
            val approvedApproverNames = approvers.filter { approver -> signedApproverNames.contains(approver.name) }.map { "${it.name} (${it.roleTitle})" }

            val deadlineStr = dateFormat.format(Date(ot.deadlineTimestamp))
            val isExpired = nowMs > ot.deadlineTimestamp
            val alertStr = when {
                ot.status == "APROBADO" -> "APROBADO DENTRO DE PLAZO"
                isExpired -> "CRÍTICO - VENCIDO"
                ot.isNearDeadline -> "ALERTA - PRÓXIMO A VENCER"
                else -> "EN PLAZO REGULAR"
            }

            csv.append("${ot.id};")
            csv.append("${ot.categoryDisplayName};")
            csv.append("\"${ot.title}\";")
            csv.append("\"${ot.clientOrArea}\";")
            csv.append("${ot.status};")
            csv.append("$signedBlueprintsCount;")
            csv.append("$pendingBlueprintsCount;")
            csv.append("$progressPercent%;")
            csv.append("${elapsedHours}h;")
            csv.append("$deadlineStr;")
            csv.append("$alertStr;")
            csv.append("\"${pendingApproverNames.joinToString(", ").ifEmpty { "Ninguno" }}\";")
            csv.append("\"${approvedApproverNames.joinToString(", ").ifEmpty { "Ninguno" }}\";")
            csv.append("${ot.driveFolderUrl}\n")
        }

        // Detailed Blueprints Section
        csv.append("\n\nDETALLE DE PLANOS POR ORDEN DE TRABAJO\n")
        csv.append("Código OT;Código Plano;Nombre Archivo;Revisión;Estado Plano;Hash Biométrico Firma;Fecha Firma\n")

        for ((otId, blueprints) in blueprintsMap) {
            for (bp in blueprints) {
                csv.append("$otId;")
                csv.append("${bp.id};")
                csv.append("\"${bp.fileName}\";")
                csv.append("${bp.revision};")
                csv.append("${if (bp.isSigned) "FIRMADO Y APROBADO" else "PENDIENTE"};")
                csv.append("${bp.signatureHash ?: "N/A"};")
                csv.append("${bp.signatureDate ?: "Pendiente"}\n")
            }
        }

        return csv.toString()
    }

    fun exportAndShareCsv(context: Context, csvContent: String): File {
        val fileName = "Informe_Firma_Planos_OT_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        file.writeText(csvContent, Charsets.UTF_8)
        return file
    }

    fun shareCsvFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Informe de Firma de Planos y Métricas OT")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir Informe Excel / CSV"))
    }
}
