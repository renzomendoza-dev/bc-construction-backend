package com.bcconstructionservices.equipment.exception;

public class InvalidCheckoutUserException extends RuntimeException {
    public InvalidCheckoutUserException(Long userId) {
        super("Cannot check out equipment to user id " + userId + ": user does not exist");
    }
}
