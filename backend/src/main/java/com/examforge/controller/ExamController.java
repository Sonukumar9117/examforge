package com.examforge.controller;

import com.examforge.dto.response.ExamPublicResponse;
import com.examforge.dto.response.ExamSummaryResponse;
import com.examforge.dto.response.PageResponse;
import com.examforge.entity.enums.Difficulty;
import com.examforge.entity.enums.ExamType;
import com.examforge.service.ExamBrowseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/** Signed-in students browse published exams. Questions are only served once an attempt starts (phase 3). */
@RestController
@RequestMapping("/api/v1/exams")
@Tag(name = "Exams")
public class ExamController {
    private final ExamBrowseService browse;

    public ExamController(ExamBrowseService browse) {
        this.browse = browse;
    }

    @GetMapping
    public PageResponse<ExamSummaryResponse> list(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) String category,
                                                  @RequestParam(required = false) Long subjectId,
                                                  @RequestParam(required = false) Difficulty difficulty,
                                                  @RequestParam(required = false) ExamType type,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "12") int size) {
        return browse.browse(q, category, subjectId, difficulty, type, page, size);
    }

    @GetMapping("/{slug}")
    public ExamPublicResponse get(@PathVariable String slug) {
        return browse.get(slug);
    }
}
