package yan.goodshare.ai;

import org.springframework.web.bind.annotation.*;
import yan.goodshare.entity.Post;
import yan.goodshare.mapper.PostMapper;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final DeepSeekService deepSeekService;
    private final PostMapper postMapper;

    public AIController(DeepSeekService deepSeekService, PostMapper postMapper) {
        this.deepSeekService = deepSeekService;
        this.postMapper = postMapper;
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        String response = deepSeekService.chat(userMessage);
        return Map.of("response", response);
    }

    @GetMapping("/summarize/{postId}")
    public Map<String, String> summarize(@PathVariable Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new IllegalArgumentException("Post not found");
        }

        String content = post.getContent();
        if (content == null || content.trim().isEmpty()) {
            content = post.getTitle(); // Fallback to title
        }

        // Limit content length to avoid exceeding token limits
        if (content.length() > 5000) {
            content = content.substring(0, 5000);
        }

        String prompt = "Please summarize the following content in a concise manner (max 100 words):\n\n" + content;
        String summary = deepSeekService.chat(prompt, "You are a helpful summarization assistant.");

        return Map.of("summary", summary);
    }

    @PostMapping("/generate")
    public Map<String, String> generatePost(@RequestBody Map<String, String> request) {
        String keyword = request.get("keyword");
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }

        String systemPrompt = "你是一个专业的社交媒体内容创作者。请根据用户提供的商品名或主题，写一篇吸引人的测评笔记。" +
                "要求：\n" +
                "1. 标题要吸引人。\n" +
                "2. 正文分段落，包含使用体验、优缺点分析等。\n" +
                "3. 语气亲切、真实、有感染力。\n" +
                "4. 可以使用emoji。\n" +
                "5. 字数控制在300-450字左右。";
        
        String userPrompt = "请为以下商品/主题写一篇笔记：" + keyword;

        String content = deepSeekService.chat(userPrompt, systemPrompt);
        return Map.of("content", content);
    }

    @PostMapping("/generate-stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter generatePostStream(@RequestBody Map<String, String> request) {
        String keyword = request.get("keyword");
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Keyword cannot be empty");
        }

        String systemPrompt = "你是一个专业的社交媒体内容创作者。请根据用户提供的商品名或主题，写一篇吸引人的测评笔记。" +
                "要求：\n" +
                "1. 标题要吸引人。\n" +
                "2. 正文分段落，包含使用体验、优缺点分析等。\n" +
                "3. 语气亲切、真实、有感染力。\n" +
                "4. 可以使用emoji。\n" +
                "5. 字数控制在300-450字左右。\n" +
                "6. 请直接返回HTML格式内容，使用<p>标签分段，<b>标签加粗，<ul><li>标签列举。不要使用Markdown代码块。";
        
        String userPrompt = "请为以下商品/主题写一篇笔记：" + keyword;

        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(60000L); // 60s timeout
        deepSeekService.streamChat(userPrompt, systemPrompt, emitter);
        
        return emitter;
    }
}
