package yan.goodshare.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import yan.goodshare.dto.ConversationDTO;
import yan.goodshare.entity.Message;
import yan.goodshare.entity.User;
import yan.goodshare.service.MessageService;
import yan.goodshare.user.UserService;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageController(MessageService messageService, UserService userService, SimpMessagingTemplate messagingTemplate) {
        this.messageService = messageService;
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat")
    public void processMessage(@Payload Map<String, Object> payload, Principal principal) {
        // Principal.getName() returns the username (email/username depending on config)
        User sender = userService.findByUsername(principal.getName()).orElseThrow();
        Long receiverId = Long.valueOf(payload.get("receiverId").toString());
        String content = (String) payload.get("content");

        Message savedMsg = messageService.sendMessage(sender.getId(), receiverId, content);
        savedMsg.setSender(sender); // Populate sender info for frontend

        // Send to receiver
        messagingTemplate.convertAndSendToUser(
                String.valueOf(receiverId), // UserID as username for simplicity in this context, but we need to ensure Principal matches
                "/queue/messages",
                savedMsg
        );
        
        // Send back to sender (so they see it immediately if they have multiple tabs or for confirmation)
        messagingTemplate.convertAndSendToUser(
                String.valueOf(sender.getId()),
                "/queue/messages",
                savedMsg
        );
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDTO>> getConversations(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(messageService.getConversations(user.getId()));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<Message>> getMessages(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long userId) {
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(messageService.getMessages(user.getId(), userId));
    }

    @PostMapping
    public ResponseEntity<Message> sendMessage(@AuthenticationPrincipal UserDetails userDetails, @RequestBody Map<String, Object> payload) {
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        Long receiverId = Long.valueOf(payload.get("receiverId").toString());
        String content = (String) payload.get("content");
        
        Message savedMsg = messageService.sendMessage(user.getId(), receiverId, content);
        savedMsg.setSender(user); // Populate sender info for frontend

        // Broadcast via WebSocket
        messagingTemplate.convertAndSendToUser(
                String.valueOf(receiverId),
                "/queue/messages",
                savedMsg
        );
        messagingTemplate.convertAndSendToUser(
                String.valueOf(user.getId()),
                "/queue/messages",
                savedMsg
        );

        return ResponseEntity.ok(savedMsg);
    }

    @PutMapping("/{userId}/read")
    public ResponseEntity<Void> markAsRead(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long userId) {
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        messageService.markAsRead(user.getId(), userId);
        return ResponseEntity.ok().build();
    }
}
