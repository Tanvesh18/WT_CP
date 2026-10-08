package com.tripshield.backend.security;
import com.tripshield.backend.model.Trip;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class Access {
 private final HttpServletRequest request;
 private final Map<String,Long> tokens = new ConcurrentHashMap<>();
 private final SecureRandom random = new SecureRandom();
 private final UserRepository users;
 public Access(HttpServletRequest request, UserRepository users) { this.request=request; this.users=users; }
 public String issue(AppUser user) { byte[] bytes=new byte[32]; random.nextBytes(bytes); String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); tokens.put(token,user.id); return token; }
 public AppUser current() { String header=request.getHeader("Authorization"); if(header==null || !header.startsWith("Bearer ")) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in required"); Long id=tokens.get(header.substring(7)); if(id==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Session expired"); return users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Account unavailable")); }
 public void logout() { String header=request.getHeader("Authorization"); if(header!=null && header.startsWith("Bearer ")) tokens.remove(header.substring(7)); }
 public void coordinator() { if(!"COORDINATOR".equals(current().role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Coordinator access required"); }
 public boolean canView(Trip trip) { AppUser user=current(); return "COORDINATOR".equals(user.role) || user.email.equalsIgnoreCase(trip.travelerEmail == null ? "" : trip.travelerEmail); }
 public void view(Trip trip) { if(!canView(trip)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Trip access denied"); if("CANCELLED".equals(trip.lifecycle)) { /* history remains visible */ } }
}
