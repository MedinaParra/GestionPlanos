package com.example.data.html

object LocalHtmlGenerator {

    fun generateOtWebFormHtml(appName: String, driveFolder: String): String {
        return """
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Formulario Web - Solicitud de Fabricación y Firma de Planos</title>
    <style>
        :root {
            --primary: #0072C6;
            --bg: #0F172A;
            --card: #1E293B;
            --text: #F8FAFC;
            --accent: #00E5FF;
            --border: #334155;
        }
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background-color: var(--bg);
            color: var(--text);
            margin: 0;
            padding: 20px;
            display: flex;
            justify-content: center;
        }
        .container {
            max-width: 800px;
            width: 100%;
            background-color: var(--card);
            border-radius: 12px;
            padding: 30px;
            box-shadow: 0 10px 25px rgba(0,0,0,0.5);
            border: 1px solid var(--border);
        }
        .header {
            border-bottom: 2px solid var(--primary);
            padding-bottom: 15px;
            margin-bottom: 25px;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .header h1 {
            margin: 0;
            color: var(--accent);
            font-size: 1.8rem;
        }
        .tag {
            background-color: var(--primary);
            color: #fff;
            padding: 4px 12px;
            border-radius: 20px;
            font-size: 0.85rem;
            font-weight: bold;
        }
        .form-group {
            margin-bottom: 20px;
        }
        label {
            display: block;
            margin-bottom: 8px;
            font-weight: 600;
            color: #94A3B8;
        }
        input, select, textarea {
            width: 100%;
            padding: 12px;
            border-radius: 6px;
            border: 1px solid var(--border);
            background-color: #090D16;
            color: #fff;
            box-sizing: border-box;
            font-size: 1rem;
        }
        input:focus, select:focus, textarea:focus {
            outline: none;
            border-color: var(--accent);
            box-shadow: 0 0 8px rgba(0, 229, 255, 0.3);
        }
        .row {
            display: flex;
            gap: 20px;
        }
        .row .form-group {
            flex: 1;
        }
        .btn-submit {
            background: linear-gradient(135deg, var(--primary), var(--accent));
            color: #000;
            font-weight: bold;
            font-size: 1.1rem;
            border: none;
            padding: 14px 28px;
            border-radius: 8px;
            cursor: pointer;
            width: 100%;
            transition: transform 0.2s, box-shadow 0.2s;
        }
        .btn-submit:hover {
            transform: translateY(-2px);
            box-shadow: 0 5px 15px rgba(0, 229, 255, 0.4);
        }
        .info-box {
            background-color: #002B49;
            border-left: 4px solid var(--accent);
            padding: 15px;
            border-radius: 4px;
            margin-bottom: 25px;
            font-size: 0.9rem;
            line-height: 1.5;
        }
        .drive-sync-badge {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            color: #38BDF8;
            font-size: 0.9rem;
            margin-top: 10px;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div>
                <h1 style="margin:0; font-size: 1.8rem; color: var(--accent);">SKM INDUSTRIAL</h1>
                <span style="font-size: 0.9rem; color: #94A3B8; font-weight: 600;">FirmaPlanos OT - Solicitud Web de Fabricación</span>
            </div>
            <span class="tag">SKM Industrial Drive</span>
        </div>
        
        <div class="info-box">
            <strong>Sincronización Automática:</strong> Al enviar este formulario, el plano PDF se cargará en la carpeta configurada en Google Drive: <code>$driveFolder</code>. La aplicación APK detectará la nueva OT y notificará a los 6 usuarios aprobadores con validación biométrica.
        </div>

        <form id="otForm" onsubmit="event.preventDefault(); simulateSubmission();">
            <div class="row">
                <div class="form-group">
                    <label for="otNumber">Número de OT (Orden de Trabajo)</label>
                    <input type="text" id="otNumber" placeholder="Ej: OT-2026-088" required value="OT-2026-088">
                </div>
                <div class="form-group">
                    <label for="category">Categoría de Fabricación</label>
                    <select id="category" required>
                        <option value="manto">Manto y Calderería</option>
                        <option value="eje" selected>Eje Mecanizado</option>
                        <option value="poleas">Polea Completa</option>
                        <option value="sellos">Sellos de Agua / Hidráulicos</option>
                        <option value="armado_taller">Plano de Armado Taller</option>
                    </select>
                </div>
            </div>

            <div class="form-group">
                <label for="title">Título del Trabajo / Componente</label>
                <input type="text" id="title" placeholder="Ej: Fabricación y Mecanizado de Eje Principal HD 800mm" required value="Fabricación Eje HD 800mm">
            </div>

            <div class="row">
                <div class="form-group">
                    <label for="client">Área o Planta Destino</label>
                    <input type="text" id="client" placeholder="Ej: Mantenimiento Planta Norte" required value="Planta Concatenación Chancado">
                </div>
                <div class="form-group">
                    <label for="deadline">Fecha Límite de Aprobación</label>
                    <input type="date" id="deadline" required value="2026-08-05">
                </div>
            </div>

            <div class="form-group">
                <label for="file">Cargar Plano en Formato PDF (Carga a Google Drive)</label>
                <input type="file" id="file" accept=".pdf" required>
            </div>

            <div class="form-group">
                <label for="notes">Observaciones / Especificaciones de Tolerancia</label>
                <textarea id="notes" rows="3" placeholder="Ingresa tolerancias críticas, rugosidad N6, material AISI 4140..."></textarea>
            </div>

            <button type="submit" class="btn-submit">📤 Enviar OT y Crear Carpeta en Google Drive</button>
        </form>

        <div class="drive-sync-badge">
            ⚡ Conectado con la App APK Android mediante Sincronizador de Drive
        </div>
    </div>

    <script>
        function simulateSubmission() {
            const ot = document.getElementById('otNumber').value;
            alert('✅ Solicitud enviada exitosamente para ' + ot + '!\nSe ha creado la carpeta en Google Drive y enviado la notificación biométrica a los 6 aprobadores.');
        }
    </script>
</body>
</html>
        """.trimIndent()
    }

    fun generatePcViewerWebLink(otId: String, title: String, driveUrl: String): String {
        return "https://ais-dev-i6k66jrgpazelwlaieh43t-424958906519.us-west1.run.app/viewer?ot=$otId&ref=pc_screen"
    }
}
