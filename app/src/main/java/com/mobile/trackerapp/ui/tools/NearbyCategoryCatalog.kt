package com.mobile.trackerapp.ui.tools

data class NearbyCategory(val name: String, val icon: String, val filters: List<String>)

object NearbyCategoryCatalog {
    val all = listOf(
        NearbyCategory("ATM", "🏧", listOf("[amenity=atm]")),
        NearbyCategory("Airport", "✈️", listOf("[aeroway=aerodrome]")),
        NearbyCategory("Amusement park", "🎡", listOf("[leisure=amusement_arcade]", "[tourism=theme_park]")),
        NearbyCategory("Art gallery", "🖼️", listOf("[tourism=gallery]")),
        NearbyCategory("Auto repair", "🔧", listOf("[shop=car_repair]", "[craft=car_repair]")),
        NearbyCategory("Bakery", "🥐", listOf("[shop=bakery]")),
        NearbyCategory("Bank", "🏦", listOf("[amenity=bank]")),
        NearbyCategory("Bar", "🍸", listOf("[amenity=bar]", "[amenity=pub]")),
        NearbyCategory("Beauty salon", "💇", listOf("[shop=beauty]")),
        NearbyCategory("Bookstore", "📚", listOf("[shop=books]")),
        NearbyCategory("Bus station", "🚌", listOf("[amenity=bus_station]")),
        NearbyCategory("Cafe", "☕", listOf("[amenity=cafe]", "[shop=coffee]")),
        NearbyCategory("Car dealer", "🚘", listOf("[shop=car]")),
        NearbyCategory("Cinema", "🎬", listOf("[amenity=cinema]")),
        NearbyCategory("Dentist", "🦷", listOf("[amenity=dentist]")),
        NearbyCategory("Electronics store", "📱", listOf("[shop=electronics]")),
        NearbyCategory("Fast food", "🍔", listOf("[amenity=fast_food]")),
        NearbyCategory("Food court", "🍽️", listOf("[amenity=food_court]")),
        NearbyCategory("Fuel station", "⛽", listOf("[amenity=fuel]")),
        NearbyCategory("Gym", "🏋️", listOf("[leisure=fitness_centre]")),
        NearbyCategory("Hardware store", "🛠️", listOf("[shop=hardware]")),
        NearbyCategory("Hospital", "🏥", listOf("[amenity=hospital]")),
        NearbyCategory("Hotel", "🏨", listOf("[tourism=hotel]", "[tourism=motel]")),
        NearbyCategory("Jewelry store", "💍", listOf("[shop=jewelry]")),
        NearbyCategory("Laundry", "🧺", listOf("[shop=laundry]", "[shop=dry_cleaning]")),
        NearbyCategory("Library", "📖", listOf("[amenity=library]")),
        NearbyCategory("Museum", "🏛️", listOf("[tourism=museum]")),
        NearbyCategory("Nightclub", "🎵", listOf("[amenity=nightclub]")),
        NearbyCategory("Park", "🌳", listOf("[leisure=park]")),
        NearbyCategory("Parking", "🅿️", listOf("[amenity=parking]")),
        NearbyCategory("Pharmacy", "💊", listOf("[amenity=pharmacy]")),
        NearbyCategory("Place of worship", "🛕", listOf("[amenity=place_of_worship]")),
        NearbyCategory("Police station", "🚓", listOf("[amenity=police]")),
        NearbyCategory("Post office", "📮", listOf("[amenity=post_office]")),
        NearbyCategory("Restaurant", "🍴", listOf("[amenity=restaurant]")),
        NearbyCategory("School", "🏫", listOf("[amenity=school]")),
        NearbyCategory("Shopping mall", "🛍️", listOf("[shop=mall]")),
        NearbyCategory("Spa", "🧖", listOf("[leisure=spa]")),
        NearbyCategory("Supermarket", "🛒", listOf("[shop=supermarket]")),
        NearbyCategory("Swimming pool", "🏊", listOf("[leisure=swimming_pool]")),
        NearbyCategory("Taxi stand", "🚕", listOf("[amenity=taxi]")),
        NearbyCategory("Train station", "🚆", listOf("[railway=station]")),
        NearbyCategory("Toy store", "🧸", listOf("[shop=toys]")),
        NearbyCategory("University", "🎓", listOf("[amenity=university]")),
        NearbyCategory("Veterinary", "🐾", listOf("[amenity=veterinary]")),
        NearbyCategory("Zoo", "🦒", listOf("[tourism=zoo]"))
    ).sortedBy { it.name.lowercase() }

    fun categoryForQuickChip(chip: String): NearbyCategory? = when (chip) {
        "Coffee" -> all.first { it.name == "Cafe" }
        "Food" -> NearbyCategory("Food", "🍴", listOf("[amenity=restaurant]", "[amenity=fast_food]", "[amenity=food_court]"))
        "Gas" -> all.first { it.name == "Fuel station" }
        else -> null
    }

    fun iconFor(name: String): String = all.firstOrNull { it.name == name }?.icon
        ?: categoryForQuickChip(name)?.icon ?: "📍"
}
