package uz.bobnoza.wedding.service;

import uz.bobnoza.wedding.entity.Hall;
import uz.bobnoza.wedding.exception.ResourceNotFoundException;
import uz.bobnoza.wedding.security.AdminPrincipal;

/**
 * Turns a request's optional "hall" value into a {@link Hall} the caller may
 * use. Absent means the caller's own hall for a hall admin, else TASHKENT
 * (the main hall, so older clients keep working).
 * A hall the caller can't access is reported as 404, same as an unknown one —
 * the bride side shouldn't be able to tell the Samarkand hall exists at all.
 */
final class HallResolver {

    private HallResolver() {
    }

    static Hall resolve(AdminPrincipal caller, String requested) {
        if (requested == null || requested.isBlank()) {
            return caller.getHall() != null ? caller.getHall() : Hall.TASHKENT;
        }
        Hall hall;
        try {
            hall = Hall.valueOf(requested.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Hall not found: " + requested);
        }
        if (!caller.canAccessHall(hall)) {
            throw new ResourceNotFoundException("Hall not found: " + requested);
        }
        return hall;
    }
}
