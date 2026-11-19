package com.codemastery.modules.assessment.api.controller;

import com.codemastery.common.api.PageResponse;
import com.codemastery.modules.assessment.api.dto.QuestionRequest;
import com.codemastery.modules.assessment.api.dto.QuestionResponse;
import com.codemastery.modules.assessment.application.service.QuestionBankService;
import jakarta.validation.Valid;
import java.util.UUID;
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
@RequestMapping("/api/courses/{courseId}/questions")
public class QuestionBankController {

    private final QuestionBankService questionBankService;

    public QuestionBankController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @PostMapping
    public ResponseEntity<QuestionResponse> createQuestion(
            @PathVariable UUID courseId,
            @Valid @RequestBody QuestionRequest request
    ) {
        QuestionResponse created = questionBankService.createQuestion(courseId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<PageResponse<QuestionResponse>> listQuestions(
            @PathVariable UUID courseId,
            @RequestParam UUID skillId,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(questionBankService.listQuestions(courseId, skillId, difficulty, page, size));
    }

    @GetMapping("/{questionId}")
    public ResponseEntity<QuestionResponse> getQuestion(
            @PathVariable UUID courseId,
            @PathVariable UUID questionId
    ) {
        return ResponseEntity.ok(questionBankService.getQuestion(courseId, questionId));
    }

    @PutMapping("/{questionId}")
    public ResponseEntity<QuestionResponse> replaceQuestion(
            @PathVariable UUID courseId,
            @PathVariable UUID questionId,
            @Valid @RequestBody QuestionRequest request
    ) {
        return ResponseEntity.ok(questionBankService.replaceQuestion(courseId, questionId, request));
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<QuestionResponse> deleteQuestion(
            @PathVariable UUID courseId,
            @PathVariable UUID questionId
    ) {
        return ResponseEntity.ok(questionBankService.deleteQuestion(courseId, questionId));
    }
}