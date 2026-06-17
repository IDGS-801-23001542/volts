package com.example.volts

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volts.ui.DogViewModel

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

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "VOLTS",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (dog == null) {
                CreateDogScreen(
                    onCreateDog = { name ->
                        viewModel.createDog(name)
                    }
                )
            } else {
                DogPanelScreen(viewModel)
            }
        }
    }
}

@Composable
fun CreateDogScreen(
    onCreateDog: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Crear perro",
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

@Composable
fun DogPanelScreen(viewModel: DogViewModel) {

    val dogState by viewModel.dog.collectAsState()

    val dog = dogState ?: return

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {

            Text(
                text = "🐶 ${dog.name}",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            StatText("Hambre", dog.hunger)
            StatText("Felicidad", dog.happiness)
            StatText("Energía", dog.energy)
            StatText("Salud", dog.health)
            StatText("Batería", dog.battery, "%")

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { viewModel.connectBluetooth() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Conectar Bluetooth")
            }

            Spacer(modifier = Modifier.height(16.dp))

            ButtonRow(
                leftText = "Alimentar",
                rightText = "Acariciar",
                onLeft = { viewModel.feed() },
                onRight = { viewModel.pet() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ButtonRow(
                leftText = "Jugar",
                rightText = "Descansar",
                onLeft = { viewModel.play() },
                onRight = { viewModel.rest() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Movimiento",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            MovementButton(
                "Adelante",
                viewModel::moveForward,
                viewModel::stop
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                MovementButton(
                    text = "Izquierda",
                    onDown = viewModel::moveLeft,
                    onUp = viewModel::stop,
                    modifier = Modifier.weight(1f)
                )

                MovementButton(
                    text = "Derecha",
                    onDown = viewModel::moveRight,
                    onUp = viewModel::stop,
                    modifier = Modifier.weight(1f)
                )
            }

            MovementButton(
                "Atrás",
                viewModel::moveBack,
                viewModel::stop
            )
        }
    }
}

@Composable
fun StatText(
    label: String,
    value: Int,
    suffix: String = ""
) {
    Text(
        text = "$label: $value$suffix",
        fontSize = 18.sp
    )
}

@Composable
fun ButtonRow(
    leftText: String,
    rightText: String,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onLeft,
            modifier = Modifier.weight(1f)
        ) {
            Text(leftText)
        }

        Button(
            onClick = onRight,
            modifier = Modifier.weight(1f)
        ) {
            Text(rightText)
        }
    }
}

@Composable
fun MovementButton(
    text: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Button(
        onClick = {},
        modifier = modifier
            .padding(vertical = 4.dp)
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
            }
    ) {
        Text(text)
    }
}