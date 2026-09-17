package vpmcp.core;

/** The bytes on the wire are not an HTTP request this server can serve. */
public class MalformedHttpException extends Exception {

    public MalformedHttpException(String message) {
        super(message);
    }
}
