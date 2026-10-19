# PillTV (nombre de trabajo) — versión 0.1.0

## De qué trata
Reproductor de video para Android con listas IPTV (M3U). Es solo un reproductor:
cada usuario agrega sus propias listas. Interfaz moderna con cristal desenfocado (Haze)
y una píldora flotante de navegación.

## Qué incluye esta base
- Interfaz Haze: fondo con halos de color que se desenfocan detrás de las píldoras.
- Píldora de navegación flotante (Canales · Favoritos · Listas) que se expande en la pestaña activa.
- Píldora de mini reproductor: aparece sobre la navegación mientras suena un canal
  (pausa, cerrar, tocar para abrir el reproductor grande).
- Agregar listas M3U por dirección (http/https) o eligiendo un archivo del celular.
- Lectura de logos, grupos y nombres de canal; búsqueda y filtro por grupo.
- Favoritos guardados en el teléfono.
- Reproductor Media3 (HLS y TS): canal anterior/siguiente, pantalla completa horizontal,
  ventana flotante (imagen en imagen), reintentar si falla, controles que se ocultan solos.

## Cómo compilar (GitHub Actions)
1. Crea un repositorio nuevo y sube TODO el contenido de esta carpeta (incluida `.github`).
2. En la pestaña Actions corre "Build APK" (se ejecuta solo al subir a main).
3. Descarga el APK en Artifacts: `PillTV-debug-apk`.
No lleva gradle wrapper: el workflow usa Gradle 8.13 instalado por la acción.

## Siguiente (ideas)
- Elegir nombre definitivo y paquete (hoy: com.pilltv.app).
- Guardar la última lista/canal y caché sin conexión.
- Audio en segundo plano con notificación.
- Guía de programación (EPG), categorías automáticas, Cast.
- Backend (Firebase) para listas administradas por ti, premium con Google Play Billing, anuncios AdMob, bot de Telegram.
