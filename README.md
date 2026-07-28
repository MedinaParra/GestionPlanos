# Gestión de Planos SKM — versión directa con Google Drive

Aplicación Android para cargar, visualizar, renombrar y controlar planos PDF usando directamente una cuenta de Google Drive. La aplicación no necesita Firebase, servidor, base de datos, Cloud Functions ni usuarios internos.

## Funcionamiento simplificado

1. El usuario conecta su propia cuenta Google y autoriza Drive y Sheets.
2. La aplicación crea en `Mi unidad` una carpeta privada llamada `GestionPlanosSKM-Privado`.
3. Dentro de esa carpeta guarda `claves-configuracion.json`, que contiene solamente IDs y configuración técnica. No almacena contraseñas, PIN ni datos biométricos.
4. El usuario pega el enlace de una carpeta normal de Drive que utilizará para los planos.
5. Los permisos de esa carpeta se administran directamente desde Google Drive:
   - **Lector:** puede abrir PDF y ver el control documental.
   - **Editor:** puede cargar PDF, cambiar revisiones y firmar.
6. La aplicación crea dentro de la carpeta compartida:
   - Los PDF administrados.
   - `control-documental.json` con el índice y los estados.
   - Una planilla `Control de Documentos SKM`.
7. Para marcar o quitar una firma, Android exige huella, rostro o PIN/patrón/clave del teléfono.

## Capacidades implementadas

- Acceso directo de lectura y escritura a Google Drive.
- Compatible con una carpeta normal o compartida de Drive.
- Detección automática de permiso de lectura o escritura mediante las capacidades de Drive.
- Carga de PDF de hasta 40 MB.
- Visor PDF dentro de la aplicación.
- Cambio de revisión con renombrado real del PDF en Drive.
- Índice documental JSON compartido.
- Planilla Google Sheets sincronizada.
- Firma mediante la ventana oficial de seguridad de Android.
- Registro de firmante, correo, fecha y método de confirmación.
- Acceso mediante cualquier cuenta Google autorizada, sin depender de Firebase.

## Archivos creados en Drive

### Carpeta privada del usuario

```text
Mi unidad/
└── GestionPlanosSKM-Privado/
    └── claves-configuracion.json
```

`claves-configuracion.json` guarda:

- ID de la carpeta compartida.
- ID de la planilla.
- ID del índice documental.
- Nombre visible de la carpeta.
- Última actualización.

No contiene contraseñas ni tokens OAuth permanentes.

### Carpeta compartida de planos

```text
Carpeta elegida por el usuario/
├── Control de Documentos SKM
├── control-documental.json
├── CODIGO_REV-A_archivo.pdf
└── otros planos PDF
```

## Configuración de Google Cloud

Solo se necesita un proyecto Google Cloud.

### 1. Crear el proyecto

Crea o selecciona un proyecto en Google Cloud Console, por ejemplo:

```text
Gestion Planos SKM
```

### 2. Habilitar APIs

En **APIs y servicios > Biblioteca**, habilita:

- Google Drive API.
- Google Sheets API.

### 3. Configurar consentimiento OAuth

En **Google Auth Platform** configura:

- Nombre de aplicación: `Gestión de Planos SKM`.
- Correo de soporte.
- Audiencia interna si el proyecto pertenece al Google Workspace de SKM; de lo contrario usa audiencia externa para pruebas.
- Agrega como usuarios de prueba las cuentas que instalarán la APK cuando la aplicación siga en modo de prueba.

La aplicación solicita estos scopes:

```text
https://www.googleapis.com/auth/drive
https://www.googleapis.com/auth/spreadsheets
```

El scope de Drive permite leer, crear, renombrar y modificar archivos de la cuenta que concede el permiso.

### 4. Crear cliente OAuth Android

Crea una credencial OAuth de tipo **Android** con:

```text
Package name: cl.skmindustrial.gestionplanos
```

Agrega la huella SHA-1 del certificado con el que se firma la APK.

Para obtener la huella debug en Windows:

```powershell
keytool -list -v `
  -alias androiddebugkey `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -storepass android `
  -keypass android
```

No se necesita `google-services.json`.

## Compilación

Requisitos:

- JDK 17.
- Android SDK 36.
- Gradle 9.3.1 o Android Studio.

```bash
gradle --no-daemon testDebugUnitTest
gradle --no-daemon assembleDebug
```

APK generado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Primer uso

1. Instala la APK.
2. Presiona **Conectar mi Google Drive**.
3. Selecciona la cuenta y acepta Drive y Sheets.
4. La app creará `GestionPlanosSKM-Privado` en tu Drive.
5. Crea manualmente la carpeta de planos en Drive.
6. Desde Drive, comparte esa carpeta con las personas necesarias como lectoras o editoras.
7. En la aplicación, presiona **Elegir carpeta** y pega el enlace.
8. Agrega el primer PDF.
9. Para firmar, pulsa **Firmar con huella o PIN** y confirma en la ventana de Android.

## Firma con huella o PIN

La aplicación no lee ni guarda la huella, el rostro, el PIN, el patrón o la contraseña del teléfono. Android realiza la validación y devuelve solamente un resultado de éxito o cancelación.

Después de una validación correcta se registra:

- Nombre de la cuenta Google.
- Correo de la cuenta.
- Fecha y hora.
- Método general utilizado: biometría o credencial del teléfono.
- Estado `FIRMADO`.

Esta función corresponde a una aprobación o firma interna de flujo documental. No inserta un certificado digital en el PDF y no equivale por sí sola a una firma electrónica avanzada.

## Seguridad práctica

- No se guardan contraseñas de usuarios en Drive.
- Los permisos dependen de las reglas normales de Google Drive.
- Una persona con permiso de lector no puede modificar la carpeta mediante la aplicación.
- Una persona con permiso de editor sí puede actualizar el índice, la planilla y los PDF.
- La carpeta privada no debe compartirse.
- La carpeta de planos sí se comparte desde Drive según las necesidades del equipo.

## Rama de desarrollo

```text
agent/document-management-v2
```

El PR permanece en borrador hasta validar el flujo con una cuenta Google real y una carpeta compartida real.
