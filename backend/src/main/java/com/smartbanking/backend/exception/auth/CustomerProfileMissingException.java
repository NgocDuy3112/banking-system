package com.smartbanking.backend.exception.auth;

public class CustomerProfileMissingException extends RuntimeException {
  public CustomerProfileMissingException(String message) {
    super(message);
  }
}