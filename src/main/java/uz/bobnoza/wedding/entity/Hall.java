package uz.bobnoza.wedding.entity;

import java.util.Set;

/**
 * Where a guest is invited to, and which hall a seating table stands in.
 * TASHKENT is the main celebration (head, bride and groom tables);
 * SAMARKAND is a second, groom-side-only celebration with its own
 * invitation (venue, date and time — see i18n.js on the frontend).
 */
public enum Hall {
    TASHKENT(Set.of(AdminSide.BRIDE, AdminSide.GROOM), 12),
    SAMARKAND(Set.of(AdminSide.GROOM), 14);

    private final Set<AdminSide> sides;
    private final int defaultTableCapacity;

    Hall(Set<AdminSide> sides, int defaultTableCapacity) {
        this.sides = sides;
        this.defaultTableCapacity = defaultTableCapacity;
    }

    /** Seats at a new table in this hall when the request doesn't say — mirrors the seeded tables in data.sql. */
    public int getDefaultTableCapacity() {
        return defaultTableCapacity;
    }

    /** Whether admins of this side may see this hall, invite guests to it, and have tables in it. */
    public boolean isOpenTo(AdminSide side) {
        return side != null && sides.contains(side);
    }
}
