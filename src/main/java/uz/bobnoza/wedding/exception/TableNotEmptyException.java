package uz.bobnoza.wedding.exception;

/** A table can't be deleted while guests are still seated at it. */
public class TableNotEmptyException extends RuntimeException {
    public TableNotEmptyException(String message) {
        super(message);
    }
}
