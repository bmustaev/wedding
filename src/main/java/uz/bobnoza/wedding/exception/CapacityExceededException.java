package uz.bobnoza.wedding.exception;

/** A seating-table assignment would exceed that table's capacity. */
public class CapacityExceededException extends RuntimeException {
    public CapacityExceededException(String message) {
        super(message);
    }
}
