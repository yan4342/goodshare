package yan.goodshare.dto;

import lombok.Data;
import java.time.LocalDateTime;

// 会话DTO·用于展示用户与当前登录用户的会话信息
@Data
public class ConversationDTO {
    private Long userId;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String lastMessageContent;
    private LocalDateTime lastMessageTime;
    private Integer unreadCount;
}
