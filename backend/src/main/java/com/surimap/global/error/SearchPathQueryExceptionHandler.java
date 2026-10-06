package com.surimap.global.error;

import com.surimap.api.controller.path.SearchPathBoardController;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// 신규 조회의 HTTP 역직렬화·Bean Validation 실패만 다룬다. BusinessException은 공통 처리한다.
@RestControllerAdvice(assignableTypes = SearchPathBoardController.class)
public class SearchPathQueryExceptionHandler {
  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<Map<String, String>> handleInvalidQuery() {
    return ResponseEntity.badRequest()
        .body(Map.of("error", ErrorCode.INVALID_SEARCH_PATH_QUERY.getError()));
  }
}
