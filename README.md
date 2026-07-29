# SKM Industrial Gestión de Planos

Aplicación Android de control documental conectada directamente a Google Drive. Administra OT, revisiones, perfiles de usuario, firmas manuales confirmadas con biometría o PIN y generación automática de copias `NO APTO PARA FABRICACIÓN` y `APTO PARA FABRICACIÓN`.

No necesita Firebase, servidor propio ni base de datos externa. Los archivos de control y los PDF se mantienen en la carpeta de Google Drive elegida por el administrador.

## Flujo de revisión implementado

Se utiliza revisión **secuencial** para evitar conflictos entre dos personas que firmen al mismo tiempo:

1. El administrador selecciona qué usuarios son firmantes obligatorios.
2. Al subir un plano se guarda el original y se crea una copia con sello rojo translúcido `NO APTO PARA FABRICACIÓN` en todas las hojas.
3. La app asigna el turno al primer firmante.
4. Cada firmante mueve su timbre sobre una vista del plano, confirma con huella o PIN y genera una nueva copia acumulativa.
5. El turno pasa al siguiente firmante.
6. Cuando firma el último usuario, se crea el PDF final con todas las firmas y el sello azul `APTO PARA FABRICACIÓN` en todas las hojas.

Antes de firmar, la app vuelve a leer el índice desde Drive para verificar que el turno siga correspondiendo al usuario.

## Perfiles y administración de usuarios

Cada cuenta Google que abre la carpeta queda registrada en `usuarios.json`. La primera cuenta registrada queda como `ADMIN`.

Cada usuario puede completar:

- Nombre completo.
- RUT.
- Cargo.
- Foto de perfil.
- Firma manual dibujada en pantalla.
- Tamaño predeterminado de su timbre.

El administrador puede:

- Activar o desactivar usuarios.
- Asignar rol `ADMIN`, `REVIEWER` o `USER`.
- Definir quién debe firmar obligatoriamente.
- Configurar los días disponibles para revisión.

## Información incluida en cada timbre

La firma se aplica en todas las hojas e incluye:

- Firma manual.
- Nombre completo.
- Cargo.
- RUT.
- Fecha.
- Hora.
- Identificación de SKM Industrial.

La huella, rostro, PIN, patrón o contraseña no se guardan. Android solo devuelve a la aplicación si la autenticación local fue correcta o cancelada.

## Estructura creada en Drive

```text
Carpeta principal/
├── GestionPlanos-Sistema/
│   ├── usuarios.json
│   └── configuracion-flujo.json
├── control-documental.json
├── Control de Documentos SKM
└── OT 1234/
    └── Rev 0/
        ├── Original/
        │   └── ORIGINAL_codigo.pdf
        ├── Revision/
        │   └── NO_APTO_codigo.pdf
        ├── Firmas/
        │   ├── 01_usuario_codigo.pdf
        │   └── 02_usuario_codigo.pdf
        └── Final/
            └── APTO_codigo.pdf
```

Una nueva revisión crea otra carpeta, por ejemplo `Rev 1`, sin eliminar el historial de `Rev 0`.

## Notificaciones

La aplicación programa una verificación local periódica mediante WorkManager:

- Recordatorio durante la franja de las 08:00.
- Recordatorio durante la franja de las 15:00.
- Después de 36 horas con una firma pendiente, recordatorio aproximadamente cada hora.

Estas notificaciones son locales y dependen del último estado sincronizado al conectar o actualizar la aplicación. Android puede retrasarlas por ahorro de batería; no equivalen a notificaciones push desde un servidor.

## Google Cloud

Habilitar:

- Google Drive API.
- Google Sheets API.

Configurar Google Auth Platform como aplicación interna de `skmindustrial.cl` y crear un cliente OAuth Android con:

```text
Package name: cl.skmindustrial.gestionplanos
SHA-1 validación v4: 7A:16:5A:7B:C3:C7:6F:C9:48:C5:F3:47:33:92:A5:34:88:C9:D4:00
```

La APK v4 y las siguientes APK debug de esta rama usan un certificado de validación estable. La SHA-1 anterior `E1:C0:BB:...` correspondía a la APK inicial y no autoriza esta nueva versión.

Scopes solicitados:

```text
https://www.googleapis.com/auth/drive
https://www.googleapis.com/auth/spreadsheets
```

No se necesita `google-services.json`.

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

## Validación pendiente

Aunque CI comprueba compilación y pruebas unitarias, antes de fusionar se debe validar físicamente:

- Conexión con una cuenta real `@skmindustrial.cl`.
- Creación de la estructura OT/Rev en Drive.
- Aplicación de sellos sobre PDF reales de varias hojas.
- Firma secuencial con dos o más teléfonos.
- Comportamiento de notificaciones bajo ahorro de batería.

Rama:

```text
agent/document-management-v2
```
