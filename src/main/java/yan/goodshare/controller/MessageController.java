package yan.goodshare.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import yan.goodshare.dto.ConversationDTO;
import yan.goodshare.entity.Message;
import yan.goodshare.entity.User;
import yan.goodshare.service.MessageService;
import yan.goodshare.user.UserService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final UserService userService;

    public MessageController(MessageService messageService, UserService userService) {
        this.messageService = messageService;
        this.userService = userService;
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
        
        return ResponseEntity.ok(messageService.sendMessage(user.getId(), receiverId, content));
    }

    @PutMapping("/{userId}/read")
    public ResponseEntity<Void> markAsRead(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long userId) {
        User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
        messageService.markAsRead(user.getId(), userId);
        return ResponseEntity.ok().build();
    }
}
