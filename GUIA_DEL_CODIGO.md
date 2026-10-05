# Guía del código de MediVida

Esta guía sirve para ubicar las piezas de la app y relacionarlas con los temas vistos en clase. La reorganización de archivos no cambia las pantallas ni las funciones.

## El recorrido de los datos

```text
Pantalla Compose
    -> HealthViewModel (acciones y estado visible)
        -> HealthRepository (operaciones comunes)
            -> DemoStore (modo de práctica, guardado local con Room)
            -> LocalStore (cuenta Firebase y copia local con Room)
```

Las alarmas tienen un recorrido separado porque Android debe poder activarlas aunque MediVida no esté abierta:

```text
Reminders programa una alarma
    -> ReminderReceiver recibe el aviso
        -> AlarmActivity muestra la alarma
            -> la persona confirma o pospone
                -> HealthRepository guarda la toma
```

## Pantallas y navegación

- `MainActivity.kt`: inicia la app, recoge el estado del ViewModel y conecta las pantallas con `NavHost`.
- `AuthUi.kt`: inicio de sesión, creación de cuenta y recuperación de contraseña.
- `HomeUi.kt`: inicio, adherencia del día, cita próxima, avisos de stock y tareas del día.
- `RecordsUi.kt`: pauta diaria, dosis e historial de medicamentos.
- `RecordCatalogUi.kt`: tarjetas de tratamientos, citas, plan de comidas y categorías de alimentos.
- `RecordEditorUi.kt`: formulario para agregar y editar tratamientos, citas, comidas y categorías.
- `ProfileUi.kt` y `AboutUi.kt`: perfil y descripción/créditos.
- `CommonUi.kt` y `Design.kt`: componentes y estilos compartidos. Si aquí se cambia un color o una tarjeta, puede verse afectada más de una pantalla.

La navegación usa nombres de ruta de texto en `MainActivity.kt` (`"Inicio"`, `"Medicación"`, `"Citas"`, etc.). `NavHost` declara qué pantalla corresponde a cada ruta; `navigate` cambia a otra. El texto visible de las pestañas también se usa como ruta, así que hay que mantener esos nombres iguales.

## Estado de Compose y ViewModel

- `remember` guarda un valor mientras la pantalla está activa; se usa, por ejemplo, para abrir un diálogo.
- `rememberSaveable` también puede restaurar valores sencillos después de recrear una pantalla, como el filtro seleccionado.
- `HealthViewModel.kt` contiene `AppState`, el estado común que observan las pantallas: usuario, datos, carga, error y estado de sincronización.
- `collectAsStateWithLifecycle` conecta ese estado con Compose para que la interfaz se redibuje cuando cambie.
- `viewModelScope` ejecuta operaciones ligadas al ciclo de vida del ViewModel. `Dispatchers.IO` evita hacer lecturas y escrituras en el hilo de la interfaz.

En `HealthViewModel.performOperation` se agrupan las operaciones: activa el indicador de carga, ejecuta el trabajo, vuelve a leer los datos y actualiza `AppState`. También convierte fallos técnicos en mensajes para la interfaz. Los métodos como `save`, `login` y `complete` usan ese recorrido común.

## Guardado local y Firebase

- `Models.kt`: datos principales (`HealthRecord`, `Profile`, `Completion`) y reglas de horarios (`Schedule`). Las reglas se comparten entre interfaz, guardado y alarmas.
- `HealthRepository.kt`: define las operaciones que necesita la app sin decidir dónde se guardan.
- `DemoStore.kt`: implementación de práctica. Usa el propietario local `demo-local`, separado de las cuentas.
- `LocalStore.kt`: implementación con Firebase Authentication y Firestore. Escucha cambios y conserva datos en Room para mostrarlos sin conexión.
- `LocalDatabase.kt`: base Room y consultas DAO. Por ahora algunas entidades guardan el objeto como texto JSON (`payload`), que luego se convierte a los modelos de la app.

No cambiar el formato de las tablas Room sin preparar una migración. El dispositivo puede tener datos guardados con el formato actual. Tampoco se debe quitar el identificador `owner`: separa los datos de prueba de los datos de cada cuenta.

Retrofit no forma parte de este proyecto. Firebase se comunica mediante sus SDK oficiales; agregar Retrofit sin un servicio REST propio no ayudaría a entender ni simplificar esta app.

## Alarmas

- `Reminders.kt`: programa, reconstruye y cancela alarmas del sistema, crea notificaciones y valida las acciones recibidas.
- `ReminderReceiver`: es el componente Android que recibe una alarma aunque la pantalla principal no esté abierta.
- `AlarmActivity.kt`: pantalla independiente de alarma. Oculta detalles mientras el teléfono está bloqueado y pide confirmación antes de guardar una toma.
- `AndroidManifest.xml`: declara el receptor, las actividades y permisos necesarios.

`AlarmManager`, `PendingIntent`, `BroadcastReceiver`, canales de notificación y permiso de alarma exacta son APIs de Android que no se reemplazan por un temporizador de Compose: el temporizador dejaría de funcionar de forma fiable al cerrar la app o reiniciar el teléfono.

## Palabras de Kotlin que pueden resultar nuevas

- `data class`: clase sencilla para guardar datos; Kotlin genera comparaciones y otras funciones habituales.
- `interface`: lista de acciones que ofrece un componente. Aquí permite que el ViewModel use el mismo repositorio con Firebase o con el modo local.
- `Flow` / `StateFlow`: canal de valores que puede cambiar con el tiempo. El ViewModel publica el estado y las tiendas avisan cuando cambian los datos.
- `suspend`, corrutinas y `Dispatchers.IO`: permiten esperar operaciones lentas sin congelar la pantalla.
- Lambdas como `{ ... }`: acciones que una pantalla pasa al ViewModel o a un botón.
- `PendingIntent`: permiso diferido que Android conserva para ejecutar una acción más tarde.

Se pueden aprender por partes; no hace falta reescribirlos todos para poder mantener la app.

## Verificación

Las reglas de calendario y adherencia tienen pruebas en `app/src/test/java/com/example/gestionmedicamentos/ScheduleTest.kt`. En Android Studio se pueden ejecutar con **Run tests** o con `gradlew testDebugUnitTest`. Antes de una entrega también hay que compilar y probar manualmente navegación, modo local, cuenta Firebase, historial y alarmas.
