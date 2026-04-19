package org.side_project.wallet_system.auth.objects;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "{error.name.required}")
    private String name;

    @Min(value = 0,   message = "{error.age.invalid}")
    @Max(value = 150, message = "{error.age.invalid}")
    private int age;

    @NotBlank(message = "{error.email.required}")
    @Email(message = "{error.email.invalid}")
    private String email;

    @NotBlank(message = "{error.password.required}")
    @Size(min = 6, message = "{error.password.too.short}")
    private String password;
}
