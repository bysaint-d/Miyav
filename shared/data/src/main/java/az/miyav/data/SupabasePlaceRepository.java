package az.miyav.data;

import az.miyav.data.http.HttpExchange;
import az.miyav.domain.place.Place;
import az.miyav.domain.repository.PlaceRepository;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** {@code public.places}, read with the anonymous role. */
public final class SupabasePlaceRepository implements PlaceRepository {
    private static final Type ROWS = new TypeToken<List<Row>>() {
    }.getType();
    private final SupabaseClient client;

    public SupabasePlaceRepository(SupabaseClient client) {
        this.client = client;
    }

    @Override
    public List<Place> list(Place.Kind kind) {
        HttpExchange response = client.send("GET", "/rest/v1/places?select=id,kind,name,address,phone,opening_hours,latitude,longitude"
                + "&active=eq.true&kind=eq." + kind.databaseValue() + "&limit=1000", null, null);
        if (response.status() / 100 != 2) {
            throw PostgrestErrors.from(response);
        }
        List<Row> rows = client.gson().fromJson(response.body(), ROWS);
        List<Place> places = new ArrayList<>();
        for (Row row : rows == null ? List.<Row>of() : rows) {
            places.add(new Place(UUID.fromString(row.id), Place.Kind.fromDatabase(row.kind), row.name, row.address,
                    row.phone, row.opening_hours, row.latitude, row.longitude));
        }
        return places;
    }

    private static final class Row {
        String id;
        String kind;
        String name;
        String address;
        String phone;
        String opening_hours;
        double latitude;
        double longitude;
    }
}
