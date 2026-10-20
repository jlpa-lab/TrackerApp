package com.mobile.trackerapp.ui.tools

import androidx.annotation.DrawableRes
import com.mobile.trackerapp.R
import java.util.Locale

/**
 * Google Places API (New) Table A categories used by the Nearby picker.
 *
 * The googleType values are requestable Table A types. OSM filters are only
 * used by the isolated network fallback and are never sent to Google.
 */
data class NearbyCategory(
    val name: String,
    val googleType: String,
    @DrawableRes val iconRes: Int,
    val emoji: String,
    val group: String,
    val osmFilters: List<String> = emptyList()
)

object NearbyCategoryCatalog {
    private fun category(name: String, googleType: String, group: String, vararg osmFilters: String) =
        NearbyCategory(name, googleType, iconForCategory(name, group), emojiForCategory(name, group), group, osmFilters.toList())

    private fun emojiForCategory(name: String, group: String): String {
        val normalized = name.lowercase(Locale.ROOT)
        return when {
            // Keep the Explore Categories property and destination icons
            // explicit so broad group fallbacks do not make them all look the
            // same.
            normalized == "business center" -> "🏢"
            normalized == "corporate office" -> "🏙️"
            normalized == "art gallery" -> "🖼️"
            normalized == "amusement center" || normalized == "amusement park" -> "🎡"
            normalized.containsAny("bathroom", "toilet") -> "🚽"
            normalized == "apartment building" -> "🏬"
            normalized == "apartment complex" -> "🏘️"
            normalized == "condominium complex" -> "🏙️"
            normalized == "guest house" -> "🛖"
            normalized == "beach" -> "🏖️"
            normalized == "lake" -> "⛵"
            normalized == "scenic spot" -> "🌅"
            normalized.containsAny("hospital") -> "🏥"
            normalized.containsAny("pharmacy", "drugstore") -> "💊"
            normalized.containsAny("doctor", "chiropractor", "physiotherapist") -> "👨‍⚕️"
            normalized.containsAny("dentist", "dental") -> "🦷"
            normalized.containsAny("medical lab", "laboratory") -> "🧪"
            normalized.containsAny("emergency", "ambulance") -> "🚑"
            normalized.containsAny("police") -> "👮"
            normalized.containsAny("fire station") -> "🚒"
            normalized.containsAny("veterinary", "vet") -> "🐾"
            normalized.containsAny("cafe", "coffee", "tea", "internet cafe") -> "☕"
            normalized.containsAny("pizza") -> "🍕"
            normalized.containsAny("fast food", "hamburger", "sandwich", "chicken") -> "🍔"
            normalized.containsAny("bakery", "cake", "donut", "pastry") -> "🥐"
            normalized.containsAny("ice cream", "dessert") -> "🍦"
            normalized.containsAny("vegetarian", "vegan", "salad") -> "🥗"
            normalized.containsAny("chinese") -> "🍜"
            normalized.containsAny("indian") -> "🍛"
            normalized.containsAny("restaurant", "bistro", "deli", "food court", "meal", "buffet", "steak", "seafood") -> "🍽️"
            normalized.containsAny("petrol", "gas station", "fuel") -> "⛽"
            normalized.containsAny("electric vehicle", "charging") -> "⚡"
            normalized.containsAny("parking") -> "🅿️"
            normalized.containsAny("bus station") -> "🚌"
            normalized.containsAny("bus stop") -> "🚏"
            normalized.containsAny("train", "rail") -> "🚆"
            normalized.containsAny("subway", "metro") -> "🚇"
            normalized.containsAny("airport", "airstrip", "heliport") -> "✈️"
            normalized.containsAny("taxi") -> "🚕"
            normalized.containsAny("car rental", "car dealer") -> "🚗"
            normalized.containsAny("car repair", "tire", "mechanic") -> "🔧"
            normalized.containsAny("car wash") -> "🚿"
            normalized.containsAny("supermarket") -> "🛒"
            normalized.containsAny("grocery") -> "🥬"
            normalized.containsAny("shopping mall", "mall") -> "🛍️"
            normalized.containsAny("clothing") -> "👕"
            normalized.containsAny("shoe") -> "👟"
            normalized.containsAny("electronics") -> "📱"
            normalized.containsAny("book", "library") -> "📚"
            normalized.containsAny("jewelry") -> "💎"
            normalized == "atm" -> "🏧"
            normalized == "bank" || group == "Finance" -> "🏦"
            normalized.containsAny("school", "preschool", "educational") -> "🏫"
            normalized.containsAny("university", "academic", "research") -> "🎓"
            normalized.containsAny("park", "garden", "hiking", "botanical") -> "🌳"
            normalized.containsAny("museum", "gallery", "cultural", "historical", "monument", "castle") -> "🏛️"
            normalized.containsAny("movie", "cinema", "theater", "theatre") -> "🎬"
            normalized.containsAny("zoo", "wildlife") -> "🦁"
            normalized.containsAny("aquarium") -> "🐠"
            normalized.containsAny("amusement", "theme park", "ferris", "water park") -> "🎢"
            normalized.containsAny("stadium", "sports") -> "🏟️"
            normalized.containsAny("gym", "fitness", "yoga") -> "🏋️"
            normalized.containsAny("swimming", "pool") -> "🏊"
            normalized.containsAny("hotel") -> "🏨"
            normalized.containsAny("hostel") -> "🛏️"
            normalized.containsAny("resort") -> "🏝️"
            normalized.containsAny("camp", "cottage", "cabin") -> "⛺"
            normalized.containsAny("barber") -> "💈"
            normalized.containsAny("beauty", "hair", "salon") -> "💇"
            normalized.containsAny("spa", "sauna", "massage", "wellness") -> "🧖"
            normalized.containsAny("laundry") -> "🧺"
            normalized.containsAny("post office") -> "🏤"
            normalized.containsAny("government", "city hall", "embassy") -> "🏛️"
            normalized.containsAny("courthouse") -> "⚖️"
            normalized.containsAny("hindu", "temple") -> "🛕"
            normalized.containsAny("mosque") -> "🕌"
            normalized.containsAny("church") -> "⛪"
            normalized.containsAny("synagogue") -> "🕍"
            group == "Food and Drink" -> "🍽️"
            group == "Automotive" || group == "Transportation" -> "🚗"
            group == "Health and Wellness" -> "🏥"
            group == "Education" -> "🎓"
            group == "Entertainment and Recreation" -> "🎡"
            group == "Lodging" -> "🏨"
            group == "Finance" -> "🏦"
            else -> "📍"
        }
    }

    private fun iconForCategory(name: String, group: String): Int {
        val normalized = name.lowercase(Locale.ROOT)
        return when {
            normalized.containsAny("coffee", "cafe", "tea", "bakery") -> R.drawable.ic_nearby_coffee
            group == "Food and Drink" || normalized.containsAny("restaurant", "food", "meal", "bar", "pizza") -> R.drawable.ic_nearby_food
            group == "Automotive" || group == "Transportation" || normalized.containsAny(
                "car", "gas", "fuel", "parking", "tire", "vehicle", "charging", "motorcycle"
            ) -> R.drawable.ic_nearby_gas
            else -> R.drawable.ic_nearby_more
        }
    }

    private fun String.containsAny(vararg values: String): Boolean = values.any(::contains)

    /** Requestable Google Places API (New) Table A types, grouped for the picker. */
    val all: List<NearbyCategory> = listOf(
        category("Car dealer", "car_dealer", "Automotive", "[shop=car]"),
        category("Car rental", "car_rental", "Automotive", "[amenity=car_rental]"),
        category("Car repair", "car_repair", "Automotive", "[shop=car_repair]", "[craft=car_repair]"),
        category("Car wash", "car_wash", "Automotive", "[amenity=car_wash]"),
        category("Electric vehicle charging", "electric_vehicle_charging_station", "Automotive", "[amenity=charging_station]"),
        category("Gas station", "gas_station", "Automotive", "[amenity=fuel]"),
        category("Parking", "parking", "Automotive", "[amenity=parking]"),
        category("Parking garage", "parking_garage", "Automotive", "[amenity=parking]"),
        category("Parking lot", "parking_lot", "Automotive", "[amenity=parking]"),
        category("Rest stop", "rest_stop", "Automotive", "[amenity=rest_area]"),
        category("Tire shop", "tire_shop", "Automotive", "[shop=tyres]"),
        category("Business center", "business_center", "Business"),
        category("Corporate office", "corporate_office", "Business", "[office=company]"),
        category("Coworking space", "coworking_space", "Business", "[amenity=coworking_space]"),
        category("Farm", "farm", "Business", "[landuse=farm]"),
        category("Manufacturer", "manufacturer", "Business", "[industrial=manufacture]"),
        category("Ranch", "ranch", "Business", "[landuse=farm]"),
        category("Supplier", "supplier", "Business", "[office=company]"),
        category("Art gallery", "art_gallery", "Culture", "[tourism=gallery]"),
        category("Art museum", "art_museum", "Culture", "[tourism=museum]"),
        category("Art studio", "art_studio", "Culture", "[craft=artist]"),
        category("Auditorium", "auditorium", "Culture", "[amenity=arts_centre]"),
        category("Castle", "castle", "Culture", "[historic=castle]"),
        category("Cultural landmark", "cultural_landmark", "Culture", "[historic=monument]"),
        category("Fountain", "fountain", "Culture", "[amenity=fountain]"),
        category("Historical place", "historical_place", "Culture", "[historic]"),
        category("History museum", "history_museum", "Culture", "[tourism=museum]"),
        category("Monument", "monument", "Culture", "[historic=monument]"),
        category("Museum", "museum", "Culture", "[tourism=museum]"),
        category("Performing arts theater", "performing_arts_theater", "Culture", "[amenity=theatre]"),
        category("Academic department", "academic_department", "Education", "[education=department]"),
        category("Educational institution", "educational_institution", "Education", "[amenity=school]"),
        category("Library", "library", "Education", "[amenity=library]"),
        category("Preschool", "preschool", "Education", "[amenity=kindergarten]"),
        category("Primary school", "primary_school", "Education", "[amenity=school]"),
        category("Research institute", "research_institute", "Education", "[amenity=research_institute]"),
        category("School", "school", "Education", "[amenity=school]"),
        category("Secondary school", "secondary_school", "Education", "[amenity=school]"),
        category("University", "university", "Education", "[amenity=university]"),
        category("Adventure sports center", "adventure_sports_center", "Entertainment and Recreation"),
        category("Amphitheatre", "amphitheatre", "Entertainment and Recreation", "[amenity=theatre]"),
        category("Amusement center", "amusement_center", "Entertainment and Recreation", "[leisure=amusement_arcade]"),
        category("Amusement park", "amusement_park", "Entertainment and Recreation", "[tourism=theme_park]"),
        category("Aquarium", "aquarium", "Entertainment and Recreation", "[tourism=aquarium]"),
        category("Banquet hall", "banquet_hall", "Entertainment and Recreation", "[amenity=events_venue]"),
        category("Barbecue area", "barbecue_area", "Entertainment and Recreation", "[amenity=bbq]"),
        category("Botanical garden", "botanical_garden", "Entertainment and Recreation", "[tourism=botanical_garden]"),
        category("Bowling alley", "bowling_alley", "Entertainment and Recreation", "[leisure=bowling_alley]"),
        category("Casino", "casino", "Entertainment and Recreation", "[amenity=casino]"),
        category("Children's camp", "childrens_camp", "Entertainment and Recreation", "[tourism=camp_site]"),
        category("City park", "city_park", "Entertainment and Recreation", "[leisure=park]"),
        category("Comedy club", "comedy_club", "Entertainment and Recreation", "[amenity=theatre]"),
        category("Community center", "community_center", "Entertainment and Recreation", "[amenity=community_centre]"),
        category("Concert hall", "concert_hall", "Entertainment and Recreation", "[amenity=music_venue]"),
        category("Convention center", "convention_center", "Entertainment and Recreation", "[amenity=conference_centre]"),
        category("Cultural center", "cultural_center", "Entertainment and Recreation", "[amenity=arts_centre]"),
        category("Cycling park", "cycling_park", "Entertainment and Recreation", "[leisure=track]"),
        category("Dance hall", "dance_hall", "Entertainment and Recreation", "[amenity=dancing_school]"),
        category("Dog park", "dog_park", "Entertainment and Recreation", "[leisure=dog_park]"),
        category("Event venue", "event_venue", "Entertainment and Recreation", "[amenity=events_venue]"),
        category("Ferris wheel", "ferris_wheel", "Entertainment and Recreation"),
        category("Garden", "garden", "Entertainment and Recreation", "[leisure=garden]"),
        category("Go-karting venue", "go_karting_venue", "Entertainment and Recreation", "[leisure=track]"),
        category("Hiking area", "hiking_area", "Entertainment and Recreation", "[route=hiking]"),
        category("Indoor playground", "indoor_playground", "Entertainment and Recreation", "[leisure=playground]"),
        category("Internet cafe", "internet_cafe", "Entertainment and Recreation", "[amenity=internet_cafe]"),
        category("Karaoke", "karaoke", "Entertainment and Recreation", "[amenity=karaoke_box]"),
        category("Live music venue", "live_music_venue", "Entertainment and Recreation", "[amenity=music_venue]"),
        category("Marina", "marina", "Entertainment and Recreation", "[leisure=marina]"),
        category("Miniature golf course", "miniature_golf_course", "Entertainment and Recreation", "[leisure=miniature_golf]"),
        category("Movie theater", "movie_theater", "Entertainment and Recreation", "[amenity=cinema]"),
        category("National park", "national_park", "Entertainment and Recreation", "[boundary=national_park]"),
        category("Night club", "night_club", "Entertainment and Recreation", "[amenity=nightclub]"),
        category("Observation deck", "observation_deck", "Entertainment and Recreation", "[tourism=viewpoint]"),
        category("Opera house", "opera_house", "Entertainment and Recreation", "[amenity=theatre]"),
        category("Park", "park", "Entertainment and Recreation", "[leisure=park]"),
        category("Planetarium", "planetarium", "Entertainment and Recreation", "[amenity=planetarium]"),
        category("Plaza", "plaza", "Entertainment and Recreation", "[place=square]"),
        category("Skateboard park", "skateboard_park", "Entertainment and Recreation", "[leisure=skatepark]"),
        category("State park", "state_park", "Entertainment and Recreation", "[leisure=park]"),
        category("Video arcade", "video_arcade", "Entertainment and Recreation", "[leisure=amusement_arcade]"),
        category("Water park", "water_park", "Entertainment and Recreation", "[leisure=water_park]"),
        category("Wedding venue", "wedding_venue", "Entertainment and Recreation", "[amenity=events_venue]"),
        category("Wildlife park", "wildlife_park", "Entertainment and Recreation", "[tourism=zoo]"),
        category("Zoo", "zoo", "Entertainment and Recreation", "[tourism=zoo]"),
        category("Public bath", "public_bath", "Facilities", "[amenity=public_bath]"),
        category("Public bathroom", "public_bathroom", "Facilities", "[amenity=toilets]"),
        category("Stable", "stable", "Facilities", "[amenity=animal_boarding]"),
        category("Accounting", "accounting", "Finance", "[office=accountant]"),
        category("ATM", "atm", "Finance", "[amenity=atm]"),
        category("Bank", "bank", "Finance", "[amenity=bank]"),
        category("Cafe", "cafe", "Food and Drink", "[amenity=cafe]", "[shop=coffee]"),
        category("Coffee shop", "coffee_shop", "Food and Drink", "[amenity=cafe]"),
        category("Coffee roastery", "coffee_roastery", "Food and Drink", "[craft=roaster]"),
        category("Bakery", "bakery", "Food and Drink", "[shop=bakery]"),
        category("Bar", "bar", "Food and Drink", "[amenity=bar]"),
        category("Bistro", "bistro", "Food and Drink", "[amenity=restaurant]"),
        category("Breakfast restaurant", "breakfast_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Brewery", "brewery", "Food and Drink", "[craft=brewery]"),
        category("Brunch restaurant", "brunch_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Buffet restaurant", "buffet_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Cake shop", "cake_shop", "Food and Drink", "[shop=pastry]"),
        category("Candy store", "candy_store", "Food and Drink", "[shop=confectionery]"),
        category("Chicken restaurant", "chicken_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Chinese restaurant", "chinese_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Cocktail bar", "cocktail_bar", "Food and Drink", "[amenity=bar]"),
        category("Deli", "deli", "Food and Drink", "[shop=deli]"),
        category("Dessert restaurant", "dessert_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Donut shop", "donut_shop", "Food and Drink", "[shop=pastry]"),
        category("Fast food restaurant", "fast_food_restaurant", "Food and Drink", "[amenity=fast_food]"),
        category("Food court", "food_court", "Food and Drink", "[amenity=food_court]"),
        category("Hamburger restaurant", "hamburger_restaurant", "Food and Drink", "[amenity=fast_food]"),
        category("Ice cream shop", "ice_cream_shop", "Food and Drink", "[amenity=ice_cream]"),
        category("Indian restaurant", "indian_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Italian restaurant", "italian_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Japanese restaurant", "japanese_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Juice shop", "juice_shop", "Food and Drink", "[amenity=juice_bar]"),
        category("Korean restaurant", "korean_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Meal delivery", "meal_delivery", "Food and Drink", "[amenity=restaurant]"),
        category("Meal takeaway", "meal_takeaway", "Food and Drink", "[amenity=fast_food]"),
        category("Mexican restaurant", "mexican_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Pizza restaurant", "pizza_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Pub", "pub", "Food and Drink", "[amenity=pub]"),
        category("Ramen restaurant", "ramen_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Restaurant", "restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Salad shop", "salad_shop", "Food and Drink", "[amenity=restaurant]"),
        category("Sandwich shop", "sandwich_shop", "Food and Drink", "[amenity=fast_food]"),
        category("Seafood restaurant", "seafood_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Steak house", "steak_house", "Food and Drink", "[amenity=restaurant]"),
        category("Sushi restaurant", "sushi_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Tea house", "tea_house", "Food and Drink", "[amenity=cafe]"),
        category("Thai restaurant", "thai_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Vegan restaurant", "vegan_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Vegetarian restaurant", "vegetarian_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Vietnamese restaurant", "vietnamese_restaurant", "Food and Drink", "[amenity=restaurant]"),
        category("Wine bar", "wine_bar", "Food and Drink", "[amenity=bar]"),
        category("Winery", "winery", "Food and Drink", "[craft=winery]"),
        category("City hall", "city_hall", "Government", "[amenity=townhall]"),
        category("Courthouse", "courthouse", "Government", "[amenity=courthouse]"),
        category("Embassy", "embassy", "Government", "[amenity=embassy]"),
        category("Fire station", "fire_station", "Government", "[amenity=fire_station]"),
        category("Government office", "government_office", "Government", "[office=government]"),
        category("Police", "police", "Government", "[amenity=police]"),
        category("Post office", "post_office", "Government", "[amenity=post_office]"),
        category("Chiropractor", "chiropractor", "Health and Wellness", "[healthcare=chiropractic]"),
        category("Dental clinic", "dental_clinic", "Health and Wellness", "[amenity=dentist]"),
        category("Dentist", "dentist", "Health and Wellness", "[amenity=dentist]"),
        category("Doctor", "doctor", "Health and Wellness", "[amenity=doctors]"),
        category("Drugstore", "drugstore", "Health and Wellness", "[shop=chemist]"),
        category("General hospital", "general_hospital", "Health and Wellness", "[amenity=hospital]"),
        category("Hospital", "hospital", "Health and Wellness", "[amenity=hospital]"),
        category("Massage", "massage", "Health and Wellness", "[shop=massage]"),
        category("Medical center", "medical_center", "Health and Wellness", "[amenity=clinic]"),
        category("Medical clinic", "medical_clinic", "Health and Wellness", "[amenity=clinic]"),
        category("Medical lab", "medical_lab", "Health and Wellness", "[amenity=clinic]"),
        category("Pharmacy", "pharmacy", "Health and Wellness", "[amenity=pharmacy]"),
        category("Physiotherapist", "physiotherapist", "Health and Wellness", "[healthcare=physiotherapist]"),
        category("Sauna", "sauna", "Health and Wellness", "[leisure=sauna]"),
        category("Skin care clinic", "skin_care_clinic", "Health and Wellness", "[shop=beauty]"),
        category("Spa", "spa", "Health and Wellness", "[leisure=spa]"),
        category("Tanning studio", "tanning_studio", "Health and Wellness", "[shop=tanning]"),
        category("Wellness center", "wellness_center", "Health and Wellness", "[healthcare=centre]"),
        category("Yoga studio", "yoga_studio", "Health and Wellness", "[leisure=fitness_centre]"),
        category("Apartment building", "apartment_building", "Housing", "[building=apartments]"),
        category("Apartment complex", "apartment_complex", "Housing", "[building=apartments]"),
        category("Condominium complex", "condominium_complex", "Housing", "[building=residential]"),
        category("Housing complex", "housing_complex", "Housing", "[landuse=residential]"),
        category("Bed and breakfast", "bed_and_breakfast", "Lodging", "[tourism=guest_house]"),
        category("Campground", "campground", "Lodging", "[tourism=camp_site]"),
        category("Camping cabin", "camping_cabin", "Lodging", "[tourism=camp_site]"),
        category("Cottage", "cottage", "Lodging", "[tourism=guest_house]"),
        category("Extended stay hotel", "extended_stay_hotel", "Lodging", "[tourism=hotel]"),
        category("Farmstay", "farmstay", "Lodging", "[tourism=guest_house]"),
        category("Guest house", "guest_house", "Lodging", "[tourism=guest_house]"),
        category("Hostel", "hostel", "Lodging", "[tourism=hostel]"),
        category("Hotel", "hotel", "Lodging", "[tourism=hotel]"),
        category("Inn", "inn", "Lodging", "[tourism=hotel]"),
        category("Japanese inn", "japanese_inn", "Lodging", "[tourism=hotel]"),
        category("Lodging", "lodging", "Lodging", "[tourism=hotel]"),
        category("Mobile home park", "mobile_home_park", "Lodging", "[tourism=camp_site]"),
        category("Motel", "motel", "Lodging", "[tourism=motel]"),
        category("Resort hotel", "resort_hotel", "Lodging", "[tourism=resort]"),
        category("RV park", "rv_park", "Lodging", "[tourism=caravan_site]"),
        category("Beach", "beach", "Natural Features", "[natural=beach]"),
        category("Lake", "lake", "Natural Features", "[natural=water]"),
        category("Mountain peak", "mountain_peak", "Natural Features", "[natural=peak]"),
        category("Nature preserve", "nature_preserve", "Natural Features", "[leisure=nature_reserve]"),
        category("River", "river", "Natural Features", "[waterway=river]"),
        category("Scenic spot", "scenic_spot", "Natural Features", "[tourism=viewpoint]"),
        category("Woods", "woods", "Natural Features", "[natural=wood]"),
        category("Buddhist temple", "buddhist_temple", "Places of Worship", "[amenity=place_of_worship]"),
        category("Church", "church", "Places of Worship", "[amenity=place_of_worship]"),
        category("Hindu temple", "hindu_temple", "Places of Worship", "[amenity=place_of_worship]"),
        category("Mosque", "mosque", "Places of Worship", "[amenity=place_of_worship]"),
        category("Place of worship", "place_of_worship", "Places of Worship", "[amenity=place_of_worship]"),
        category("Shinto shrine", "shinto_shrine", "Places of Worship", "[amenity=place_of_worship]"),
        category("Synagogue", "synagogue", "Places of Worship", "[amenity=place_of_worship]"),
        category("Barber shop", "barber_shop", "Services", "[shop=hairdresser]"),
        category("Beauty salon", "beauty_salon", "Services", "[shop=beauty]"),
        category("Body art service", "body_art_service", "Services", "[shop=tattoo]"),
        category("Catering service", "catering_service", "Services", "[craft=caterer]"),
        category("Cemetery", "cemetery", "Services", "[landuse=cemetery]"),
        category("Child care agency", "child_care_agency", "Services", "[amenity=childcare]"),
        category("Consultant", "consultant", "Services", "[office=consulting]"),
        category("Courier service", "courier_service", "Services", "[office=courier]"),
        category("Electrician", "electrician", "Services", "[craft=electrician]"),
        category("Florist", "florist", "Services", "[shop=florist]"),
        category("Food delivery", "food_delivery", "Services", "[amenity=restaurant]"),
        category("Funeral home", "funeral_home", "Services", "[shop=funeral_directors]"),
        category("Hair salon", "hair_salon", "Services", "[shop=hairdresser]"),
        category("Insurance agency", "insurance_agency", "Services", "[office=insurance]"),
        category("Laundry", "laundry", "Services", "[shop=laundry]"),
        category("Lawyer", "lawyer", "Services", "[office=lawyer]"),
        category("Locksmith", "locksmith", "Services", "[craft=locksmith]"),
        category("Moving company", "moving_company", "Services", "[office=moving_company]"),
        category("Nail salon", "nail_salon", "Services", "[shop=beauty]"),
        category("Painter", "painter", "Services", "[craft=painter]"),
        category("Photo studio", "photography_studio", "Services", "[shop=photo_studio]"),
        category("Plumber", "plumber", "Services", "[craft=plumber]"),
        category("Real estate agency", "real_estate_agency", "Services", "[office=estate_agent]"),
        category("Storage", "storage", "Services", "[amenity=storage_rental]"),
        category("Tailor", "tailor", "Services", "[shop=tailor]"),
        category("Travel agency", "travel_agency", "Services", "[shop=travel_agency]"),
        category("Veterinary care", "veterinary_care", "Services", "[amenity=veterinary]"),
        category("Asian grocery store", "asian_grocery_store", "Shopping", "[shop=supermarket]"),
        category("Auto parts store", "auto_parts_store", "Shopping", "[shop=car_parts]"),
        category("Bicycle store", "bicycle_store", "Shopping", "[shop=bicycle]"),
        category("Book store", "book_store", "Shopping", "[shop=books]"),
        category("Building materials store", "building_materials_store", "Shopping", "[shop=doityourself]"),
        category("Butcher shop", "butcher_shop", "Shopping", "[shop=butcher]"),
        category("Cell phone store", "cell_phone_store", "Shopping", "[shop=mobile_phone]"),
        category("Clothing store", "clothing_store", "Shopping", "[shop=clothes]"),
        category("Convenience store", "convenience_store", "Shopping", "[shop=convenience]"),
        category("Cosmetics store", "cosmetics_store", "Shopping", "[shop=cosmetics]"),
        category("Department store", "department_store", "Shopping", "[shop=department_store]"),
        category("Discount store", "discount_store", "Shopping", "[shop=variety_store]"),
        category("Electronics store", "electronics_store", "Shopping", "[shop=electronics]"),
        category("Farmers market", "farmers_market", "Shopping", "[amenity=marketplace]"),
        category("Furniture store", "furniture_store", "Shopping", "[shop=furniture]"),
        category("Garden center", "garden_center", "Shopping", "[shop=garden_centre]"),
        category("Gift shop", "gift_shop", "Shopping", "[shop=gift]"),
        category("Grocery store", "grocery_store", "Shopping", "[shop=supermarket]"),
        category("Hardware store", "hardware_store", "Shopping", "[shop=hardware]"),
        category("Health food store", "health_food_store", "Shopping", "[shop=health_food]"),
        category("Home goods store", "home_goods_store", "Shopping", "[shop=houseware]"),
        category("Jewelry store", "jewelry_store", "Shopping", "[shop=jewelry]"),
        category("Liquor store", "liquor_store", "Shopping", "[shop=alcohol]"),
        category("Pet store", "pet_store", "Shopping", "[shop=pet]"),
        category("Shoe store", "shoe_store", "Shopping", "[shop=shoes]"),
        category("Shopping mall", "shopping_mall", "Shopping", "[shop=mall]"),
        category("Sporting goods store", "sporting_goods_store", "Shopping", "[shop=sports]"),
        category("Store", "store", "Shopping", "[shop]"),
        category("Supermarket", "supermarket", "Shopping", "[shop=supermarket]"),
        category("Tea store", "tea_store", "Shopping", "[shop=tea]"),
        category("Toy store", "toy_store", "Shopping", "[shop=toys]"),
        category("Warehouse store", "warehouse_store", "Shopping", "[shop=warehouse]"),
        category("Arena", "arena", "Sports", "[leisure=stadium]"),
        category("Athletic field", "athletic_field", "Sports", "[leisure=pitch]"),
        category("Golf course", "golf_course", "Sports", "[leisure=golf_course]"),
        category("Gym", "gym", "Sports", "[leisure=fitness_centre]"),
        category("Ice skating rink", "ice_skating_rink", "Sports", "[leisure=ice_rink]"),
        category("Playground", "playground", "Sports", "[leisure=playground]"),
        category("Race course", "race_course", "Sports", "[leisure=racecourse]"),
        category("Ski resort", "ski_resort", "Sports", "[tourism=resort]"),
        category("Sports club", "sports_club", "Sports", "[leisure=sports_centre]"),
        category("Sports complex", "sports_complex", "Sports", "[leisure=sports_centre]"),
        category("Stadium", "stadium", "Sports", "[leisure=stadium]"),
        category("Swimming pool", "swimming_pool", "Sports", "[leisure=swimming_pool]"),
        category("Tennis court", "tennis_court", "Sports", "[leisure=pitch]"),
        category("Airport", "airport", "Transportation", "[aeroway=aerodrome]"),
        category("Airstrip", "airstrip", "Transportation", "[aeroway=aerodrome]"),
        category("Bike sharing station", "bike_sharing_station", "Transportation", "[amenity=bicycle_rental]"),
        category("Bus station", "bus_station", "Transportation", "[amenity=bus_station]"),
        category("Bus stop", "bus_stop", "Transportation", "[highway=bus_stop]"),
        category("Ferry terminal", "ferry_terminal", "Transportation", "[amenity=ferry_terminal]"),
        category("Heliport", "heliport", "Transportation", "[aeroway=helipad]"),
        category("International airport", "international_airport", "Transportation", "[aeroway=aerodrome]"),
        category("Light rail station", "light_rail_station", "Transportation", "[railway=station]"),
        category("Park and ride", "park_and_ride", "Transportation", "[amenity=parking]"),
        category("Subway station", "subway_station", "Transportation", "[railway=station]"),
        category("Taxi stand", "taxi_stand", "Transportation", "[amenity=taxi]"),
        category("Train station", "train_station", "Transportation", "[railway=station]"),
        category("Transit station", "transit_station", "Transportation", "[public_transport=station]"),
        category("Truck stop", "truck_stop", "Transportation", "[amenity=rest_area]")
    ).sortedBy { it.name.lowercase(Locale.ROOT) }

    private val byName = all.associateBy { it.name }

    fun categoryForQuickChip(chip: String): NearbyCategory? = when (chip) {
        "Coffee" -> byName["Cafe"]
        "Food" -> category("Food", "restaurant", "Food and Drink", "[amenity=restaurant]")
        "Gas" -> byName["Gas station"]
        else -> null
    }

    fun find(name: String): NearbyCategory? = byName[name] ?: categoryForQuickChip(name)

    @DrawableRes
    fun iconFor(name: String): Int = find(name)?.iconRes ?: R.drawable.ic_nearby_more

    fun groups(): List<Pair<String, List<NearbyCategory>>> = all.groupBy { it.group }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        .map { it.key to it.value }
}
