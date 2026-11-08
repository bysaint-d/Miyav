package az.miyav.domain.map;

public enum MapPointKind {
    LIVE_WALK("walk"),
    WALK_HELPER("helper"),
    ADOPTION("adoption");

    private final String wireValue;

    MapPointKind(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static MapPointKind fromWire(String value) {
        for (MapPointKind kind : values()) {
            if (kind.wireValue.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown map point kind: " + value);
    }
}
