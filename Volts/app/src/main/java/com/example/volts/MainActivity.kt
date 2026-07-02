package com.example.volts

import android.Manifest
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.GifDecoder
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.volts.data.DogEntity
import com.example.volts.ui.BluetoothConnectionState
import com.example.volts.ui.DogViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    CHILI
}

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
        DragFood.CHILI -> R.drawable.food_chili
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: DogViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            // El DogViewModel/BluetoothController reportará el error
            // si el usuario no concedió los permisos necesarios.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestBluetoothPermissionsIfNeeded()

        setContent {
            MaterialTheme {
                VoltsApp(viewModel)
            }
        }
    }

    private fun requestBluetoothPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }
    }
}

@Composable
fun VoltsApp(viewModel: DogViewModel) {
    val dog by viewModel.dog.collectAsState()
    val message by viewModel.message.collectAsState()

    /*
     * CONTRATO DE INTEGRACIÓN CON PERSONA 2:
     *
     * DogViewModel debe exponer:
     *
     * val bluetoothState: StateFlow<BluetoothConnectionState>
     *
     * y BluetoothConnectionState debe contener:
     *
     * DISCONNECTED, CONNECTING, CONNECTED, ERROR
     */
    val bluetoothState by viewModel.bluetoothState.collectAsState()

    if (dog == null) {
        CreateDogScreen(
            message = message,
            onCreateDog = { name ->
                viewModel.createDog(name)
            }
        )
    } else {
        DogHomeScreen(
            dog = dog!!,
            message = message,
            bluetoothState = bluetoothState,
            onConnectBluetooth = {
                viewModel.connectBluetooth()
            },
            onCookie = {
                viewModel.feedCookie()
            },
            onBone = {
                viewModel.feedBone()
            },
            onChili = {
                viewModel.feedChili()
            },
            onBall = {
                viewModel.playBall()
            },
            onStick = {
                viewModel.playStick()
            },
            onPet = {
                viewModel.petDog()
            },
            onRest = {
                viewModel.toggleSleep()
            },
            onMoveForward = {
                viewModel.moveForward()
            },
            onMoveBack = {
                viewModel.moveBack()
            },
            onMoveLeft = {
                viewModel.moveLeft()
            },
            onMoveRight = {
                viewModel.moveRight()
            },
            onStop = {
                viewModel.stop()
            }
        )
    }
}

@Composable
fun CreateDogScreen(
    message: String,
    onCreateDog: (String) -> Unit
) {
    var name by remember {
        mutableStateOf("")
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(
                id = R.drawable.background_day
            ),
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
            Text(
                text = "VOLTS",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = message,
                fontSize = 14.sp,
                color = Color.White
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

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

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                        },
                        label = {
                            Text("Nombre de VOLTS")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    Button(
                        onClick = {
                            val cleanName = name.trim()

                            if (cleanName.isNotEmpty()) {
                                onCreateDog(cleanName)
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
    bluetoothState: BluetoothConnectionState,
    onConnectBluetooth: () -> Unit,
    onCookie: () -> Unit,
    onBone: () -> Unit,
    onChili: () -> Unit,
    onBall: () -> Unit,
    onStick: () -> Unit,
    onPet: () -> Unit,
    onRest: () -> Unit,
    onMoveForward: () -> Unit,
    onMoveBack: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onStop: () -> Unit
) {
    var showBluetoothModal by remember {
        mutableStateOf(false)
    }

    var temporaryDogAnimation by remember {
        mutableStateOf<Int?>(null)
    }

    var temporaryAnimationDurationMs by remember {
        mutableStateOf(3000L)
    }

    var dogDropArea by remember {
        mutableStateOf<Rect?>(null)
    }

    var mouthDropArea by remember {
        mutableStateOf<Rect?>(null)
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(2)
            .build()
    }

    val barkSoundId = remember {
        soundPool.load(
            context,
            R.raw.dog_bark,
            1
        )
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

    fun playTemporaryAnimation(
        animationRes: Int,
        durationMs: Long,
        action: () -> Unit
    ) {
        action()
        temporaryAnimationDurationMs = durationMs
        temporaryDogAnimation = animationRes
    }

    fun handleToyDrop(toy: DragToy) {
        val duration = 7000L

        playTemporaryAnimation(
            animationRes = R.drawable.dog_play,
            durationMs = duration
        ) {
            when (toy) {
                DragToy.BALL -> onBall()
                DragToy.STICK -> onStick()
            }
        }

        coroutineScope.launch {
            delay(6208L)
            playBark()
        }
    }

    fun handleFoodDrop(food: DragFood) {
        val duration = 3800L

        playTemporaryAnimation(
            animationRes = R.drawable.dog_eat,
            durationMs = duration
        ) {
            when (food) {
                DragFood.COOKIE -> onCookie()
                DragFood.BONE -> onBone()
                DragFood.CHILI -> onChili()
            }
        }
    }

    LaunchedEffect(
        temporaryDogAnimation,
        temporaryAnimationDurationMs
    ) {
        if (temporaryDogAnimation != null) {
            delay(temporaryAnimationDurationMs)
            temporaryDogAnimation = null
        }
    }

    val baseDogAnimation = when {
        dog.sleeping -> R.drawable.dog_sleep

        dog.health < 50 ||
                dog.hunger < 50 -> R.drawable.dog_sick

        dog.happiness < 50 ||
                dog.energy < 50 -> R.drawable.dog_sad

        else -> R.drawable.dog_idle
    }

    val currentDogAnimation =
        temporaryDogAnimation ?: baseDogAnimation

    val currentBackground =
        if (dog.sleeping) {
            R.drawable.background_night
        } else {
            R.drawable.background_day
        }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(
                id = currentBackground
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    horizontal = 16.dp,
                    vertical = 10.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusBar(dog)

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = message,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(
                modifier = Modifier.height(170.dp)
            )

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
                            val dogBounds =
                                coordinates.boundsInRoot()

                            dogDropArea = dogBounds

                            mouthDropArea = Rect(
                                left =
                                    dogBounds.left +
                                            dogBounds.width * 0.38f,
                                top =
                                    dogBounds.top +
                                            dogBounds.height * 0.50f,
                                right =
                                    dogBounds.left +
                                            dogBounds.width * 0.62f,
                                bottom =
                                    dogBounds.top +
                                            dogBounds.height * 0.70f
                            )
                        }
                )
            }

            Spacer(
                modifier = Modifier.height(28.dp)
            )

            MainActionButtons(
                dogDropArea = dogDropArea,
                mouthDropArea = mouthDropArea,
                onCookie = {
                    playTemporaryAnimation(
                        animationRes = R.drawable.dog_eat,
                        durationMs = 3800L
                    ) {
                        onCookie()
                    }
                },
                onBone = {
                    playTemporaryAnimation(
                        animationRes = R.drawable.dog_eat,
                        durationMs = 3800L
                    ) {
                        onBone()
                    }
                },
                onChili = {
                    playTemporaryAnimation(
                        animationRes = R.drawable.dog_eat,
                        durationMs = 3800L
                    ) {
                        onChili()
                    }
                },
                onBall = {
                    handleToyDrop(DragToy.BALL)
                },
                onStick = {
                    handleToyDrop(DragToy.STICK)
                },
                onPet = {
                    playTemporaryAnimation(
                        animationRes = R.drawable.dog_happy,
                        durationMs = 2500L
                    ) {
                        onPet()
                    }

                    coroutineScope.launch {
                        delay(1250L)
                        playBark()
                    }
                },
                onRest = {
                    onRest()
                    temporaryDogAnimation = null
                }
            )
        }

        BluetoothFloatingButton(
            connectionState = bluetoothState,
            onClick = {
                showBluetoothModal = true
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(
                    top = 96.dp,
                    end = 18.dp
                )
        )

        if (showBluetoothModal) {
            BluetoothControlModal(
                message = message,
                connectionState = bluetoothState,
                onClose = {
                    showBluetoothModal = false
                },
                onConnectBluetooth = onConnectBluetooth,
                onMoveForward = onMoveForward,
                onMoveBack = onMoveBack,
                onMoveLeft = onMoveLeft,
                onMoveRight = onMoveRight,
                onStop = onStop
            )
        }
    }
}

@Composable
fun StatusBar(dog: DogEntity) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Color.White.copy(alpha = 0.72f)
            )
            .padding(
                horizontal = 6.dp,
                vertical = 8.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceEvenly,
            verticalAlignment =
                Alignment.Top
        ) {
            StatusItem(
                R.drawable.icon_hunger,
                dog.hunger
            )

            StatusItem(
                R.drawable.icon_happiness,
                dog.happiness
            )

            StatusItem(
                R.drawable.icon_energy,
                dog.energy
            )

            StatusItem(
                R.drawable.icon_health,
                dog.health
            )

            StatusItem(
                R.drawable.icon_battery,
                dog.battery
            )
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
        horizontalAlignment =
            Alignment.CenterHorizontally,
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
                painter = painterResource(
                    id = iconRes
                ),
                contentDescription = null,
                modifier = Modifier.size(27.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(3.dp)
        )

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
    onCookie: () -> Unit,
    onBone: () -> Unit,
    onChili: () -> Unit,
    onBall: () -> Unit,
    onStick: () -> Unit,
    onPet: () -> Unit,
    onRest: () -> Unit
) {
    var openedMenu by remember {
        mutableStateOf<ActionMenu?>(null)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
    ) {
        when (openedMenu) {
            ActionMenu.FOOD -> {
                ActionButton(
                    iconRes = R.drawable.btn_food,
                    label = "Comer",
                    onClick = {
                        openedMenu = null
                    }
                )

                DraggableFoodButton(
                    food = DragFood.COOKIE,
                    label = "Galleta",
                    mouthDropArea = mouthDropArea,
                    onDroppedOnMouth = {
                        onCookie()
                        openedMenu = null
                    }
                )

                DraggableFoodButton(
                    food = DragFood.BONE,
                    label = "Hueso",
                    mouthDropArea = mouthDropArea,
                    onDroppedOnMouth = {
                        onBone()
                        openedMenu = null
                    }
                )

                DraggableFoodButton(
                    food = DragFood.CHILI,
                    label = "Chile",
                    mouthDropArea = mouthDropArea,
                    onDroppedOnMouth = {
                        onChili()
                        openedMenu = null
                    }
                )
            }

            ActionMenu.PLAY -> {
                ActionButton(
                    iconRes = R.drawable.btn_food,
                    label = "Comer",
                    onClick = {
                        openedMenu = ActionMenu.FOOD
                    }
                )

                ActionButton(
                    iconRes = R.drawable.btn_play,
                    label = "Jugar",
                    onClick = {
                        openedMenu = null
                    }
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
                    onClick = {
                        openedMenu = ActionMenu.FOOD
                    }
                )

                ActionButton(
                    iconRes = R.drawable.btn_play,
                    label = "Jugar",
                    onClick = {
                        openedMenu = ActionMenu.PLAY
                    }
                )

                ActionButton(
                    iconRes = R.drawable.btn_pet,
                    label = "Acariciar",
                    onClick = onPet
                )

                ActionButton(
                    iconRes = R.drawable.btn_sleep,
                    label = "Dormir",
                    onClick = onRest
                )
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
        horizontalAlignment =
            Alignment.CenterHorizontally,
        modifier = Modifier.width(82.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(
                    Color.White.copy(alpha = 0.90f)
                )
        ) {
            Image(
                painter = painterResource(
                    id = iconRes
                ),
                contentDescription = label,
                modifier = Modifier.size(45.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(5.dp)
        )

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
    var offset by remember {
        mutableStateOf(Offset.Zero)
    }

    var itemBounds by remember {
        mutableStateOf<Rect?>(null)
    }

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally,
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
                .background(
                    Color.White.copy(alpha = 0.90f)
                )
                .onGloballyPositioned { coordinates ->
                    itemBounds =
                        coordinates.boundsInRoot()
                }
                .pointerInput(toy) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offset += dragAmount
                        },
                        onDragEnd = {
                            val itemCenter =
                                itemBounds?.center

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
                painter = painterResource(
                    id = toyImage(toy)
                ),
                contentDescription = label,
                modifier = Modifier.size(45.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(5.dp)
        )

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
    onDroppedOnMouth: () -> Unit
) {
    var offset by remember {
        mutableStateOf(Offset.Zero)
    }

    var itemBounds by remember {
        mutableStateOf<Rect?>(null)
    }

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally,
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
                .background(
                    Color.White.copy(alpha = 0.90f)
                )
                .onGloballyPositioned { coordinates ->
                    itemBounds =
                        coordinates.boundsInRoot()
                }
                .pointerInput(food) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offset += dragAmount
                        },
                        onDragEnd = {
                            val itemCenter =
                                itemBounds?.center

                            if (
                                mouthDropArea != null &&
                                itemCenter != null &&
                                mouthDropArea.contains(itemCenter)
                            ) {
                                onDroppedOnMouth()
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
                painter = painterResource(
                    id = foodImage(food)
                ),
                contentDescription = label,
                modifier = Modifier.size(45.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(5.dp)
        )

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
    enabled: Boolean,
    onMoveForward: () -> Unit,
    onMoveBack: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {
        MovementButton(
            text = "↑",
            enabled = enabled,
            onDown = onMoveForward,
            onUp = onStop
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            MovementButton(
                text = "←",
                enabled = enabled,
                onDown = onMoveLeft,
                onUp = onStop
            )

            MovementButton(
                text = "↓",
                enabled = enabled,
                onDown = onMoveBack,
                onUp = onStop
            )

            MovementButton(
                text = "→",
                enabled = enabled,
                onDown = onMoveRight,
                onUp = onStop
            )
        }
    }
}

@Composable
fun MovementButton(
    text: String,
    enabled: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit
) {
    var pressed by remember {
        mutableStateOf(false)
    }

    Button(
        onClick = {},
        enabled = enabled,
        modifier = Modifier
            .size(58.dp)
            .pointerInteropFilter { event ->
                if (!enabled) {
                    pressed = false
                    return@pointerInteropFilter false
                }

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!pressed) {
                            pressed = true
                            onDown()
                        }

                        true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        if (pressed) {
                            pressed = false
                            onUp()
                        }

                        true
                    }

                    else -> true
                }
            },
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF6F4FB5),
            disabledContainerColor =
                Color(0xFF8E8E93)
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
    connectionState: BluetoothConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor =
        when (connectionState) {
            BluetoothConnectionState.CONNECTED ->
                Color(0xFF35C85A)

            BluetoothConnectionState.CONNECTING ->
                Color(0xFFFFD447)

            BluetoothConnectionState.ERROR ->
                Color(0xFFE53935)

            BluetoothConnectionState.DISCONNECTED ->
                Color.White.copy(alpha = 0.90f)
        }

    val textColor =
        when (connectionState) {
            BluetoothConnectionState.DISCONNECTED ->
                Color(0xFF6F4FB5)

            BluetoothConnectionState.CONNECTING ->
                Color.Black

            BluetoothConnectionState.CONNECTED,
            BluetoothConnectionState.ERROR ->
                Color.White
        }

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(backgroundColor)
    ) {
        Text(
            text = "BT",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = textColor
        )
    }
}

@Composable
fun BluetoothControlModal(
    message: String,
    connectionState: BluetoothConnectionState,
    onClose: () -> Unit,
    onConnectBluetooth: () -> Unit,
    onMoveForward: () -> Unit,
    onMoveBack: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onStop: () -> Unit
) {
    val connected =
        connectionState ==
                BluetoothConnectionState.CONNECTED

    val connectionLabel =
        when (connectionState) {
            BluetoothConnectionState.DISCONNECTED ->
                "Desconectado"

            BluetoothConnectionState.CONNECTING ->
                "Conectando..."

            BluetoothConnectionState.CONNECTED ->
                "Conectado a VOLTS"

            BluetoothConnectionState.ERROR ->
                "Error de conexión"
        }

    val connectionColor =
        when (connectionState) {
            BluetoothConnectionState.CONNECTED ->
                Color(0xFF178A36)

            BluetoothConnectionState.CONNECTING ->
                Color(0xFF9A7200)

            BluetoothConnectionState.ERROR ->
                Color(0xFFC62828)

            BluetoothConnectionState.DISCONNECTED ->
                Color(0xFF616161)
        }

    val connectButtonText =
        when (connectionState) {
            BluetoothConnectionState.CONNECTING ->
                "Conectando..."

            BluetoothConnectionState.CONNECTED ->
                "VOLTS conectado"

            BluetoothConnectionState.ERROR ->
                "Reintentar conexión"

            BluetoothConnectionState.DISCONNECTED ->
                "Conectar a VOLTS"
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color.Black.copy(alpha = 0.72f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.90f),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Control Bluetooth",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                Text(
                    text = connectionLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = connectionColor
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Text(
                    text = message,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(
                    modifier = Modifier.height(20.dp)
                )

                Button(
                    onClick = onConnectBluetooth,
                    enabled =
                        connectionState !=
                                BluetoothConnectionState.CONNECTING &&
                                connectionState !=
                                BluetoothConnectionState.CONNECTED,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(
                        text = connectButtonText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(
                    modifier = Modifier.height(26.dp)
                )

                MovementControls(
                    enabled = connected,
                    onMoveForward = onMoveForward,
                    onMoveBack = onMoveBack,
                    onMoveLeft = onMoveLeft,
                    onMoveRight = onMoveRight,
                    onStop = onStop
                )

                if (!connected) {
                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Text(
                        text =
                            "Conecta VOLTS para habilitar " +
                                    "los controles de movimiento.",
                        fontSize = 13.sp,
                        color = Color(0xFF616161)
                    )
                }

                Spacer(
                    modifier = Modifier.height(26.dp)
                )

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
