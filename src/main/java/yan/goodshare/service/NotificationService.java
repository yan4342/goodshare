package yan.goodshare.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import yan.goodshare.entity.Notification;
import yan.goodshare.mapper.NotificationMapper;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Service
public class NotificationService extends ServiceImpl<NotificationMapper, Notification> {

    @Autowired
    private NotificationMapper notificationMapper;

    public void createNotification(Long recipientId, Long senderId, String type, Long relatedId) {
        if (recipientId.equals(senderId)) {
            return; // Don't notify self
        }
        
        Notification notification = new Notification();
        notification.setRecipientId(recipientId);
        notification.setSenderId(senderId);
        notification.setType(type);
        notification.setRelatedId(relatedId);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        
        save(notification);
    }

    public IPage<Notification> getUserNotifications(Long userId, int page, int size, String type) {
        Page<Notification> pageParam = new Page<>(page, size);
        return notificationMapper.selectNotificationsWithDetails(pageParam, userId, type);
    }

    public void markAsRead(Long notificationId) {
        Notification notification = getById(notificationId);
        if (notification != null) {
            notification.setIsRead(true);
            updateById(notification);
        }
    }
}
