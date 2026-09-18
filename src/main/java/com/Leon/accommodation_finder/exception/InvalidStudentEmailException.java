package com.Leon.accommodation_finder.exception;

public class InvalidStudentEmailException extends RuntimeException {
    public InvalidStudentEmailException(String email) {
        super("The email '" + email + "' is not a valid KCA University student email. "
                + "Student registration requires an email ending in @students.kcau.ac.ke");
    }
}