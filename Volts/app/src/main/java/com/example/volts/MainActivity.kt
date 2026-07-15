package com.example.volts

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volts.ui.DogViewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.volts.data.DogEntity
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.ImageLoader
import coil3.gif.GifDecoder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.statusBarsPadding
import kotlinx.coroutines.delay
import android.media.SoundPool
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

enum class ActionMenu {
    FOOD,
    PLAY
}

enum class DragToy {
    BALL,
    STICK
}

enum class DragFood {
    COOKIE,
    BONE,
    CHOCOLATE
}

data class EducationalMessage(
    val title: String,
    val message: String,
    val buttonText: String = "Entendido",

    val hungerReward: Int = 0,
    val happinessReward: Int = 0,
    val energyReward: Int = 0,
    val healthReward: Int = 0
)

val environmentalMessages = listOf(

    EducationalMessage(
        title = "¡VOLTS necesita tu ayuda!",
        message = """
            VOLTS dejó residuos durante su paseo.

            Recoger los desechos de las mascotas mantiene limpios los espacios públicos y evita la contaminación del suelo y del agua.
        """.trimIndent(),
        buttonText = "Recoger responsablemente",
        energyReward = 5,
        healthReward = 3
    ),

    EducationalMessage(
        title = "Cuidemos el agua",
        message = """
            Al limpiar el área de VOLTS, utiliza solamente el agua necesaria.

            Cerrar la llave cuando no se utiliza ayuda a evitar el desperdicio de este recurso tan importante.
        """.trimIndent(),
        buttonText = "Cuidar el agua",
        healthReward = 3
    ),

    EducationalMessage(
        title = "Separemos los residuos",
        message = """
            Deposita los residuos en el contenedor adecuado.

            Separar correctamente la basura facilita su tratamiento, favorece el reciclaje y ayuda a proteger el ambiente.
        """.trimIndent(),
        buttonText = "Separar correctamente",
        happinessReward = 5
    ),

    EducationalMessage(
        title = "Protejamos las áreas verdes",
        message = """
            Durante el paseo de VOLTS, evita dañar plantas, árboles y jardines.

            Las áreas verdes producen oxígeno y sirven como hogar para muchas especies.
        """.trimIndent(),
        buttonText = "Cuidar las áreas verdes",
        healthReward = 5
    ),

    EducationalMessage(
        title = "Una comunidad más limpia",
        message = """
            Si encuentras basura durante el paseo, colócala en un contenedor.

            Una pequeña acción puede ayudar a mantener limpio el entorno de toda la comunidad.
        """.trimIndent(),
        buttonText = "Recoger la basura",
        happinessReward = 5,
        energyReward = 3
    )
)

val feedingMessages = listOf(
    EducationalMessage(
        title = "Alimentación responsable",
        message = """
            Las mascotas necesitan alimentos apropiados para su especie, edad y tamaño.

            Darles comida adecuada y respetar sus horarios ayuda a mantenerlas saludables.
        """.trimIndent()
    ),

    EducationalMessage(
        title = "No desperdicies alimento",
        message = """
            Sirve únicamente la cantidad de alimento que la mascota necesita.

            Evitar el desperdicio también ayuda a aprovechar mejor los recursos del planeta.
        """.trimIndent()
    ),

    EducationalMessage(
        title = "Agua limpia para las mascotas",
        message = """
            Además de una buena alimentación, las mascotas siempre deben tener acceso a agua limpia y fresca.

            Cambiar el agua regularmente ayuda a proteger su salud.
        """.trimIndent()
    )
)

val playMessages = listOf(
    EducationalMessage(
        title = "El juego también es salud",
        message = """
            Jugar y realizar actividad física ayuda a que las mascotas mantengan un peso saludable y reduzcan el estrés.

            También fortalece la relación entre las personas y los animales.
        """.trimIndent()
    ),

    EducationalMessage(
        title = "Juega en espacios seguros",
        message = """
            Antes de jugar con una mascota, revisa que el lugar esté libre de basura, objetos peligrosos o plantas que puedan dañarla.
        """.trimIndent()
    ),

    EducationalMessage(
        title = "Reutiliza para jugar",
        message = """
            Algunos juguetes para mascotas pueden elaborarse reutilizando materiales limpios y seguros.

            Reutilizar reduce residuos, pero siempre debes asegurarte de que el objeto no pueda lastimar al animal.
        """.trimIndent()
    )
)

val petMessages = listOf(
    EducationalMessage(
        title = "Trata a los animales con respeto",
        message = """
            Las mascotas deben recibir cariño sin ser lastimadas ni incomodadas.

            Antes de acariciar un animal desconocido, pregunta a su responsable y acércate con calma.
        """.trimIndent()
    ),

    EducationalMessage(
        title = "Aprende a observar a tu mascota",
        message = """
            Los animales expresan miedo, cansancio o incomodidad mediante su comportamiento.

            Respetar su espacio también forma parte de una convivencia responsable.
        """.trimIndent()
    )
)

val restMessages = listOf(
    EducationalMessage(
        title = "VOLTS necesita descansar",
        message = """
            Las mascotas necesitan un lugar limpio, tranquilo y seguro para dormir.

            Un descanso adecuado les permite recuperar energía y conservar una buena salud.
        """.trimIndent()
    ),

    EducationalMessage(
        title = "Respeta su descanso",
        message = """
            No debes despertar o molestar constantemente a una mascota mientras duerme.

            El descanso es una necesidad importante para su bienestar.
        """.trimIndent()
    )
)

val movementMessages = listOf(
    EducationalMessage(
        title = "Paseo responsable",
        message = """
            Durante un paseo, mantén a tu mascota bajo supervisión y respeta los espacios compartidos con otras personas y animales.
        """.trimIndent()
    ),

    EducationalMessage(
        title = "Lleva lo necesario",
        message = """
            Para pasear responsablemente lleva agua, bolsas para recoger residuos y los elementos necesarios para mantener segura a tu mascota.
        """.trimIndent()
    ),

    EducationalMessage(
        title = "Caminar ayuda al planeta",
        message = """
            Caminar distancias cortas, cuando sea posible, reduce el uso de vehículos y las emisiones contaminantes.

            Además, permite que VOLTS realice actividad física.
        """.trimIndent()
    )
)

val chocolateMessage = EducationalMessage(
    title = "¡Cuidado con el chocolate!",
    message = """
        El chocolate contiene sustancias que pueden ser tóxicas para los perros y afectar seriamente su salud.

        Nunca debes darle chocolate a una mascota. Si un perro lo consume accidentalmente, avisa inmediatamente a una persona adulta o consulta a un veterinario.
    """.trimIndent(),
    buttonText = "Entendido"
)

fun toyImage(toy: DragToy): Int {
    return when (toy) {
        DragToy.BALL -> R.drawable.toy_ball
        DragToy.STICK -> R.drawable.toy_stick
    }
}

fun foodImage(food: DragFood): Int {
    return when (food) {
        DragFood.COOKIE -> R.drawable.food_cookie
        DragFood.BONE -> R.drawable.food_bone
        DragFood.CHOCOLATE -> R.drawable.food_chocolate
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: DogViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        setTheme(R.style.Theme_Volts)
        super.onCreate(savedInstanceState)

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        )

        setContent {
            MaterialTheme {
                VoltsApp(viewModel)
            }
        }
    }
}

@Composable
fun VoltsApp(viewModel: DogViewModel) {
    val dog by viewModel.dog.collectAsState()
    val message by viewModel.message.collectAsState()

    if (dog == null) {
        CreateDogScreen(
            message = message,
            onCreateDog = { name -> viewModel.createDog(name) }
        )
    } else {
        DogHomeScreen(
            dog = dog!!,
            message = message,
            onConnectBluetooth = { viewModel.connectBluetooth() },

            onCookie = { viewModel.feedCookie() },
            onBone = { viewModel.feedBone() },
            onChocolate = { viewModel.feedChocolate() },

            onBall = { viewModel.playBall() },
            onStick = { viewModel.playStick() },

            onPet = { viewModel.petDog() },
            onRest = { viewModel.toggleSleep() },

            onMoveForward = { viewModel.moveForward() },
            onMoveBack = { viewModel.moveBack() },
            onMoveLeft = { viewModel.moveLeft() },
            onMoveRight = { viewModel.moveRight() },
            onStop = { viewModel.stop() },

            onEducationalReward = { hunger, happiness, energy, health ->
                viewModel.applyEducationalReward(
                    hunger = hunger,
                    happiness = happiness,
                    energy = energy,
                    health = health
                )
            }
        )
    }
}

@Composable
fun CreateDogScreen(
    message: String,
    onCreateDog: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.background_day),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_volts),
                contentDescription = "Logo VOLTS",
                modifier = Modifier.size(180.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "VOLTS",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                fontSize = 14.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Crea a tu perro",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre de VOLTS") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (name.trim().isNotEmpty()) {
                                onCreateDog(name.trim())
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Crear perro")
                    }
                }
            }
        }
    }
}

@Composable
fun DogHomeScreen(
    dog: DogEntity,
    message: String,
    onConnectBluetooth: () -> Unit,

    onCookie: () -> Unit,
    onBone: () -> Unit,
    onChocolate: () -> Unit,

    onBall: () -> Unit,
    onStick: () -> Unit,

    onPet: () -> Unit,
    onRest: () -> Unit,

    onMoveForward: () -> Unit,
    onMoveBack: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onStop: () -> Unit,

    onEducationalReward: (
        hunger: Int,
        happiness: Int,
        energy: Int,
        health: Int
    ) -> Unit
) {
    var showBluetoothModal by remember { mutableStateOf(false) }

    var educationalMessage by remember { mutableStateOf<EducationalMessage?>(null) }

    var feedingActionCount by remember { mutableIntStateOf(0) }
    var playActionCount by remember { mutableIntStateOf(0) }
    var petActionCount by remember { mutableIntStateOf(0) }
    var restActionCount by remember { mutableIntStateOf(0) }
    var movementActionCount by remember { mutableIntStateOf(0) }

    var temporaryDogAnimation by remember { mutableStateOf<Int?>(null) }

    var temporaryAnimationDurationMs by remember { mutableStateOf(3000L) }

    var dogDropArea by remember { mutableStateOf<Rect?>(null) }

    var mouthDropArea by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)

            if (educationalMessage == null && !showBluetoothModal) {
                educationalMessage = environmentalMessages.random()
            }
        }
    }

    val context = LocalContext.current

    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(2)
            .build()
    }

    val barkSoundId = remember {
        soundPool.load(context, R.raw.dog_bark, 1)
    }

    DisposableEffect(Unit) {
        onDispose {
            soundPool.release()
        }
    }

    fun playBark() {
        soundPool.play(
            barkSoundId,
            1f,
            1f,
            1,
            0,
            1f
        )
    }

    fun registerFeedingAction() {
        feedingActionCount++

        if (feedingActionCount >= 3) {
            feedingActionCount = 0

            if (educationalMessage == null) {
                educationalMessage = feedingMessages.random()
            }
        }
    }

    fun registerPlayAction() {
        playActionCount++

        if (playActionCount >= 3) {
            playActionCount = 0

            if (educationalMessage == null) {
                educationalMessage = playMessages.random()
            }
        }
    }

    fun registerPetAction() {
        petActionCount++

        if (petActionCount >= 3) {
            petActionCount = 0

            if (educationalMessage == null) {
                educationalMessage = petMessages.random()
            }
        }
    }

    fun registerRestAction() {
        restActionCount++

        if (restActionCount >= 3) {
            restActionCount = 0

            if (educationalMessage == null) {
                educationalMessage = restMessages.random()
            }
        }
    }

    fun registerMovementAction() {
        movementActionCount++

        if (movementActionCount >= 3) {
            movementActionCount = 0

            if (educationalMessage == null) {
                educationalMessage = movementMessages.random()
            }
        }
    }

    fun playTemporaryAnimation(animationRes: Int, durationMs: Long, action: () -> Unit) {
        action()
        temporaryDogAnimation = animationRes
    }

    fun handleToyDrop(toy: DragToy) {
        temporaryAnimationDurationMs = 7000L

        playTemporaryAnimation(R.drawable.dog_play, temporaryAnimationDurationMs) {
            when (toy) {
                DragToy.BALL -> onBall()
                DragToy.STICK -> onStick()
            }
        }

        kotlinx.coroutines.GlobalScope.launch {
            delay(6208)
            playBark()
        }
    }

    fun handleFoodDrop(food: DragFood) {
        temporaryAnimationDurationMs = 3800L

        playTemporaryAnimation(R.drawable.dog_eat, temporaryAnimationDurationMs) {
            when (food) {
                DragFood.COOKIE -> onCookie()
                DragFood.BONE -> onBone()
                DragFood.CHOCOLATE -> onChocolate()
            }
        }
    }

    LaunchedEffect(temporaryDogAnimation) {
        if (temporaryDogAnimation != null) {
            delay(temporaryAnimationDurationMs)
            temporaryDogAnimation = null
        }
    }

    val baseDogAnimation = when {
        dog.sleeping -> R.drawable.dog_sleep
        dog.health < 50 || dog.hunger < 50 -> R.drawable.dog_sick
        dog.happiness < 50 || dog.energy < 50 -> R.drawable.dog_sad
        else -> R.drawable.dog_idle
    }

    val currentDogAnimation = temporaryDogAnimation ?: baseDogAnimation

    val currentBackground = if (dog.sleeping) {
        R.drawable.background_night
    } else {
        R.drawable.background_day
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = currentBackground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusBar(dog)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(170.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                AnimatedDogImage(
                    resId = currentDogAnimation,
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .aspectRatio(1f)
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { coordinates ->
                            val dogBounds = coordinates.boundsInRoot()
                            dogDropArea = dogBounds

                            mouthDropArea = Rect(
                                left = dogBounds.left + dogBounds.width * 0.38f,
                                top = dogBounds.top + dogBounds.height * 0.50f,
                                right = dogBounds.left + dogBounds.width * 0.62f,
                                bottom = dogBounds.top + dogBounds.height * 0.70f
                            )
                        }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            MainActionButtons(

                dogDropArea = dogDropArea,
                mouthDropArea = mouthDropArea,

                onFoodDragStart = {
                    temporaryDogAnimation = R.drawable.dog_mouth_open
                },
                onFoodDragCancel = {
                    temporaryDogAnimation = null
                },
                onCookie = {
                    temporaryAnimationDurationMs = 3800L

                    playTemporaryAnimation(
                        animationRes = R.drawable.dog_eat,
                        durationMs = temporaryAnimationDurationMs
                    ) {
                        onCookie()
                        registerFeedingAction()
                    }
                },
                onBone = {
                    temporaryAnimationDurationMs = 3800L

                    playTemporaryAnimation(
                        animationRes = R.drawable.dog_eat,
                        durationMs = temporaryAnimationDurationMs
                    ) {
                        onBone()
                        registerFeedingAction()
                    }
                },
                onChocolate = {
                    onChocolate()

                    temporaryAnimationDurationMs = 3800L
                    temporaryDogAnimation = R.drawable.dog_eat

                    educationalMessage = chocolateMessage
                },

                onBall = {
                    handleToyDrop(DragToy.BALL)
                    registerPlayAction()
                },
                onStick = {
                    handleToyDrop(DragToy.STICK)
                    registerPlayAction()
                },

                onPet = {
                    temporaryAnimationDurationMs = 2500L
                    playTemporaryAnimation(R.drawable.dog_happy, temporaryAnimationDurationMs) {
                        onPet()
                    }

                    kotlinx.coroutines.GlobalScope.launch {
                        delay(1250)
                        playBark()
                    }
                },
                onRest = {
                    onRest()
                    temporaryDogAnimation = null
                    registerRestAction()
                }
            )
        }

        BluetoothFloatingButton(
            onClick = { showBluetoothModal = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 96.dp, end = 18.dp)
        )

        if (showBluetoothModal) {
            BluetoothControlModal(
                message = message,
                onClose = { showBluetoothModal = false },
                onConnectBluetooth = onConnectBluetooth,
                onMoveForward = {
                    onMoveForward()
                    registerMovementAction()
                },
                onMoveBack = {
                    onMoveBack()
                    registerMovementAction()
                },
                onMoveLeft = {
                    onMoveLeft()
                    registerMovementAction()
                },
                onMoveRight = {
                    onMoveRight()
                    registerMovementAction()
                },
                onStop = onStop
            )
        }

        if (!showBluetoothModal) {
            educationalMessage?.let { currentMessage ->
                EducationalMessageModal(
                    educationalMessage = currentMessage,
                    onConfirm = {
                        onEducationalReward(
                            currentMessage.hungerReward,
                            currentMessage.happinessReward,
                            currentMessage.energyReward,
                            currentMessage.healthReward
                        )

                        educationalMessage = null
                    }
                )
            }
        }
    }
}

@Composable
fun StatusBar(dog: DogEntity) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            StatusItem(R.drawable.icon_hunger, dog.hunger)
            StatusItem(R.drawable.icon_happiness, dog.happiness)
            StatusItem(R.drawable.icon_energy, dog.energy)
            StatusItem(R.drawable.icon_health, dog.health)
            StatusItem(R.drawable.icon_battery, dog.battery)
        }
    }
}

@Composable
fun StatusItem(
    iconRes: Int,
    value: Int
) {
    val bgColor = when {
        value >= 76 -> Color(0xFF35C85A)
        value >= 51 -> Color(0xFFFFD447)
        value >= 26 -> Color(0xFFFF8A2A)
        else -> Color(0xFFE53935)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(58.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(27.dp)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = "$value%",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.Black
        )
    }
}

@Composable
fun MainActionButtons(
    dogDropArea: Rect?,
    mouthDropArea: Rect?,

    onFoodDragStart: () -> Unit,
    onFoodDragCancel: () -> Unit,

    onCookie: () -> Unit,
    onBone: () -> Unit,
    onChocolate: () -> Unit,

    onBall: () -> Unit,
    onStick: () -> Unit,

    onPet: () -> Unit,
    onRest: () -> Unit
) {
    var openedMenu by remember { mutableStateOf<ActionMenu?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
    ) {
        when (openedMenu) {
            ActionMenu.FOOD -> {
                ActionButton(
                    iconRes = R.drawable.btn_food,
                    label = "Comer",
                    onClick = { openedMenu = null }
                )

                DraggableFoodButton(
                    food = DragFood.COOKIE,
                    label = "Galleta",
                    mouthDropArea = mouthDropArea,
                    onDragStart = onFoodDragStart,
                    onDragCancel = onFoodDragCancel,
                    onDroppedOnMouth = {
                        onCookie()
                        openedMenu = null
                    }
                )

                DraggableFoodButton(
                    food = DragFood.BONE,
                    label = "Hueso",
                    mouthDropArea = mouthDropArea,
                    onDragStart = onFoodDragStart,
                    onDragCancel = onFoodDragCancel,
                    onDroppedOnMouth = {
                        onBone()
                        openedMenu = null
                    }
                )

                DraggableFoodButton(
                    food = DragFood.CHOCOLATE,
                    label = "Chocolate",
                    mouthDropArea = mouthDropArea,
                    onDragStart = onFoodDragStart,
                    onDragCancel = onFoodDragCancel,
                    onDroppedOnMouth = {
                        onChocolate()
                        openedMenu = null
                    }
                )
            }

            ActionMenu.PLAY -> {
                ActionButton(
                    iconRes = R.drawable.btn_food,
                    label = "Comer",
                    onClick = { openedMenu = ActionMenu.FOOD }
                )

                ActionButton(
                    iconRes = R.drawable.btn_play,
                    label = "Jugar",
                    onClick = { openedMenu = null }
                )

                DraggableToyButton(
                    toy = DragToy.BALL,
                    label = "Pelota",
                    dogDropArea = dogDropArea,
                    onDroppedOnDog = {
                        onBall()
                        openedMenu = null
                    }
                )

                DraggableToyButton(
                    toy = DragToy.STICK,
                    label = "Rama",
                    dogDropArea = dogDropArea,
                    onDroppedOnDog = {
                        onStick()
                        openedMenu = null
                    }
                )
            }

            null -> {
                ActionButton(
                    iconRes = R.drawable.btn_food,
                    label = "Comer",
                    onClick = { openedMenu = ActionMenu.FOOD }
                )

                ActionButton(
                    iconRes = R.drawable.btn_play,
                    label = "Jugar",
                    onClick = { openedMenu = ActionMenu.PLAY }
                )

                ActionButton(R.drawable.btn_pet, "Acariciar", onPet)
                ActionButton(R.drawable.btn_sleep, "Dormir", onRest)
            }
        }
    }
}

@Composable
fun ActionButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(82.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.90f))
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                modifier = Modifier.size(45.dp)
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
    }
}

@Composable
fun DraggableToyButton(
    toy: DragToy,
    label: String,
    dogDropArea: Rect?,
    onDroppedOnDog: () -> Unit
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var itemBounds by remember { mutableStateOf<Rect?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(82.dp)
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        offset.x.roundToInt(),
                        offset.y.roundToInt()
                    )
                }
                .size(70.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.90f))
                .onGloballyPositioned { coordinates ->
                    itemBounds = coordinates.boundsInRoot()
                }
                .pointerInput(toy) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offset += dragAmount
                        },
                        onDragEnd = {
                            val currentBounds = itemBounds
                            val itemCenter = currentBounds?.center

                            if (
                                dogDropArea != null &&
                                itemCenter != null &&
                                dogDropArea.contains(itemCenter)
                            ) {
                                onDroppedOnDog()
                            }

                            offset = Offset.Zero
                        },
                        onDragCancel = {
                            offset = Offset.Zero
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = toyImage(toy)),
                contentDescription = label,
                modifier = Modifier.size(45.dp)
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
    }
}

@Composable
fun DraggableFoodButton(
    food: DragFood,
    label: String,
    mouthDropArea: Rect?,
    onDragStart: () -> Unit,
    onDragCancel: () -> Unit,
    onDroppedOnMouth: () -> Unit
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var itemBounds by remember { mutableStateOf<Rect?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(82.dp)
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        offset.x.roundToInt(),
                        offset.y.roundToInt()
                    )
                }
                .size(70.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.90f))
                .onGloballyPositioned { coordinates ->
                    itemBounds = coordinates.boundsInRoot()
                }
                .pointerInput(food) {
                    detectDragGestures(
                        onDragStart = {
                            onDragStart()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offset += dragAmount
                        },
                        onDragEnd = {
                            val currentBounds = itemBounds
                            val itemCenter = currentBounds?.center

                            val droppedOnMouth =
                                mouthDropArea != null &&
                                        itemCenter != null &&
                                        mouthDropArea.contains(itemCenter)

                            if (droppedOnMouth) {
                                onDroppedOnMouth()
                            } else {
                                onDragCancel()
                            }

                            offset = Offset.Zero
                        },
                        onDragCancel = {
                            onDragCancel()
                            offset = Offset.Zero
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = foodImage(food)),
                contentDescription = label,
                modifier = Modifier.size(45.dp)
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
    }
}

@Composable
fun MovementControls(
    onMoveForward: () -> Unit,
    onMoveBack: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MovementButton("↑", onMoveForward, onStop)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MovementButton("←", onMoveLeft, onStop)
            MovementButton("↓", onMoveBack, onStop)
            MovementButton("→", onMoveRight, onStop)
        }
    }
}

@Composable
fun MovementButton(
    text: String,
    onDown: () -> Unit,
    onUp: () -> Unit
) {
    Button(
        onClick = {},
        modifier = Modifier
            .size(58.dp)
            .pointerInteropFilter { event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        onDown()
                        true
                    }

                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        onUp()
                        true
                    }

                    else -> true
                }
            },
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF6F4FB5)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AnimatedDogImage(
    resId: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(GifDecoder.Factory())
            }
            .build()
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(resId)
            .crossfade(false)
            .build(),
        imageLoader = imageLoader,
        contentDescription = "Perro animado",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
fun BluetoothFloatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.90f))
    ) {
        Text(
            text = "BT",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF6F4FB5)
        )
    }
}

@Composable
fun EducationalMessageModal(
    educationalMessage: EducationalMessage,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = educationalMessage.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF2E7D32)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = educationalMessage.message,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    color = Color(0xFF252525)
                )

                val rewards = buildList {
                    if (educationalMessage.hungerReward > 0) {
                        add("Hambre +${educationalMessage.hungerReward}")
                    }

                    if (educationalMessage.happinessReward > 0) {
                        add("Felicidad +${educationalMessage.happinessReward}")
                    }

                    if (educationalMessage.energyReward > 0) {
                        add("Energía +${educationalMessage.energyReward}")
                    }

                    if (educationalMessage.healthReward > 0) {
                        add("Salud +${educationalMessage.healthReward}")
                    }
                }

                if (rewards.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(18.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8F5E9)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Recompensa",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF2E7D32)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = rewards.joinToString(separator = "\n"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF43A047)
                    )
                ) {
                    Text(
                        text = educationalMessage.buttonText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun BluetoothControlModal(
    message: String,
    onClose: () -> Unit,
    onConnectBluetooth: () -> Unit,
    onMoveForward: () -> Unit,
    onMoveBack: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onStop: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.90f),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Control Bluetooth",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = message,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onConnectBluetooth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(
                        text = "Conectar a VOLTS",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))

                MovementControls(
                    onMoveForward = onMoveForward,
                    onMoveBack = onMoveBack,
                    onMoveLeft = onMoveLeft,
                    onMoveRight = onMoveRight,
                    onStop = onStop
                )

                Spacer(modifier = Modifier.height(26.dp))

                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Text(
                        text = "Cerrar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}