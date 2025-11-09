package com.xqfitness.readservice.service;

import com.xqfitness.readservice.dto.MuscleGroupDTO;
import com.xqfitness.readservice.dto.WorkoutDayDetailDTO;
import com.xqfitness.readservice.dto.WorkoutRoutineDTO;
import com.xqfitness.readservice.dto.WorkoutRoutineDetailDTO;
import com.xqfitness.readservice.entity.WorkoutRoutine;
import com.xqfitness.readservice.repository.MuscleGroupRepository;
import com.xqfitness.readservice.repository.WorkoutDayRepository;
import com.xqfitness.readservice.repository.WorkoutRoutineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadService {

    private final MuscleGroupRepository muscleGroupRepository;
    private final WorkoutRoutineRepository workoutRoutineRepository;
    private final WorkoutDayRepository workoutDayRepository;

    public List<MuscleGroupDTO> getAllMuscleGroups() {
        return muscleGroupRepository.findAll().stream()
            .map(MuscleGroupDTO::fromEntity)
            .collect(Collectors.toList());
    }

    public List<WorkoutRoutineDTO> getAllRoutines(Boolean isActive) {
        List<WorkoutRoutine> routines;
        if (isActive != null) {
            routines = workoutRoutineRepository.findByIsActive(isActive);
        } else {
            routines = workoutRoutineRepository.findAll();
        }
        return routines.stream()
            .map(WorkoutRoutineDTO::fromEntity)
            .collect(Collectors.toList());
    }

    public Optional<WorkoutRoutineDetailDTO> getRoutineById(Long id) {
        return workoutRoutineRepository.findByIdWithDetails(id)
            .map(WorkoutRoutineDetailDTO::fromEntity);
    }

    public List<WorkoutDayDetailDTO> getWorkoutDaysByRoutineId(Long routineId) {
        return workoutDayRepository.findByRoutineIdWithSets(routineId).stream()
            .map(WorkoutDayDetailDTO::fromEntity)
            .collect(Collectors.toList());
    }
}
