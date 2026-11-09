package az.miyav.domain.pet;

import java.util.Objects;

public final class PetQuery {
    private final PetSpecies species;
    private final String city;
    private final String search;
    private final int limit;
    private final int offset;
    private final PetFilters filters;

    public PetQuery(PetSpecies species, String city, String search, int limit, int offset) {
        this(species, city, search, limit, offset, PetFilters.NONE);
    }

    public PetQuery(PetSpecies species, String city, String search, int limit, int offset, PetFilters filters) {
        this.species = species;
        this.city = city == null ? "" : city.trim();
        this.search = search == null ? "" : search.trim();
        this.limit = limit;
        this.offset = offset;
        this.filters = filters == null ? PetFilters.NONE : filters;
    }

    public static PetQuery firstPage() {
        return new PetQuery(null, "", "", 20, 0);
    }

    public PetSpecies species() {
        return species;
    }

    public String city() {
        return city;
    }

    public String search() {
        return search;
    }

    public int limit() {
        return limit;
    }

    public int offset() {
        return offset;
    }

    public PetFilters filters() {
        return filters;
    }

    public PetQuery nextPage() {
        return new PetQuery(species, city, search, limit, offset + limit, filters);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PetQuery query)) {
            return false;
        }
        return limit == query.limit
                && offset == query.offset
                && species == query.species
                && city.equals(query.city)
                && search.equals(query.search)
                && filters.equals(query.filters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(species, city, search, limit, offset, filters);
    }
}
