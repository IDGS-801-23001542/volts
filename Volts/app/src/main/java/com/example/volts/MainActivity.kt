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
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
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
            onChili = { viewModel.feedChili() },

            onBall = { viewModel.playBall() },
            onStick = { viewModel.playStick() },

            onPet = { viewModel.petDog() },
            onRest = { viewModel.toggleSleep() },

            onMoveForward = { viewModel.moveForward() },
            onMoveBack = { viewModel.moveBack() },
            onMoveLeft = { viewModel.moveLeft() },
            onMoveRight = { viewModel.moveRight() },
            onStop = { viewModel.stop() }
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
    var showBluetoothModal by remember { mutableStateOf(false) }

    var temporaryDogAnimation by remember { mutableStateOf<Int?>(null) }

    var temporaryAnimationDurationMs by remember { mutableStateOf(3000L) }

    var dogDropArea by remember { mutableStateOf<Rect?>(null) }

    var mouthDropArea by remember { mutableStateOf<Rect?>(null) }

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
                DragFood.CHILI -> onChili()
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
                    playTemporaryAnimation(R.drawable.dog_eat, temporaryAnimationDurationMs) {
                        onCookie()
                    }
                },
                onBone = {
                    temporaryAnimationDurationMs = 3800L
                    playTemporaryAnimation(R.drawable.dog_eat, temporaryAnimationDurationMs) {
                        onBone()
                    }
                },
                onChili = {
                    temporaryAnimationDurationMs = 3800L
                    playTemporaryAnimation(R.drawable.dog_eat, temporaryAnimationDurationMs) {
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
    onChili: () -> Unit,

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
                    food = DragFood.CHILI,
                    label = "Chile",
                    mouthDropArea = mouthDropArea,
                    onDragStart = onFoodDragStart,
                    onDragCancel = onFoodDragCancel,
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