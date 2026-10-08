package com.documents.controller;

import com.documents.api.exception.AccessDeniedException;
import com.documents.api.exception.DocumentNotFoundException;
import com.documents.api.exception.OwnerNotAllowedException;
import com.documents.api.exception.OwnerVerificationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Status codes for document-core exceptions. A record the caller may not see is 404 (same as a
 * missing one); an owner the caller may not list or attach to is 403.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DocumentExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(DocumentNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Document not found");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> forbidden(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage() == null ? "Not allowed" : ex.getMessage());
    }

    @ExceptionHandler({OwnerNotAllowedException.class, OwnerVerificationException.class})
    public ResponseEntity<ProblemDetail> badOwner(RuntimeException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage() == null ? "Invalid owner" : ex.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(status.getReasonPhrase());
        return ResponseEntity.status(status).body(body);
    }
}
