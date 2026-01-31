package com.xqfitness.readservice.service;

import com.xqfitness.readservice.dto.ExerciseDTO;
import com.xqfitness.readservice.dto.MuscleGroupDTO;
import com.xqfitness.readservice.dto.WorkoutDayDetailDTO;
import com.xqfitness.readservice.dto.WorkoutRoutineDTO;
import com.xqfitness.readservice.dto.WorkoutRoutineDetailDTO;
import com.xqfitness.readservice.entity.Exercise;
import com.xqfitness.readservice.entity.WorkoutDay;
import com.xqfitness.readservice.entity.WorkoutRoutine;
import com.xqfitness.readservice.repository.ExerciseRepository;
import com.xqfitness.readservice.repository.MuscleGroupRepository;
import com.xqfitness.readservice.repository.WorkoutDayRepository;
import com.xqfitness.readservice.repository.WorkoutRoutineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    private final ExerciseRepository exerciseRepository;

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

    public Optional<WorkoutRoutineDetailDTO> getRoutineById(Integer id) {
        return workoutRoutineRepository.findByIdWithDetails(id)
            .map(this::toRoutineDetailWithExercises);
    }

    public List<ExerciseDTO> getExercises(Integer workoutDayId, Integer muscleGroupId) {
        List<Exercise> exercises;
        if (muscleGroupId != null) {
            exercises = exerciseRepository.findByWorkoutDayIdAndMuscleGroupIdOrderById(workoutDayId, muscleGroupId);
        } else {
            exercises = exerciseRepository.findByWorkoutDayIdOrderById(workoutDayId);
        }
        return ExerciseDTO.fromEntities(exercises);
    }

    private WorkoutRoutineDetailDTO toRoutineDetailWithExercises(WorkoutRoutine entity) {
        List<WorkoutDayDetailDTO> days = new ArrayList<>();
        for (WorkoutDay day : entity.getWorkoutDays()) {
            List<Exercise> exercises = exerciseRepository.findByWorkoutDayIdOrderById(day.getId());
            days.add(WorkoutDayDetailDTO.fromEntity(day, exercises));
        }
        return new WorkoutRoutineDetailDTO(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getIsActive(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            days
        );
    }

    public List<WorkoutDayDetailDTO> getWorkoutDaysByRoutineId(Integer routineId) {
        return workoutDayRepository.findByRoutineIdWithSets(routineId).stream()
            .map(day -> {
                List<Exercise> exercises = exerciseRepository.findByWorkoutDayIdOrderById(day.getId());
                return WorkoutDayDetailDTO.fromEntity(day, exercises);
            })
            .collect(Collectors.toList());
    }
}
