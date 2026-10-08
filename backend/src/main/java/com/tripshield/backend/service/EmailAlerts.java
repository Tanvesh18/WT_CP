package com.tripshield.backend.service;
import com.tripshield.backend.model.Trip;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
@Service
public class EmailAlerts {
 private final ObjectProvider<JavaMailSender> sender;
 @Value("${tripshield.alert.to:}") private String to;
 @Value("${spring.mail.username:}") private String from;
 public EmailAlerts(ObjectProvider<JavaMailSender> sender){this.sender=sender;}
 public void helpRequested(Trip trip){
  if(to.isBlank() || from.isBlank() || sender.getIfAvailable()==null) return;
  SimpleMailMessage message=new SimpleMailMessage(); message.setTo(to); message.setFrom(from);
  message.setSubject("TripShield help request: trip #"+trip.id);
  message.setText("Traveler: "+trip.traveler+"\nRoute: "+trip.origin+" to "+trip.destination+"\nNote: "+(trip.checkInNote==null?"":trip.checkInNote));
  sender.getObject().send(message);
 }
}
