package org.side_project.wallet_system.auth.objects;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {
    private String name;
    private int age;
    private String email;
    private String password;
}
