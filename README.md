# SKM Industrial Gestión de Planos

Aplicación Android para administrar planos PDF, revisiones, firmas, observaciones y aprobación para fabricación usando Google Drive.

## Interfaz corporativa v7

La navegación fue reorganizada sin modificar el flujo documental ni la persistencia existente:

- Paleta corporativa naranja, blanco y grafito.
- Menú lateral con Inicio, Planos, Mis revisiones, Usuarios y firmantes, Notificaciones, Mi perfil, Configuración y Ayuda.
- Cerrar sesión aislado al final del menú lateral.
- Sincronización ubicada en la barra superior, lejos de cerrar sesión.
- Panel de inicio con indicadores y accesos rápidos.
- Configuración de Drive y plazos centralizada en una sola pantalla.
- Perfil y administración de usuarios como pantallas completas, no como formularios comprimidos.

## Adaptación a pantallas

La versión 7 usa layouts adaptativos de Jetpack Compose:

- Respeta barras de estado, gestos y navegación del teléfono mediante `WindowInsets.safeDrawing`.
- Respeta la aparición del teclado mediante `imePadding`.
- Formularios y diálogos largos tienen desplazamiento vertical real.
- Acciones principales quedan en barras inferiores fijas y accesibles.
- Botones se apilan verticalmente en pantallas estrechas.
- Tarjetas de indicadores cambian de cuatro columnas a una cuadrícula de dos por dos.
- Herramientas del visor usan desplazamiento horizontal para evitar cortes.
- El contenido principal limita su ancho en pantallas grandes sin romper teléfonos pequeños.
- La interfaz evita depender de una resolución fija o una relación de aspecto específica.

## Visor PDF

- Pantalla completa.
- Zoom entre 100 % y 600 % mediante gesto de pinza o botones.
- Desplazamiento con dos dedos.
- Navegación de páginas fija.
- Lista de observaciones e historial accesibles desde la barra superior.
- Botón visible **Nueva observación**.
- Después de tocar el plano se abre un editor de texto de pantalla completa.
- El editor mantiene Guardar, Publicar y Cancelar visibles sobre el teclado.
- Borradores privados y observaciones publicadas conservan el comportamiento de la versión 6.
- Aprobar, firmar y solicitar cambios permanecen en la barra inferior.

## Flujo documental

1. El administrador carga un PDF con OT, código y revisión.
2. La app crea `OT XXX / Rev N`, conserva el original y genera la copia roja `NO APTO PARA FABRICACIÓN`.
3. El documento queda `EN_REVISIÓN` y se asigna secuencialmente a los firmantes obligatorios.
4. Cada revisor puede crear observaciones como borradores privados y publicarlas cuando estén listas.
5. El revisor decide entre **Aprobar y firmar** o **Solicitar cambios**.
6. Al aprobar, se agrega el timbre y la firma en todas las hojas y el turno pasa al siguiente revisor.
7. Al solicitar cambios, el flujo se detiene y el administrador debe cargar una revisión nueva.
8. Cuando todos aprueban, se genera el PDF azul `APTO PARA FABRICACIÓN`.

## Estructura de Drive

```text
Carpeta principal/
├── GestionPlanos-Sistema/
│   ├── usuarios.json
│   ├── configuracion-flujo.json
│   ├── comentarios-publicados.json
│   └── historial-flujo.json
├── control-documental.json
├── Control de Documentos SKM
└── OT 1234/
    └── Rev 0/
        ├── Original/
        ├── Revision/
        ├── Firmas/
        └── Final/
```

Los borradores privados se guardan dentro de `GestionPlanosSKM-Privado/comentarios-borradores.json` en el Drive personal del autor.

## Google Cloud

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
Version code: 7
Version name: 7.0.0
Rama: agent/document-management-v2
```

El PR permanece en borrador mientras se completa la validación física en teléfonos de distintas dimensiones, con tamaño de fuente aumentado, teclado abierto y navegación por gestos o botones.
