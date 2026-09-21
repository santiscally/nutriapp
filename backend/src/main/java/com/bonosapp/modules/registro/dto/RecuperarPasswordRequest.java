package com.bonosapp.modules.registro.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RecuperarPasswordRequest(@NotBlank @Email String email) {}
