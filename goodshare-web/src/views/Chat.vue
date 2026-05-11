<template>
  <div class="chat-container">
    <div class="chat-layout">
        <div class="chat-sidebar">
            <div class="chat-header">
                <el-button link @click="router.back()" style="margin-right: 8px;">
                    <el-icon :size="20"><ArrowLeft /></el-icon>
                </el-button>
                <h3>消息列表</h3>
            </div>
            <div class="conversation-list" v-loading="loadingConversations">
                <div v-if="conversations.length === 0" class="empty-conversations">
                    <el-empty description="暂无消息" :image-size="60" />
                </div>
                <div 
                    v-for="conv in conversations" 
                    :key="conv.userId" 
                    class="conversation-item"
                    :class="{ active: currentChatUser?.id === conv.userId }"
                    @click="selectConversation(conv)"
                >
                    <el-avatar :size="40" :src="conv.avatarUrl || defaultAvatar" />
                    <div class="conv-info">
                        <div class="conv-top">
                            <span class="conv-name">{{ conv.nickname || conv.username }}</span>
                            <span class="conv-time">{{ formatTime(conv.lastMessageTime) }}</span>
                        </div>
                        <div class="conv-preview">{{ conv.lastMessageContent }}</div>
                    </div>
                    <div v-if="conv.unreadCount > 0" class="unread-badge">{{ conv.unreadCount }}</div>
                </div>
            </div>
        </div>
        
        <div class="chat-main">
            <template v-if="currentChatUser">
                <div class="chat-main-header">
                    <div class="user-info" @click="goToUser(currentChatUser.id)">
                        <el-avatar :size="32" :src="currentChatUser.avatarUrl || defaultAvatar" />
                        <span class="username">{{ currentChatUser.nickname || currentChatUser.username }}</span>
                    </div>
                </div>
                
                <div class="message-list" ref="messageListRef">
                    <div v-if="loadingMessages" class="loading-messages">
                        <el-icon class="is-loading"><Loading /></el-icon>
                    </div>
                    <div v-else-if="messages.length === 0" class="empty-messages">
                        <span>开始聊天吧</span>
                    </div>
                    <div 
                        v-for="msg in messages" 
                        :key="msg.id" 
                        class="message-item"
                        :class="{ 'message-mine': msg.senderId === currentUser?.id }"
                    >
                        <el-avatar class="message-avatar" :size="32" :src="(msg.senderId === currentUser?.id ? currentUser.avatarUrl : currentChatUser.avatarUrl) || defaultAvatar" />
                        <div class="message-bubble">
                            <div class="message-content" v-html="formatMessageContent(msg.content)"></div>
                            <div class="message-time">{{ formatTime(msg.createdAt) }}</div>
                        </div>
                    </div>
                </div>
                
                <div class="chat-input-area">
                    <el-input
                        v-model="newMessage"
                        type="textarea"
                        :rows="3"
                        placeholder="输入消息..."
                        resize="none"
                        @keydown.enter.prevent="sendMessage"
                    />
                    <div class="input-actions">
                        <el-popover placement="top" :width="300" trigger="click">
                            <template #reference>
                                <el-button circle class="emoji-btn" style="margin-right: 10px;">
                                    <span style="font-size: 18px; line-height: 1;">😀</span>
                                </el-button>
                            </template>
                            <div class="emoji-picker">
                                <span 
                                    v-for="emoji in emojis" 
                                    :key="emoji" 
                                    class="emoji-item"
                                    @click="addEmoji(emoji)"
                                >{{ emoji }}</span>
                            </div>
                        </el-popover>
                        <el-button type="primary" :disabled="!newMessage.trim()" @click="sendMessage">发送</el-button>
                    </div>
                </div>
            </template>
            <div v-else class="empty-chat-main">
                <el-empty description="选择一个联系人开始聊天" />
            </div>
        </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../utils/request'
import authStore from '../stores/auth'
import { Loading, ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import SockJS from 'sockjs-client/dist/sockjs.min.js'
import { parseServerTime } from '../utils/time'
import Stomp from 'stompjs'

const route = useRoute()
const router = useRouter()
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'

const conversations = ref([])
const messages = ref([])
const currentChatUser = ref(null)
const currentUser = computed(() => authStore.state.user)
const newMessage = ref('')
const loadingConversations = ref(false)
const loadingMessages = ref(false)
const messageListRef = ref(null)
let stompClient = null

const emojis = [
    '😀', '😃', '😄', '😁', '😆', '😅', '🤣', '😂', '🙂', '🙃',
    '😉', '😊', '😇', '🥰', '😍', '🤩', '😘', '😗', '😚', '😙',
    '😋', '😛', '😜', '🤪', '😝', '🤑', '🤗', '🤭', '🤫', '🤔',
    '🤐', '🤨', '😐', '😑', '😶', '😏', '😒', '🙄', '😬', '🤥',
    '😌', '😔', '😪', '🤤', '😴', '😷', '🤒', '🤕', '🤢', '🤮',
    '🤧', '🥵', '🥶', '🥴', '😵', '🤯', '🤠', '🥳', '😎', '🤓',
    '🧐', '😕', '😟', '🙁', '😮', '😯', '😲', '😳', '🥺', '😦',
    '😧', '😨', '😰', '😥', '😢', '😭', '😱', '😖', '😣', '😞',
    '😓', '😩', '😫', '🥱', '😤', '😡', '😠', '🤬', '😈', '👿',
    '💀', '☠️', '💩', '🤡', '👹', '👺', '👻', '👽', '👾', '🤖',
    '😺', '😸', '😹', '😻', '😼', '😽', '🙀', '😿', '😾', '👋',
    '🤚', '🖐', '✋', '🖖', '👌', '🤏', '✌️', '🤞', '🤟', '🤘',
    '🤙', '👈', '👉', '👆', '🖕', '👇', '👍', '👎', '✊', '👊',
    '🤛', '🤜', '👏', '🙌', '👐', '🤲', '🤝', '🙏', '💅', '💪'
]


onMounted(async () => {
    // Register global navigation handler for post links in messages
    window.__navigateToPost = (postId) => {
        router.push(`/post/${postId}`)
    }
    
    if (!currentUser.value) {
        await authStore.fetchUser()
    }
    await fetchConversations()
    connectWebSocket()
    
    // If query has userId, open that chat
    const targetUserId = route.query.userId
    if (targetUserId) {
        // Check if already in conversations
        const existingConv = conversations.value.find(c => c.userId == targetUserId)
        if (existingConv) {
            selectConversation(existingConv)
        } else {
            // Fetch user info and start new chat
            try {
                const res = await request.get(`/users/${targetUserId}`)
                const user = res.data
                currentChatUser.value = user
                // Fetch empty messages or history
                fetchMessages(targetUserId)
            } catch (e) {
                console.error('Failed to fetch target user', e)
            }
        }
    }
})

const fetchConversations = async () => {
    loadingConversations.value = true
    try {
        const res = await request.get('/messages/conversations')
        conversations.value = res.data
    } catch (e) {
        console.error('Failed to fetch conversations', e)
    } finally {
        loadingConversations.value = false
    }
}

const selectConversation = (conv) => {
    currentChatUser.value = {
        id: conv.userId,
        username: conv.username,
        nickname: conv.nickname,
        avatarUrl: conv.avatarUrl
    }
    // Mark as read locally
    conv.unreadCount = 0
    fetchMessages(conv.userId)
}

const fetchMessages = async (userId) => {
    loadingMessages.value = true
    try {
        const res = await request.get(`/messages/${userId}`)
        messages.value = res.data
        scrollToBottom()
        // Mark as read on server
        await request.put(`/messages/${userId}/read`)
    } catch (e) {
        console.error('Failed to fetch messages', e)
    } finally {
        loadingMessages.value = false
    }
}

const addEmoji = (emoji) => {
    newMessage.value += emoji
}

const formatMessageContent = (content) => {
    if (!content) return ''
    // Escape HTML to prevent XSS, then convert [POST:ID] to clickable links
    const escaped = content
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/\n/g, '<br>')
        .replace(/\[POST:(\d+)\]/g, (_, postId) => {
            return `<a href="#/post/${postId}" onclick="event.preventDefault(); window.__navigateToPost('${postId}')" class="post-link">查看帖子 →</a>`
        })
    return escaped
}

const sendMessage = async () => {
    if (!newMessage.value.trim() || !currentChatUser.value) return
    
    const content = newMessage.value.trim()
    newMessage.value = '' // Clear input immediately
    
    try {
        const res = await request.post('/messages', {
            receiverId: currentChatUser.value.id,
            content: content
        })
        
        const sentMsg = res.data
        messages.value.push(sentMsg)
        scrollToBottom()
        
        // Update conversation list
        const convIndex = conversations.value.findIndex(c => c.userId === currentChatUser.value.id)
        if (convIndex !== -1) {
            conversations.value[convIndex].lastMessageContent = content
            conversations.value[convIndex].lastMessageTime = new Date().toISOString()
            // Move to top
            const conv = conversations.value.splice(convIndex, 1)[0]
            conversations.value.unshift(conv)
        } else {
            // Add new conversation item
            conversations.value.unshift({
                userId: currentChatUser.value.id,
                username: currentChatUser.value.username,
                nickname: currentChatUser.value.nickname,
                avatarUrl: currentChatUser.value.avatarUrl,
                lastMessageContent: content,
                lastMessageTime: new Date().toISOString(),
                unreadCount: 0
            })
        }
    } catch (e) {
        console.error('Failed to send message', e)
        ElMessage.error('发送失败')
    }
}

const scrollToBottom = () => {
    nextTick(() => {
        if (messageListRef.value) {
            messageListRef.value.scrollTop = messageListRef.value.scrollHeight
        }
    })
}

const formatTime = (timeStr) => {
    const date = parseServerTime(timeStr)
    if (!date) return ''
    const now = new Date()
    if (date.toDateString() === now.toDateString()) {
        return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }
    return date.toLocaleDateString()
}

const goToUser = (userId) => {
    router.push(`/user/${userId}`)
}

onUnmounted(() => {
    if (stompClient && stompClient.connected) {
        stompClient.disconnect()
    }
    delete window.__navigateToPost
})

const connectWebSocket = () => {
    if (stompClient && stompClient.connected) return

    // Connect to Gateway (8080) which routes to Goodshare Server
    const socket = new SockJS('http://localhost:8080/ws')
    stompClient = Stomp.over(socket)
    stompClient.debug = null // Disable debug logs

    const token = authStore.state.token
    if (!token) return

    stompClient.connect({ 'Authorization': 'Bearer ' + token }, (frame) => {
        // Subscribe to user-specific queue
        stompClient.subscribe('/user/queue/messages', (message) => {
            const msg = JSON.parse(message.body)
            handleIncomingMessage(msg)
        })
    }, (error) => {
        console.error('STOMP error', error)
    })
}

const handleIncomingMessage = (msg) => {
    const otherId = msg.senderId === currentUser.value.id ? msg.receiverId : msg.senderId
    
    // Update active chat if open
    if (currentChatUser.value && currentChatUser.value.id === otherId) {
        // Only append if it's not already in the list (avoid duplicates if we sent it via HTTP and also got WS echo)
        // Actually, we should rely on WS for everything OR handle deduplication.
        // Current sendMessage adds to list immediately.
        // If we get an echo back from WS for our own message, we should check ID.
        
        // Check if message with this ID already exists (if ID is available)
        // Our sendMessage returns the saved msg with ID.
        const exists = messages.value.some(m => m.id === msg.id)
        if (!exists) {
            messages.value.push(msg)
            scrollToBottom()
            
            // Mark as read if it's incoming and we are viewing it
            if (msg.senderId !== currentUser.value.id) {
                 request.put(`/messages/${otherId}/read`)
            }
        }
    }
    
    // Update conversation list item
    updateConversationList(msg)
}

const updateConversationList = (msg) => {
    const otherId = msg.senderId === currentUser.value.id ? msg.receiverId : msg.senderId
    const existingIndex = conversations.value.findIndex(c => c.userId === otherId)
    
    if (existingIndex !== -1) {
        const conv = conversations.value[existingIndex]
        conv.lastMessageContent = msg.content
        conv.lastMessageTime = msg.createdAt
        
        // Increment unread if incoming and NOT currently viewing this chat
        if (msg.senderId !== currentUser.value.id) {
            if (!currentChatUser.value || currentChatUser.value.id !== otherId) {
                conv.unreadCount = (conv.unreadCount || 0) + 1
            }
        }
        
        // Move to top
        conversations.value.splice(existingIndex, 1)
        conversations.value.unshift(conv)
    } else {
        // New conversation - refresh list to get full user details
        fetchConversations()
    }
}
</script>

<style scoped>
.chat-container {
    display: flex;
    justify-content: center;
    padding: 20px;
    padding-left: calc(20px + var(--sidebar-width));
    background-color: var(--bg-color);
    height: 100vh;
    box-sizing: border-box;
}

.chat-layout {
    display: flex;
    width: 100%;
    max-width: 1000px;
    background: var(--bg-color-overlay);
    border-radius: 16px;
    overflow: hidden;
    box-shadow: 0 2px 12px rgba(0,0,0,0.05);
    height: calc(100vh - 40px);
}

.chat-sidebar {
    width: 300px;
    border-right: 1px solid var(--border-color);
    display: flex;
    flex-direction: column;
}

.chat-header {
    padding: 20px;
    border-bottom: 1px solid var(--border-color);
    display: flex;
    align-items: center;
}
.chat-header h3 {
    margin: 0;
    font-size: 18px;
}

.conversation-list {
    flex: 1;
    overflow-y: auto;
}

.conversation-item {
    display: flex;
    padding: 15px;
    cursor: pointer;
    transition: background-color 0.2s;
    position: relative;
}

.conversation-item:hover {
    background-color: var(--hover-bg);
}

.conversation-item.active {
    background-color: var(--hover-bg);
}

.conv-info {
    flex: 1;
    margin-left: 12px;
    overflow: hidden;
}

.conv-top {
    display: flex;
    justify-content: space-between;
    margin-bottom: 4px;
}

.conv-name {
    font-weight: 600;
    font-size: 14px;
    color: var(--text-color);
}

.conv-time {
    font-size: 12px;
    color: var(--text-color-secondary);
}

.conv-preview {
    font-size: 13px;
    color: var(--text-color-secondary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.unread-badge {
    position: absolute;
    right: 15px;
    bottom: 15px;
    background-color: #ff2442;
    color: white;
    border-radius: 10px;
    padding: 0 6px;
    font-size: 12px;
    height: 18px;
    line-height: 18px;
}

.chat-main {
    flex: 1;
    display: flex;
    flex-direction: column;
}

.chat-main-header {
    padding: 15px 20px;
    border-bottom: 1px solid var(--border-color);
    display: flex;
    align-items: center;
}

.user-info {
    display: flex;
    align-items: center;
    cursor: pointer;
}

.user-info .username {
    margin-left: 10px;
    font-weight: 600;
}

.message-list {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 20px;
}

.message-item {
    display: flex;
    align-items: flex-start;
    max-width: 70%;
    width: 100%; /* Ensure it takes full width of container for alignment */
}

.message-mine {
    align-self: flex-end;
    flex-direction: row-reverse;
}

.message-avatar {
    margin: 0 10px;
    flex-shrink: 0; /* Prevent avatar from shrinking */
}

.message-bubble {
    background-color: var(--hover-bg);
    padding: 10px 15px;
    border-radius: 12px;
    border-top-left-radius: 2px;
    color: var(--text-color);
}

.message-mine .message-bubble {
    background-color: var(--el-color-primary);
    color: white;
    border-top-left-radius: 12px;
    border-top-right-radius: 2px;
}

.message-time {
    font-size: 11px;
    color: var(--text-color-secondary);
    margin-top: 4px;
    text-align: right;
}

.message-mine .message-time {
    color: rgba(255,255,255,0.8);
}

.chat-input-area {
    padding: 20px;
    border-top: 1px solid var(--border-color);
}

.input-actions {
    display: flex;
    justify-content: flex-end;
    margin-top: 10px;
}

.empty-chat-main {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: center;
}

.emoji-picker {
    display: grid;
    grid-template-columns: repeat(8, 1fr);
    gap: 5px;
    max-height: 200px;
    overflow-y: auto;
}

.emoji-item {
    font-size: 20px;
    cursor: pointer;
    text-align: center;
    padding: 5px;
    border-radius: 4px;
    transition: background-color 0.2s;
}

.emoji-item:hover {
    background-color: var(--hover-bg);
}

:deep(.post-link) {
    color: #ffb3bd;
    text-decoration: underline;
    cursor: pointer;
    font-weight: 500;
}

:deep(.post-link:hover) {
    color: #ff2442;
}
</style>