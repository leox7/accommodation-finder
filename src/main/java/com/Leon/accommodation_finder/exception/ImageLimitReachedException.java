package com.Leon.accommodation_finder.exception;

public class ImageLimitReachedException extends RuntimeException {
    public ImageLimitReachedException(int maxSecondaryImages) {
        super("A listing can have at most " + maxSecondaryImages + " secondary images");
    }
}
