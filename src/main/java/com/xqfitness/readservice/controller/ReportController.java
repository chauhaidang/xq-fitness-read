package com.xqfitness.readservice.controller;

import com.xqfitness.readservice.dto.WeeklyReportDTO;
import com.xqfitness.readservice.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/routines/{routineId}/weekly-report")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<WeeklyReportDTO> getWeeklyReport(
            @PathVariable Integer routineId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate
    ) {
        try {
            log.info("Received request for weekly report: routineId={}, weekStartDate={}", routineId, weekStartDate);
            WeeklyReportDTO report = reportService.getWeeklyReport(routineId, weekStartDate);
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for weekly report: routineId={}, error={}", routineId, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error generating weekly report: routineId={}", routineId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
