package com.catguard.detection

/**
 * Something the detector can be asked to watch for.
 *
 * [label] must match the category string the model emits. The rest is purely
 * presentation, so swapping in a different model means editing this catalogue
 * rather than hunting through the UI.
 */
data class AnimalTarget(
    val label: String,
    val displayName: String,
    val emoji: String,
    /** Shown under the name so it is clear what to expect. */
    val note: String = "",
)

/** How the catalogue is grouped in the picker. */
data class TargetGroup(
    val name: String,
    val targets: List<AnimalTarget>,
)

/**
 * Everything the bundled COCO model can recognise - all 80 categories.
 *
 * The full list is exposed rather than just the animals, because "what can this
 * thing actually see?" is the first question anyone has, and hiding 69 of the
 * answers makes the app look less capable than it is. Anything here can be made
 * to trigger the deterrent; the animals are simply the ones that make sense for
 * the job, so they are listed first and everything else sits behind an expander.
 *
 * Nothing outside this list can be detected without replacing the model.
 */
object SupportedAnimals {

    val cat = AnimalTarget("cat", "Cat", "🐱", "What CatGuard is for. Detected reliably in good light.")

    /** The categories worth putting at the top in a house. */
    val common: List<AnimalTarget> = listOf(
        cat,
        AnimalTarget("dog", "Dog", "🐶", "Detected well. Leave off if you own a dog."),
        AnimalTarget("bird", "Bird", "🐦", "Detected when close and reasonably large in frame."),
        AnimalTarget("person", "Person", "🧍", "Very reliable. Useful for testing; will fire at household members."),
    )

    /** The remaining COCO animals. Unlikely indoors, but the model knows them. */
    val otherAnimals: List<AnimalTarget> = listOf(
        AnimalTarget("horse", "Horse", "🐎"),
        AnimalTarget("sheep", "Sheep", "🐑"),
        AnimalTarget("cow", "Cow", "🐄"),
        AnimalTarget("bear", "Bear", "🐻"),
        AnimalTarget("elephant", "Elephant", "🐘"),
        AnimalTarget("zebra", "Zebra", "🦓"),
        AnimalTarget("giraffe", "Giraffe", "🦒"),
    )

    /**
     * Everything else in COCO, grouped.
     *
     * These are real, selectable targets - watching for a "backpack" or a "cell
     * phone" works exactly like watching for a cat. They are not what the app is
     * for, but they are what the model can do, so they are shown honestly.
     */
    val otherGroups: List<TargetGroup> = listOf(
        TargetGroup(
            "Vehicles",
            listOf(
                AnimalTarget("bicycle", "Bicycle", "🚲"),
                AnimalTarget("car", "Car", "🚗"),
                AnimalTarget("motorcycle", "Motorcycle", "🏍"),
                AnimalTarget("airplane", "Aeroplane", "✈"),
                AnimalTarget("bus", "Bus", "🚌"),
                AnimalTarget("train", "Train", "🚆"),
                AnimalTarget("truck", "Truck", "🚚"),
                AnimalTarget("boat", "Boat", "⛵"),
            ),
        ),
        TargetGroup(
            "Street",
            listOf(
                AnimalTarget("traffic light", "Traffic light", "🚦"),
                AnimalTarget("fire hydrant", "Fire hydrant", "🚧"),
                AnimalTarget("stop sign", "Stop sign", "🛑"),
                AnimalTarget("parking meter", "Parking meter", "🅿"),
                AnimalTarget("bench", "Bench", "🪑"),
            ),
        ),
        TargetGroup(
            "Things people carry",
            listOf(
                AnimalTarget("backpack", "Backpack", "🎒"),
                AnimalTarget("umbrella", "Umbrella", "☂"),
                AnimalTarget("handbag", "Handbag", "👜"),
                AnimalTarget("tie", "Tie", "👔"),
                AnimalTarget("suitcase", "Suitcase", "🧳"),
            ),
        ),
        TargetGroup(
            "Sport",
            listOf(
                AnimalTarget("frisbee", "Frisbee", "🥏"),
                AnimalTarget("skis", "Skis", "🎿"),
                AnimalTarget("snowboard", "Snowboard", "🏂"),
                AnimalTarget("sports ball", "Ball", "⚽"),
                AnimalTarget("kite", "Kite", "🪁"),
                AnimalTarget("baseball bat", "Baseball bat", "🏏"),
                AnimalTarget("baseball glove", "Baseball glove", "🧤"),
                AnimalTarget("skateboard", "Skateboard", "🛹"),
                AnimalTarget("surfboard", "Surfboard", "🏄"),
                AnimalTarget("tennis racket", "Tennis racket", "🎾"),
            ),
        ),
        TargetGroup(
            "Kitchen",
            listOf(
                AnimalTarget("bottle", "Bottle", "🍼"),
                AnimalTarget("wine glass", "Wine glass", "🍷"),
                AnimalTarget("cup", "Cup", "☕"),
                AnimalTarget("fork", "Fork", "🍴"),
                AnimalTarget("knife", "Knife", "🔪"),
                AnimalTarget("spoon", "Spoon", "🥄"),
                AnimalTarget("bowl", "Bowl", "🥣"),
            ),
        ),
        TargetGroup(
            "Food",
            listOf(
                AnimalTarget("banana", "Banana", "🍌"),
                AnimalTarget("apple", "Apple", "🍎"),
                AnimalTarget("sandwich", "Sandwich", "🥪"),
                AnimalTarget("orange", "Orange", "🍊"),
                AnimalTarget("broccoli", "Broccoli", "🥦"),
                AnimalTarget("carrot", "Carrot", "🥕"),
                AnimalTarget("hot dog", "Hot dog", "🌭"),
                AnimalTarget("pizza", "Pizza", "🍕"),
                AnimalTarget("donut", "Doughnut", "🍩"),
                AnimalTarget("cake", "Cake", "🍰"),
            ),
        ),
        TargetGroup(
            "Furniture",
            listOf(
                AnimalTarget("chair", "Chair", "🪑"),
                AnimalTarget("couch", "Sofa", "🛋"),
                AnimalTarget("potted plant", "Potted plant", "🪴"),
                AnimalTarget("bed", "Bed", "🛏"),
                AnimalTarget("dining table", "Dining table", "🍽"),
                AnimalTarget("toilet", "Toilet", "🚽"),
            ),
        ),
        TargetGroup(
            "Electronics",
            listOf(
                AnimalTarget("tv", "TV", "📺"),
                AnimalTarget("laptop", "Laptop", "💻"),
                AnimalTarget("mouse", "Computer mouse", "🖱"),
                AnimalTarget("remote", "Remote control", "🎛"),
                AnimalTarget("keyboard", "Keyboard", "⌨"),
                AnimalTarget("cell phone", "Phone", "📱"),
            ),
        ),
        TargetGroup(
            "Appliances",
            listOf(
                AnimalTarget("microwave", "Microwave", "🍛"),
                AnimalTarget("oven", "Oven", "🔥"),
                AnimalTarget("toaster", "Toaster", "🍞"),
                AnimalTarget("sink", "Sink", "🚰"),
                AnimalTarget("refrigerator", "Fridge", "🧊"),
            ),
        ),
        TargetGroup(
            "Around the house",
            listOf(
                AnimalTarget("book", "Book", "📖"),
                AnimalTarget("clock", "Clock", "🕓"),
                AnimalTarget("vase", "Vase", "🏺"),
                AnimalTarget("scissors", "Scissors", "✂"),
                AnimalTarget("teddy bear", "Teddy bear", "🧸"),
                AnimalTarget("hair drier", "Hair dryer", "💨"),
                AnimalTarget("toothbrush", "Toothbrush", "🪥"),
            ),
        ),
    )

    /** Animals only - the sensible targets for this app. */
    val all: List<AnimalTarget> = common + otherAnimals

    /** Literally everything the model knows. */
    val everything: List<AnimalTarget> = all + otherGroups.flatMap { it.targets }

    /** Total number of categories the model can output. */
    val totalCategories: Int get() = everything.size

    val labels: Set<String> = everything.map { it.label }.toSet()

    /** Kept for the picker's "uncommon animals" section. */
    val uncommon: List<AnimalTarget> get() = otherAnimals

    fun byLabel(label: String): AnimalTarget? =
        everything.firstOrNull { it.label.equals(label, ignoreCase = true) }

    fun displayNameFor(label: String): String =
        byLabel(label)?.displayName ?: label.replaceFirstChar { it.uppercase() }

    val DEFAULT_TARGETS: Set<String> = setOf(cat.label)
}
