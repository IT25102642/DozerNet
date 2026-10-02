package com.dozernet.common.exception;

/**
 * Thrown when a requested entity does not exist. Handled globally to show a
 * friendly 404 page.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String entity, Object id) {
        return new ResourceNotFoundException(entity + " not found (id=" + id + ")");
    }
}
