package com.todo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.todo.dto.response.ApiResponse;
import com.todo.dto.response.ReportListResponse;
import com.todo.dto.response.ReportResponse;
import com.todo.entity.User;
import com.todo.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Validated
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/generate")
    public ApiResponse<Void> generate(@RequestParam @NotBlank(message = "报表类型不能为空") String type,
                                      @AuthenticationPrincipal User user) {
        reportService.generateAll(user.getId(), type);
        return ApiResponse.ok(null);
    }

    @GetMapping
    public ApiResponse<IPage<ReportListResponse>> list(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User user) {
        return ApiResponse.ok(reportService.getReports(user.getId(), scope, type, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReportResponse> get(@PathVariable Long id,
                                           @AuthenticationPrincipal User user) {
        return ApiResponse.ok(reportService.getReport(id, user.getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal User user) {
        reportService.deleteReport(id, user.getId());
        return ApiResponse.ok(null);
    }
}
