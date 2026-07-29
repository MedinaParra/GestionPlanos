# SKM Industrial Gestión de Planos

Aplicación Android para controlar planos PDF, observaciones, revisiones, aprobaciones y firmas usando Google Drive como repositorio documental.

La versión 6 toma los principios útiles del flujo de Autodesk Docs, pero los reduce a un proceso específico para SKM Industrial. No intenta reproducir todos los módulos de Autodesk Construction Cloud.

## Flujo oficial

```text
1. Administrador carga PDF y define OT, código y revisión
2. La app crea OT XXX / Rev N y genera la copia NO APTO
3. El plano queda EN REVISIÓN y se asigna al primer revisor
4. El revisor puede crear borradores privados y publicar observaciones
5. El revisor decide:
   ├── Aprobar y firmar
   └── Solicitar cambios
6. Si aprueba, el turno pasa al siguiente revisor
7. Si solicita cambios, el flujo se detiene hasta cargar una nueva revisión
8. Al aprobar todos, se genera el PDF APTO PARA FABRICACIÓN
```

La aprobación es secuencial para evitar que dos personas modifiquen simultáneamente la misma copia firmada.

## Estados visibles

- `En revisión`
- `Cambios solicitados`
- `Apto para fabricación`

## Funciones mantenidas

- Acceso con cuenta Google corporativa.
- Organización automática por `OT XXX / Rev N`.
- Norma mínima de nombre: OT, código de plano y revisión.
- Copia roja translúcida `NO APTO PARA FABRICACIÓN` durante la revisión.
- Visor PDF con zoom de 100 % a 600 % y desplazamiento.
- Observaciones ubicadas en una hoja y posición del plano.
- Borradores privados almacenados en el Drive personal del autor.
- Publicación de observaciones para todos los miembros del proyecto.
- Solicitud de cambios con motivo obligatorio.
- Aprobación y firma confirmada mediante biometría o bloqueo del teléfono.
- Firma acumulativa con nombre, cargo, RUT, fecha, hora y firma manual.
- Historial de carga, observaciones, aprobaciones, solicitudes de cambios y cierre.
- Panel de administración de usuarios y firmantes obligatorios.
- Notificaciones locales a las 08:00 y 15:00, con escalamiento después de 36 horas.
- PDF final azul `APTO PARA FABRICACIÓN` cuando todos aprueban.

## Observaciones privadas y publicadas

Los comentarios no alteran el PDF original.

### Borrador privado

- Se guarda en `GestionPlanosSKM-Privado/comentarios-borradores.json` dentro del Drive del autor.
- Solo el autor puede verlo, editarlo, reubicarlo, publicarlo o eliminarlo.
- No queda almacenado en la carpeta compartida del proyecto.

### Observación publicada

- Se guarda en `GestionPlanos-Sistema/comentarios-publicados.json`.
- Todos los usuarios del proyecto pueden verla.
- El autor o un administrador puede editarla o eliminarla.
- Los comentarios creados con versiones anteriores se migran como publicados.

## Solicitar cambios

El revisor que tiene el turno puede detener la revisión y escribir el motivo. El documento pasa a `CAMBIOS_SOLICITADOS`, deja de generar avisos de firma y no puede seguir firmándose.

El administrador debe corregir el plano y cargar una revisión nueva, por ejemplo `Rev 1`. La revisión anterior se conserva como historial y no se sobrescribe.

## Estructura de Drive

```text
Drive privado de cada usuario/
└── GestionPlanosSKM-Privado/
    ├── claves-configuracion.json
    └── comentarios-borradores.json

Carpeta principal compartida/
├── GestionPlanos-Sistema/
│   ├── usuarios.json
│   ├── configuracion-flujo.json
│   ├── comentarios-publicados.json
│   └── historial-flujo.json
├── control-documental.json
├── Control de Documentos SKM
└── OT 1234/
    ├── Rev 0/
    │   ├── Original/
    │   ├── Revision/
    │   ├── Firmas/
    │   └── Final/
    └── Rev 1/
        ├── Original/
        ├── Revision/
        ├── Firmas/
        └── Final/
```

## Funciones de Autodesk Docs que no se incorporan

Se excluyen porque no forman parte del objetivo de revisión y liberación de planos PDF de SKM:

- Conjuntos y paquetes de archivos.
- Modelos 3D, ViewCube, secciones y navegación BIM.
- Revit, extracción automatizada de dibujos e intercambios de datos.
- Formularios, incidencias fotográficas y herramientas de terreno.
- Informes de transmisión.
- Vínculos públicos y colaboración anónima.
- Sincronización con AutoCAD.
- Edición de Word, Excel y PowerPoint.
- Búsquedas guardadas complejas y exploración de duplicados.
- Plantillas ISO 19650 completas.

La app mantiene una nomenclatura básica y suficiente para SKM, sin implementar el motor completo ISO 19650.

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

No se necesita Firebase, servidor propio ni `google-services.json`.

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
Version code: 6
Version name: 6.0.0
Rama: agent/document-management-v2
```

El PR permanece en borrador hasta validar físicamente el flujo completo con un PDF real de varias hojas, al menos dos cuentas corporativas y dos teléfonos.
