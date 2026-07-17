package com.nutriapp.modules.paciente.mapper;

import com.nutriapp.modules.paciente.dto.PacienteResponse;
import com.nutriapp.modules.paciente.entity.Paciente;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PacienteMapper {

    PacienteResponse toResponse(Paciente paciente);
}
