package com.codemastery.modules.assessment.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.modules.assessment.api.dto.QuizRequest.PublicationRequest;
import com.codemastery.modules.assessment.api.dto.QuizRequest.WriteQuizRequest;
import com.codemastery.modules.assessment.api.dto.QuizResponses.OwnedQuizResponse;
import com.codemastery.modules.assessment.api.dto.QuizResponses.QuizSummaryResponse;
import com.codemastery.modules.assessment.application.service.QuizService;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses/{courseId}")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = Objects.requireNonNull(quizService);
    }

    @PostMapping("/quizzes")
    public ResponseEntity<OwnedQuizResponse> createQuiz(
            @PathVariable UUID courseId,
            @Valid @RequestBody WriteQuizRequest request
    ) {
        OwnedQuizResponse created = quizService.createQuiz(courseId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(created);
    }

    @GetMapping("/quizzes")
    public ResponseEntity<PageResponse<QuizSummaryResponse>> listLearnerQuizzes(
            @PathVariable UUID courseId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        PageResponse<QuizSummaryResponse> response = quizService.listLearnerQuizzes(courseId, page, size);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping("/owned-quizzes")
    public ResponseEntity<PageResponse<OwnedQuizResponse>> listOwnedQuizzes(
            @PathVariable UUID courseId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        PageResponse<OwnedQuizResponse> response = quizService.listOwnedQuizzes(courseId, page, size);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping("/quizzes/{quizId}")
    public ResponseEntity<OwnedQuizResponse> getOwnedQuiz(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId
    ) {
        OwnedQuizResponse response = quizService.getOwnedQuiz(courseId, quizId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PutMapping("/quizzes/{quizId}")
    public ResponseEntity<OwnedQuizResponse> replaceUnusedHiddenQuiz(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @Valid @RequestBody WriteQuizRequest request
    ) {
        OwnedQuizResponse response = quizService.replaceUnusedHiddenQuiz(courseId, quizId, request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @DeleteMapping("/quizzes/{quizId}")
    public ResponseEntity<OwnedQuizResponse> deleteUnusedHiddenQuiz(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId
    ) {
        OwnedQuizResponse response = quizService.deleteUnusedHiddenQuiz(courseId, quizId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PutMapping("/quizzes/{quizId}/publication")
    public ResponseEntity<OwnedQuizResponse> setQuizPublication(
            @PathVariable UUID courseId,
            @PathVariable UUID quizId,
            @Valid @RequestBody PublicationRequest request
    ) {
        OwnedQuizResponse response = quizService.setQuizPublication(courseId, quizId, request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}