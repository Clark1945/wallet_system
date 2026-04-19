package org.side_project.wallet_system.auth.objects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "{error.password.required}")
    @Size(min = 6, message = "{error.password.too.short}")
    private String password;
}
