# SKM Industrial Gestión de Planos

Aplicación Android para administrar planos PDF, revisiones, firmas, comentarios y aprobación para fabricación usando Google Drive.

## Funciones principales

- Acceso con cuenta Google corporativa.
- Organización automática por `OT XXX / Rev N`.
- Copia roja `NO APTO PARA FABRICACIÓN` durante la revisión.
- Firmas acumulativas con nombre, cargo, RUT, fecha, hora y firma manual.
- PDF final azul `APTO PARA FABRICACIÓN` cuando termina el flujo.
- Panel de administración de usuarios y firmantes obligatorios.
- Notificaciones locales de pendientes.
- Visor PDF con zoom mediante gesto de pinza, desplazamiento y controles de acercar/alejar.
- Comentarios como cuadros de texto ubicados sobre una hoja específica.

## Zoom del visor

Dentro del visor:

- Pellizca con dos dedos para acercar o alejar.
- Arrastra con dos dedos para desplazarte por el plano ampliado.
- Usa los botones de acercar, alejar y restablecer.
- El rango de ampliación es de 100 % a 600 %.

## Comentarios

Los comentarios son anotaciones compartidas del sistema y no modifican el PDF original.

1. Abre un plano.
2. Pulsa **Agregar comentario**.
3. Toca la posición del plano donde deseas ubicarlo.
4. Escribe el texto y ajusta el ancho del cuadro.
5. Pulsa **Guardar**.

Cada comentario registra:

- Documento y hoja.
- Posición y ancho normalizados.
- Texto.
- Nombre y correo del autor.
- Fecha y hora.

El autor o un administrador puede editar, reubicar o eliminar el comentario. Los datos se sincronizan en:

```text
GestionPlanos-Sistema/comentarios-planos.json
```

## Estructura de Drive

```text
Carpeta principal/
├── GestionPlanos-Sistema/
│   ├── usuarios.json
│   ├── configuracion-flujo.json
│   └── comentarios-planos.json
├── control-documental.json
├── Control de Documentos SKM
└── OT 1234/
    └── Rev 0/
        ├── Original/
        ├── Revision/
        ├── Firmas/
        └── Final/
```

## Google Cloud

Cliente OAuth Android:

```text
Package: cl.skmindustrial.gestionplanos
SHA-1: 7A:16:5A:7B:C3:C7:6F:C9:48:C5:F3:47:33:92:A5:34:88:C9:D4:00
```

APIs y permisos:

```text
Google Drive API
Google Sheets API
https://www.googleapis.com/auth/drive
https://www.googleapis.com/auth/spreadsheets
```

No se necesita Firebase ni `google-services.json`.

## Compilación

Requisitos:

- JDK 17.
- Android SDK 36.
- Gradle 9.3.1.

```bash
gradle --no-daemon testDebugUnitTest
gradle --no-daemon assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Versión actual

```text
Version code: 5
Version name: 5.0.0
Rama: agent/document-management-v2
```

El PR permanece en borrador mientras se completa la validación física con cuentas corporativas, planos reales de varias hojas y más de un teléfono.
