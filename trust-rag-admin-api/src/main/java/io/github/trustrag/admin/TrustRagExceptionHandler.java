package io.github.trustrag.admin;

import io.github.trustrag.core.exception.InvalidKnowledgeStateException;
import io.github.trustrag.core.exception.InvalidRagRequestException;
import io.github.trustrag.core.exception.KnowledgeNotFoundException;
import io.github.trustrag.core.exception.TrustRagException;
import io.github.trustrag.document.DocumentImportTaskNotFoundException;
import io.github.trustrag.evaluation.EvaluationResourceConflictException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class TrustRagExceptionHandler {

    @ExceptionHandler({
            InvalidRagRequestException.class,
            InvalidKnowledgeStateException.class,
            IllegalArgumentException.class})
    ProblemDetail handleBadRequest(RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setTitle("TrustRAG request rejected");
        return detail;
    }

    @ExceptionHandler(KnowledgeNotFoundException.class)
    ProblemDetail handleNotFound(KnowledgeNotFoundException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        detail.setTitle("TrustRAG resource not found");
        return detail;
    }

    @ExceptionHandler(DocumentImportTaskNotFoundException.class)
    ProblemDetail handleDocumentTaskNotFound(DocumentImportTaskNotFoundException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        detail.setTitle("TrustRAG document task not found");
        return detail;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded document exceeds the server limit");
        detail.setTitle("TrustRAG document upload rejected");
        return detail;
    }

    @ExceptionHandler(EvaluationResourceConflictException.class)
    ProblemDetail handleEvaluationConflict(EvaluationResourceConflictException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        detail.setTitle("TrustRAG evaluation conflict");
        return detail;
    }

    @ExceptionHandler(TrustRagException.class)
    ProblemDetail handleInternalError(TrustRagException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "TrustRAG operation failed");
        detail.setTitle("TrustRAG internal error");
        return detail;
    }
}
