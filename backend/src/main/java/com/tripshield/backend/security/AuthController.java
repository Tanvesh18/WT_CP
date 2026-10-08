package com.tripshield.backend.security;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;
@RestController @RequestMapping("/api/auth")
public class AuthController {
 private final UserRepository users; private final Access access;
 private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
 @Value("${tripshield.coordinator.email:}") private String coordinatorEmail;
 @Value("${tripshield.coordinator.password:}") private String coordinatorPassword;
 public AuthController(UserRepository users,Access access){this.users=users;this.access=access;}
 public record Credentials(@NotBlank @Email String email,@NotBlank String password) {}
 public record Registration(@NotBlank String name,@NotBlank @Email String email,@NotBlank String password) {}
 public record Session(String token,String name,String email,String role) {}
 @PostMapping("/register") public Session register(@Valid @RequestBody Registration input){
  if(input.password().length()<8) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password needs at least 8 characters");
  String email=input.email().trim().toLowerCase(Locale.ROOT);
  if(email.equalsIgnoreCase(coordinatorEmail) || users.findByEmailIgnoreCase(email).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered");
  AppUser user=new AppUser(); user.name=input.name().trim(); user.email=email; user.passwordHash=encoder.encode(input.password()); user.role="TRAVELER";
  users.save(user); return session(user);
 }
 @PostMapping("/login") public Session login(@Valid @RequestBody Credentials input){
  String email=input.email().trim().toLowerCase(Locale.ROOT);
  AppUser user=users.findByEmailIgnoreCase(email).orElse(null);
  if(user==null && !coordinatorEmail.isBlank() && !coordinatorPassword.isBlank() && email.equalsIgnoreCase(coordinatorEmail) && input.password().equals(coordinatorPassword)) {
   user=new AppUser(); user.name="Coordinator"; user.email=email; user.passwordHash=encoder.encode(coordinatorPassword); user.role="COORDINATOR"; user=users.save(user);
  }
  if(user==null || !encoder.matches(input.password(),user.passwordHash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
  return session(user);
 }
 @GetMapping("/me") public Session me(){AppUser u=access.current();return new Session("",u.name,u.email,u.role);}
 @PostMapping("/logout") public void logout(){access.logout();}
 private Session session(AppUser user){return new Session(access.issue(user),user.name,user.email,user.role);}
}
