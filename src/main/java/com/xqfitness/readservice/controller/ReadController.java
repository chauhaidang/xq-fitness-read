package com.xqfitness.readservice.controller;

import com.xqfitness.readservice.dto.*;
import com.xqfitness.readservice.service.ReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReadController {

    private final ReadService readService;

    @GetMapping("/muscle-groups")
    public ResponseEntity<List<MuscleGroupDTO>> getMuscleGroups() {
        return ResponseEntity.ok(readService.getAllMuscleGroups());
    }

    @GetMapping("/routines")
    public ResponseEntity<List<WorkoutRoutineDTO>> getRoutines(
        @RequestParam(required = false) Boolean isActive
    ) {
        return ResponseEntity.ok(readService.getAllRoutines(isActive));
    }

    @GetMapping("/routines/{routineId}")
    public ResponseEntity<WorkoutRoutineDetailDTO> getRoutineById(
        @PathVariable Long routineId
    ) {
        return readService.getRoutineById(routineId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/routines/{routineId}/days")
    public ResponseEntity<List<WorkoutDayDetailDTO>> getWorkoutDays(
        @PathVariable Long routineId
    ) {
        List<WorkoutDayDetailDTO> days = readService.getWorkoutDaysByRoutineId(routineId);
        if (days.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(days);
    }
}
