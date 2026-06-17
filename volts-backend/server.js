require("dotenv").config();
const express = require("express");
const cors = require("cors");
const mongoose = require("mongoose");

const app = express();

app.use(cors());
app.use(express.json());

mongoose
  .connect(process.env.MONGO_URI)
  .then(() => console.log("MongoDB conectado"))
  .catch((err) => console.error("Error MongoDB:", err));

const dogSchema = new mongoose.Schema(
  {
    deviceId: { type: String, required: true },
    name: { type: String, required: true, unique: true },
    ageDays: { type: Number, default: 0 },
    hunger: { type: Number, default: 100 },
    happiness: { type: Number, default: 100 },
    energy: { type: Number, default: 100 },
    health: { type: Number, default: 100 },
    battery: { type: Number, default: 100 },
    alive: { type: Boolean, default: true },
    appearance: {
      color: { type: String, default: "Blanco" },
      accessory: { type: String, default: "Collar" }
    },
    deathReason: { type: String, default: null },
    diedAt: { type: Date, default: null }
  },
  { timestamps: true }
);

const Dog = mongoose.model("Dog", dogSchema);

app.get("/", (req, res) => {
  res.json({ message: "VOLTS backend activo" });
});

app.get("/dogs/:deviceId", async (req, res) => {
  const dog = await Dog.findOne({
    deviceId: req.params.deviceId,
    alive: true
  });

  res.json(dog);
});

app.post("/dogs", async (req, res) => {
  try {
    const existingActive = await Dog.findOne({
      deviceId: req.body.deviceId,
      alive: true
    });

    if (existingActive) {
      return res.status(400).json({
        message: "Ya existe un perro activo en este dispositivo"
      });
    }

    const nameUsed = await Dog.findOne({ name: req.body.name });

    if (nameUsed) {
      return res.status(400).json({
        message: "Ese nombre ya fue usado y no se puede reutilizar"
      });
    }

    const dog = await Dog.create(req.body);
    res.status(201).json(dog);
  } catch (err) {
    res.status(500).json({ message: "Error creando perro", error: err.message });
  }
});

app.put("/dogs/:id", async (req, res) => {
  const dog = await Dog.findByIdAndUpdate(req.params.id, req.body, {
    new: true
  });

  res.json(dog);
});

app.post("/dogs/:id/die", async (req, res) => {
  const dog = await Dog.findByIdAndUpdate(
    req.params.id,
    {
      alive: false,
      deathReason: req.body.reason,
      diedAt: new Date()
    },
    { new: true }
  );

  res.json(dog);
});

app.get("/graveyard/:deviceId", async (req, res) => {
  const dogs = await Dog.find({
    deviceId: req.params.deviceId,
    alive: false
  }).sort({ diedAt: -1 });

  res.json(dogs);
});

app.listen(process.env.PORT, () => {
  console.log(`Servidor VOLTS en puerto ${process.env.PORT}`);
});