package com.jjj.jjj.global.exception;

import com.jjj.jjj.global.exception.dto.ErrorDTO;
import com.jjj.jjj.global.exception.dto.FieldDTO;
import com.jjj.jjj.global.exception.dto.FieldErrorDTO;
import com.jjj.jjj.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<FieldErrorDTO<FieldDTO>>> handleValidException(MethodArgumentNotValidException e) {
        ErrorCode errorCode = ErrorCode.NOT_VALID_DTO_ERR;
        List<FieldDTO> details = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new FieldDTO(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                ))
                .toList();

        ApiResponse<FieldErrorDTO<FieldDTO>> response = ApiResponse.fail(
                new FieldErrorDTO<>(
                        errorCode.getStatusCode(),
                        errorCode.getErrorCode(),
                        errorCode.getErrorMessage(),
                        details,
                        Instant.now()
                )
        );
        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(response);
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ApiResponse<ErrorDTO>> handleDateTimeParseException(DateTimeParseException e) {
        ErrorCode errorCode = ErrorCode.NOT_VALID_DTO_ERR;

        ApiResponse<ErrorDTO> response = ApiResponse.fail(ErrorDTO.from(errorCode));
        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<ErrorDTO>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        ErrorCode errorCode = ErrorCode.NOT_VALID_DTO_ERR;

        ApiResponse<ErrorDTO> response = ApiResponse.fail(ErrorDTO.from(errorCode));
        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ErrorDTO>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        ErrorCode errorCode = ErrorCode.NOT_VALID_DTO_ERR;

        ApiResponse<ErrorDTO> response = ApiResponse.fail(ErrorDTO.from(errorCode));
        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(response);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<ErrorDTO>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        ApiResponse<ErrorDTO> response = ApiResponse.fail(ErrorDTO.from(errorCode));
        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<ErrorDTO>> handleNoResourceFoundException(
            NoResourceFoundException e
    ) {
        ErrorCode errorCode = ErrorCode.NOT_FOUND;

        ApiResponse<ErrorDTO> response = ApiResponse.fail(ErrorDTO.from(errorCode));
        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorDTO>> handleException(Exception e) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERR;
        ApiResponse<ErrorDTO> response = ApiResponse.fail(ErrorDTO.from(errorCode));
        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(response);
    }
}