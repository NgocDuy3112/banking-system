package com.smartbanking.backend.exception;

import com.smartbanking.backend.exception.account.*;
import com.smartbanking.backend.exception.auth.*;

import com.smartbanking.backend.exception.kyc.EkycUploadException;
import com.smartbanking.backend.exception.kyc.InvalidEkycAssetException;
import com.smartbanking.backend.exception.kyc.EkycNotApprovedException;
import com.smartbanking.backend.exception.otp.OtpExpiredException;
import com.smartbanking.backend.exception.otp.OtpInvalidException;
import com.smartbanking.backend.exception.otp.OtpLockedException;
import com.smartbanking.backend.exception.transaction.CurrencyMismatchException;
import com.smartbanking.backend.exception.transaction.SelfTransferException;
import com.smartbanking.backend.exception.transaction.TransactionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(String code, String message, Instant timestamp) {
        public static ApiError of(String code, String message) {
            return new ApiError(code, message, Instant.now());
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        logger.error("Unhandled exception", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request body");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("VALIDATION_FAILED", message));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiError> handleAccountNotFound(AccountNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("ACCOUNT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ApiError> handleAccountNotActive(AccountNotActiveException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of("ACCOUNT_NOT_ACTIVE", ex.getMessage()));
    }

    @ExceptionHandler(AccountAlreadyLockedException.class)
    public ResponseEntity<ApiError> handleAccountAlreadyLocked(AccountAlreadyLockedException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of("ACCOUNT_ALREADY_LOCKED", ex.getMessage()));
    }

    @ExceptionHandler(AccountAlreadyActiveException.class)
    public ResponseEntity<ApiError> handleAccountAlreadyActive(AccountAlreadyActiveException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of("ACCOUNT_ALREADY_ACTIVE", ex.getMessage()));
    }

    @ExceptionHandler(AccountClosedException.class)
    public ResponseEntity<ApiError> handleAccountClosed(AccountClosedException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of("ACCOUNT_CLOSED", ex.getMessage()));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> handleInsufficientFunds(InsufficientFundsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ApiError.of("INSUFFICIENT_FUNDS", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("INVALID_CREDENTIALS", ex.getMessage()));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiError> handleInvalidToken(InvalidTokenException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("INVALID_TOKEN", ex.getMessage()));
    }

    @ExceptionHandler(UserLockedException.class)
    public ResponseEntity<ApiError> handleUserLocked(UserLockedException ex) {
        return ResponseEntity
                .status(HttpStatus.LOCKED)
                .body(ApiError.of("USER_LOCKED", ex.getMessage()));
    }

    @ExceptionHandler(UserDisabledException.class)
    public ResponseEntity<ApiError> handleUserDisabled(UserDisabledException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("USER_DISABLED", ex.getMessage()));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailExists(EmailAlreadyExistsException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of("EMAIL_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("USER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(CustomerProfileMissingException.class)
    public ResponseEntity<ApiError> handleProfileMissing(CustomerProfileMissingException ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("CUSTOMER_PROFILE_MISSING", ex.getMessage()));
    }

    @ExceptionHandler(EkycUploadException.class)
    public ResponseEntity<ApiError> handleEkycUpload(EkycUploadException ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("EKYC_UPLOAD", ex.getMessage()));
    }

    @ExceptionHandler(InvalidEkycAssetException.class)
    public ResponseEntity<ApiError> handleInvalidEkycAsset(InvalidEkycAssetException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("INVALID_EKYC_ASSET", ex.getMessage()));
    }

    @ExceptionHandler(SelfTransferException.class)
    public ResponseEntity<ApiError> handleSelfTransfer(SelfTransferException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("SELF_TRANSFER", ex.getMessage()));
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ApiError> handleCurrencyMismatch(CurrencyMismatchException ex) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ApiError.of("CURRENCY_MISMATCH", ex.getMessage()));
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<ApiError> handleOtpExpired(OtpExpiredException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("OTP_EXPIRED", ex.getMessage()));
    }

    @ExceptionHandler(OtpInvalidException.class)
    public ResponseEntity<ApiError> handleOtpInvalid(OtpInvalidException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("OTP_INVALID", ex.getMessage()));
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ApiError> handleTransactionNotFound(TransactionNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("TRANSACTION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String parameterName = ex.getName();
        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        String message = "Parameter '" + parameterName + "' has an invalid value '" + ex.getValue() + "'. Expected type '" + requiredType + "'";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("METHOD_ARGUMENT_TYPE_MISMATCH", message));
    }

    @ExceptionHandler(AccountNotEmptyException.class)
    public ResponseEntity<ApiError> handleAccountNotEmpty(AccountNotEmptyException ex) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ApiError.of("ACCOUNT_NOT_EMPTY", ex.getMessage()));
    }

    @ExceptionHandler(EkycNotApprovedException.class)
    public ResponseEntity<ApiError> handleKycNotApproved(EkycNotApprovedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("KYC_NOT_APPROVED", ex.getMessage()));
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleAccountAlreadyExists(AccountAlreadyExistsException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .header("Location", "/api/v1/accounts/" + ex.getAccountNumber())
                .body(ApiError.of("ACCOUNT_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(OtpLockedException.class)
    public ResponseEntity<ApiError> handleOtpLocked(OtpLockedException ex) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiError.of("OTP_LOCKED", ex.getMessage()));
    }
}