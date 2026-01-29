package yan.goodshare.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import yan.goodshare.dto.ConversationDTO;
import yan.goodshare.entity.Message;
import yan.goodshare.entity.User;
import yan.goodshare.mapper.MessageMapper;
import yan.goodshare.mapper.UserMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;

    public MessageService(MessageMapper messageMapper, UserMapper userMapper) {
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
    }

    public Message sendMessage(Long senderId, Long receiverId, String content) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setIsRead(false);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }

    public List<Message> getMessages(Long currentUserId, Long otherUserId) {
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper
                .eq("sender_id", currentUserId).eq("receiver_id", otherUserId)
                .or()
                .eq("sender_id", otherUserId).eq("receiver_id", currentUserId)
        );
        queryWrapper.orderByAsc("created_at");
        return messageMapper.selectList(queryWrapper);
    }

    public void markAsRead(Long currentUserId, Long otherUserId) {
        // Mark messages sent by otherUser to currentUser as read
        Message updateMsg = new Message();
        updateMsg.setIsRead(true);
        
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("sender_id", otherUserId)
                   .eq("receiver_id", currentUserId)
                   .eq("is_read", false);
        
        messageMapper.update(updateMsg, queryWrapper);
    }

    public List<ConversationDTO> getConversations(Long currentUserId) {
        // Get all messages involving current user
        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("sender_id", currentUserId).or().eq("receiver_id", currentUserId);
        queryWrapper.orderByDesc("created_at");
        List<Message> allMessages = messageMapper.selectList(queryWrapper);

        Map<Long, List<Message>> userMessagesMap = new LinkedHashMap<>();
        
        for (Message msg : allMessages) {
            Long otherId = msg.getSenderId().equals(currentUserId) ? msg.getReceiverId() : msg.getSenderId();
            userMessagesMap.computeIfAbsent(otherId, k -> new ArrayList<>()).add(msg);
        }

        List<ConversationDTO> conversations = new ArrayList<>();
        Set<Long> userIdsToFetch = new HashSet<>();
        for (Long otherId : userMessagesMap.keySet()) {
            userIdsToFetch.add(otherId);
        }
        
        Map<Long, User> usersMap = new HashMap<>();
        if (!userIdsToFetch.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIdsToFetch);
            for (User u : users) {
                usersMap.put(u.getId(), u);
            }
        }
        
        for (Map.Entry<Long, List<Message>> entry : userMessagesMap.entrySet()) {
            Long otherUserId = entry.getKey();
            List<Message> msgs = entry.getValue();
            if (msgs.isEmpty()) continue;

            Message lastMsg = msgs.get(0); // Since we ordered by desc, first is latest
            
            // Count unread: messages where sender is otherUser and isRead is false
            int unread = 0;
            for (Message m : msgs) {
                if (m.getSenderId().equals(otherUserId) && !m.getIsRead()) {
                    unread++;
                }
            }

            User otherUser = usersMap.get(otherUserId);
            if (otherUser != null) {
                ConversationDTO dto = new ConversationDTO();
                dto.setUserId(otherUserId);
                dto.setUsername(otherUser.getUsername());
                dto.setNickname(otherUser.getNickname());
                dto.setAvatarUrl(otherUser.getAvatarUrl());
                dto.setLastMessageContent(lastMsg.getContent());
                dto.setLastMessageTime(lastMsg.getCreatedAt());
                dto.setUnreadCount(unread);
                conversations.add(dto);
            }
        }
        
        return conversations;
    }
}
