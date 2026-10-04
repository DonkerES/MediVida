# Conectar MediVida a Firebase

La app Android está registrada en el proyecto Firebase `medivida-61ad9` y el archivo `app/google-services.json` está incluido. Para registrar usuarios y sincronizar datos aún debes habilitar Authentication y crear Firestore siguiendo los pasos siguientes. Si Firebase no está configurado o disponible, la app conserva su modo local con Room.

## 1. Crear el proyecto

1. El proyecto Firebase **MediVida** ya está creado y su ID es `medivida-61ad9`.
2. La aplicación Android ya está registrada con el identificador `com.example.gestionmedicamentos`.
3. `app/google-services.json` contiene la configuración de esa aplicación. Si cambias de proyecto Firebase o de identificador, descarga el archivo correspondiente y reemplázalo.
4. No necesitas una clave privada de cuenta de servicio dentro de Android. Nunca agregues credenciales de administrador a la app.

## 2. Activar cuentas

En **Build → Authentication → Sign-in method**, activa **Correo electrónico/contraseña**. No es necesario activar el enlace por correo.

En la configuración de Authentication establece una política de contraseña de al menos **12 caracteres**, en modo obligatorio, para que la exigencia también se aplique en el servidor. Activa la protección contra enumeración de correos si la consola la ofrece. Configura las plantillas de verificación y recuperación en español.

La app ofrece registro, inicio y cierre de sesión, cambio de contraseña con reautenticación, y recuperación por correo. Envía una verificación de correo al registrarse; en esta versión verificar el correo no es requisito para acceder a los registros propios. Firebase gestiona las contraseñas: no se guardan en documentos de Firestore.

## 3. Crear la base de datos y publicar las reglas

1. En **Build → Firestore Database**, crea la base de datos **(default)**. Elige una región adecuada antes de crearla.
2. Usa **modo producción**.
3. En la pestaña **Reglas**, reemplaza el contenido por el archivo `firestore.rules` de este proyecto y pulsa **Publicar**.
4. No uses reglas abiertas como `allow read, write: if true`.

Alternativa por terminal, desde el proyecto, con Node y Java instalados:

```powershell
npm ci
npx firebase login
npx firebase deploy --only firestore:rules --project TU_ID_REAL_DE_PROYECTO
```

Los datos se crean automáticamente al usar la app:

```text
users/{uid}                         perfil del paciente
users/{uid}/records/{recordId}      medicamentos, citas, comidas y restricciones
users/{uid}/completions/{eventId}   tomas y omisiones confirmadas
```

Las reglas comprueban `request.auth.uid == uid`, validan los campos permitidos, rechazan modificaciones entre cuentas, conservan el historial y evitan sobrescribir una toma ya registrada. Una persona con privilegios administrativos de tu proyecto sigue teniendo acceso administrativo: protege también tu cuenta de Google y los permisos del proyecto.

## 4. Abrir y ejecutar en Android Studio

1. Abre la carpeta **GestionMedicamentos**, que contiene `settings.gradle.kts`.
2. Usa **JDK 17**, Android SDK **35** y Build Tools **35.0.0**. Gradle se descarga mediante el wrapper incluido.
3. Sincroniza Gradle; el archivo `google-services.json` ya está incluido.
4. Ejecuta la app en un dispositivo con Android **8.0/API 26** o posterior.
5. En **Perfil**, permite las notificaciones y las alarmas exactas. Configura sonido/vibración desde el canal de notificaciones de Android.

Para generar un APK conectado a tu Firebase:

```powershell
.\gradlew.bat assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`. Este proyecto incluye `app/google-services.json` para `medivida-61ad9`. Después de sincronizar Gradle, la app podrá conectarse a ese proyecto una vez habilitados Authentication, Firestore y sus reglas. Para distribuir en producción necesitas una firma de publicación propia y una revisión de los requisitos de la tienda.

## 5. Comprobar dos celulares

1. Registra una cuenta A y agrega un medicamento con una hora unos minutos en el futuro.
2. Espera el indicador **Sincronizado**. En un segundo dispositivo inicia sesión con A y comprueba el medicamento.
3. Marca una toma desde un dispositivo y comprueba que aparece una sola vez en el historial del otro.
4. En el segundo dispositivo cierra sesión y crea una cuenta B: no debe mostrar los datos de A.
5. En A, activa modo avión, agrega un registro y cierra/reabre la app. Debe conservarse como pendiente. Recupera conexión y espera **Sincronizado**.
6. Cierra la app normalmente y verifica una alerta. Reinicia el teléfono y vuelve a comprobar una alerta futura.
7. En el mismo teléfono, registra la cuenta A y agrega un registro. En **Perfil → Cambiar cuenta**, inicia sesión o crea la cuenta B. B no debe ver datos de A. Al volver a iniciar sesión con A, sus datos deben aparecer de nuevo.

Cada cuenta se guarda bajo `users/{uid}`; la copia de Room también se separa por ese UID. Solo una cuenta queda activa a la vez en la app: **Cambiar cuenta** cierra la sesión actual y vuelve a la pantalla de acceso. Las alarmas de la cuenta que sale se cancelan; al entrar con otra, se programan las alarmas de esa cuenta. Espera a que el estado indique **Sincronizado** antes de cambiar de cuenta.

Las reglas actuales permiten los campos de inventario y alimentos del modelo MediVida y solo dejan leer o escribir bajo el UID autenticado. Puedes probarlas localmente con `npm ci` y `npm run test:rules` antes de publicarlas.

La creación de cuentas, el nuevo inicio de sesión y la recuperación de contraseña necesitan Internet. La caché mantiene la sesión y los registros consultados; las escrituras sin conexión se sincronizan después. Espera confirmación del servidor antes de cerrar sesión o desinstalar. Los cambios remotos se reciben mientras la app mantiene conexión; si Android ha cerrado su proceso, abre la app en ese teléfono para actualizar su programación local.

Fuentes: [configurar Firebase para Android](https://firebase.google.com/docs/android/setup), [persistencia sin conexión](https://firebase.google.com/docs/firestore/manage-data/enable-offline), [reglas de acceso](https://firebase.google.com/docs/firestore/security/get-started), [alarmas de Android](https://developer.android.com/develop/background-work/services/alarms).
