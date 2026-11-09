package az.miyav.domain.location;

import java.util.List;
import java.util.Map;

/** Districts and settlements members pick from, per supported city. */
public final class Districts {
    private static final Map<String, List<String>> BY_CITY = Map.of(
            "Bakı", List.of("Binəqədi", "Nərimanov", "Nəsimi", "Nizami", "Pirallahı", "Qaradağ", "Sabunçu",
                    "Səbail", "Suraxanı", "Xətai", "Xəzər", "Yasamal"),
            "Sumqayıt", List.of("Mərkəz", "Corat", "Hacı Zeynalabdin"),
            "Abşeron", List.of("Xırdalan", "Atyalı", "Ceyranbatan", "Digah", "Fatmayı", "Görədil", "Güzdək",
                    "Hökməli", "Masazır", "Mehdiabad", "Məmmədli", "Müşviqabad", "Novxanı", "Pirəkəşkül",
                    "Qobu", "Saray", "Zağulba"));

    private Districts() {
    }

    /** The districts of a city in display order; empty for an unknown city. */
    public static List<String> of(String city) {
        return city == null ? List.of() : BY_CITY.getOrDefault(city, List.of());
    }

    public static boolean belongsTo(String city, String district) {
        return district == null || district.isEmpty() || of(city).contains(district);
    }
}
