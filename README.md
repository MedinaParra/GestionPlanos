# Gestión de Planos SKM

Aplicación Android para centralizar el control documental de planos de SKM Industrial. Sustituye el seguimiento manual de PDF, revisiones y firmas por un flujo compartido con Google Drive, Google Sheets, Firebase Authentication y control de roles.

## Alcance implementado

- Inicio de sesión Google restringido al dominio `@skmindustrial.cl`.
- El primer usuario corporativo que completa el alta queda como `ADMIN` mediante una transacción del servidor.
- Los siguientes usuarios corporativos quedan como `EDITOR`.
- El administrador puede crear usuarios con nombre de usuario y contraseña en rol `VIEWER`.
- Los visualizadores pueden consultar el registro y abrir PDF, pero no modificar documentos, Drive, revisiones ni firmas.
- Carpeta de Google Drive configurable únicamente por el administrador.
- Carga de PDF a la carpeta oficial de Drive.
- Creación automática de una planilla Google Sheets llamada `Control de Documentos SKM`.
- Planilla sincronizada con código, archivo, revisión, firmado, estado, responsable, fecha, enlace y última actualización.
- Visor PDF integrado en la aplicación.
- Registro de auditoría de cargas, revisiones y cambios de firma.
- Copia protegida de previsualización en Firebase Storage para accesos de solo lectura.

## Roles

| Rol | Acceso |
|---|---|
| `ADMIN` | Configura Drive, crea visualizadores, carga y modifica documentos. |
| `EDITOR` | Carga PDF, cambia revisión y estado de firma. |
| `VIEWER` | Solo consulta el control documental y visualiza PDF. |

La asignación del primer administrador se realiza en Cloud Functions. No depende de datos locales ni del orden de instalación del APK.

## Arquitectura

- **Android / Kotlin / Jetpack Compose:** interfaz y visor PDF.
- **Firebase Authentication:** Google corporativo y cuentas de visualización.
- **Cloud Functions:** asignación segura de roles y creación de visualizadores.
- **Cloud Firestore:** configuración, metadatos documentales y auditoría.
- **Google Drive:** repositorio oficial de PDF.
- **Google Sheets:** planilla de control compartida.
- **Firebase Storage:** copia protegida para visualizadores sin permisos de Drive.

## Configuración inicial

### 1. Crear o seleccionar el proyecto Firebase

1. Registrar una aplicación Android con el package name:

   `cl.skmindustrial.gestionplanos`

2. Descargar `google-services.json` y copiarlo en:

   `app/google-services.json`

3. En Authentication habilitar:
   - Google.
   - Correo y contraseña.

4. Crear Firestore y Storage.

### 2. Configurar Google Cloud / Workspace

En el mismo proyecto Google Cloud:

1. Habilitar **Google Drive API** y **Google Sheets API**.
2. Configurar la pantalla de consentimiento OAuth como aplicación **interna** de Google Workspace cuando el dominio `skmindustrial.cl` esté administrado en Workspace.
3. Crear las credenciales OAuth Android correspondientes al package y a las huellas SHA-1/SHA-256 del certificado de desarrollo y producción.
4. Confirmar que Firebase haya creado el cliente OAuth web usado por `default_web_client_id`.

La aplicación solicita los alcances de Drive y Sheets durante la sesión del editor. El selector de cuenta y la autorización se restringen a `skmindustrial.cl`.

### 3. Desplegar backend y reglas

Instalar Firebase CLI, iniciar sesión y asociar el proyecto:

```bash
firebase login
firebase use --add
```

Instalar y compilar las funciones:

```bash
cd functions
npm install
npm run build
cd ..
```

Desplegar:

```bash
firebase deploy --only functions,firestore:rules,storage
```

### 4. Compilar Android

El repositorio original no incluía `gradlew` ni `gradle-wrapper.jar`. Hasta incorporar el wrapper binario, se debe usar Gradle 9.3.1 instalado localmente, la tarea Gradle de Android Studio o el workflow de GitHub Actions incluido en esta rama.

```bash
gradle --version
gradle --no-daemon testDebugUnitTest
gradle --no-daemon assembleDebug
```

El APK de depuración se genera en:

`app/build/outputs/apk/debug/app-debug.apk`

## Primer uso

1. Iniciar la aplicación con la cuenta corporativa que será administradora.
2. Presionar **Conectar Google Drive** y autorizar Drive/Sheets.
3. Abrir **Administrar**.
4. Pegar el enlace o ID de la carpeta oficial de Drive.
5. Guardar la configuración. La aplicación creará la planilla de control dentro de esa carpeta.
6. Agregar el primer PDF indicando su código y revisión.
7. Crear usuarios de visualización cuando sea necesario.

## Consideraciones de seguridad

- No subir `google-services.json`, keystores ni contraseñas al repositorio.
- Utilizar una carpeta o Unidad Compartida administrada por SKM Industrial.
- Las reglas impiden que `VIEWER` modifique Firestore o Storage.
- La creación de usuarios y la asignación de roles se ejecutan exclusivamente con Firebase Admin SDK en Cloud Functions.
- El estado `FIRMADO` representa control de flujo documental. No equivale por sí solo a una firma electrónica avanzada regulada ni incrusta una firma criptográfica dentro del PDF.

## Rama de implementación

La reconstrucción funcional se desarrolla en:

`agent/document-management-v2`

La interfaz heredada permanece en el código por compatibilidad temporal, pero ya no es accesible desde `MainActivity`. Puede eliminarse después de validar el nuevo flujo en dispositivos corporativos.
