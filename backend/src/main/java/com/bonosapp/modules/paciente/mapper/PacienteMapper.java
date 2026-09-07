package com.bonosapp.modules.paciente.mapper;

import com.bonosapp.modules.paciente.dto.PacienteResponse;
import com.bonosapp.modules.paciente.entity.Paciente;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PacienteMapper {

    PacienteResponse toResponse(Paciente paciente);
}
