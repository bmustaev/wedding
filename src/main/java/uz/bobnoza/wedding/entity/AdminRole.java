package uz.bobnoza.wedding.entity;

public enum AdminRole {
    SUPER_ADMIN,
    ADMIN,
    /** Works one hall's playlist (dj.html) and nothing else — no side, always a hall. */
    DJ,
    /** Pays out guests' quest ducats at one hall's bank table (bank.html) and nothing else — no side, always a hall. */
    BANKER
}
