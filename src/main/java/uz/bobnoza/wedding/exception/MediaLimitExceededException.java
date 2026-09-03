package uz.bobnoza.wedding.exception;

/** A guest has reached the 15-photo or 4-video upload ceiling. */
public class MediaLimitExceededException extends RuntimeException {
    public MediaLimitExceededException(String message) {
        super(message);
    }
}
