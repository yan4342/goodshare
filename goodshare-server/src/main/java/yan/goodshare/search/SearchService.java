package yan.goodshare.search;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import yan.goodshare.entity.Post;
import yan.goodshare.entity.Tag;
import yan.goodshare.entity.User;
import org.springframework.stereotype.Service;
import yan.goodshare.mapper.SearchStatsMapper;
import yan.goodshare.entity.SearchStats;
import yan.goodshare.mapper.PostMapper;
import yan.goodshare.mapper.UserMapper;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest;

@Service
public class SearchService {

    private final PostSearchRepository postSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final SearchStatsService searchStatsService;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final ElasticsearchClient esClient;

    public SearchService(PostSearchRepository postSearchRepository, ElasticsearchOperations elasticsearchOperations, SearchStatsService searchStatsService, PostMapper postMapper, UserMapper userMapper, ElasticsearchClient esClient) {
        this.postSearchRepository = postSearchRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.searchStatsService = searchStatsService;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.esClient = esClient;
    }

    /**
     * 调用 ES IK 分词器对文本进行分词
     */
    public List<String> tokenize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }
        try {
            AnalyzeRequest request = AnalyzeRequest.of(a -> a
                .index("posts")
                .text(text)
                .analyzer("ik_max_word")
            );
            var response = esClient.indices().analyze(request);
            return response.tokens().stream()
                .map(t -> t.token())
                .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("ES tokenize failed: " + e.getMessage());
            return List.of();
        }
    }

    public List<User> searchUsers(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        
        // Find users matching username or nickname
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("username", query)
                   .or()
                   .like("nickname", query);
        
        List<User> users = userMapper.selectList(queryWrapper);
        
        // Clean sensitive data
        users.forEach(user -> {
            user.setPassword(null);
            user.setEmail(null); // Maybe keep email? Better not for public search.
        });
        
        return users.stream()
                .filter(u -> !"system_notification".equals(u.getUsername()))
                .collect(Collectors.toList());
    }

    //为了强制更新mapping和清空数据，直接删除索引比deleteAll更快更可靠
    public void deleteAllPosts() {
        // Explicitly delete index to force mapping update and clear all data
        // postSearchRepository.deleteAll() can be slow and cause timeouts
        elasticsearchOperations.indexOps(PostDocument.class).delete();
    }

    public void indexPost(Post post) {
        PostDocument postDocument = convertToDocument(post);
        System.out.println("Indexing Post: " + post.getId() + ", Title: " + post.getTitle() + ", User: " + postDocument.getUsername() + ", Tags: " + postDocument.getTags());
        postSearchRepository.save(postDocument);
    }

    public void indexPosts(List<Post> posts) {
        List<PostDocument> documents = posts.stream()
            .map(this::convertToDocument)
            .collect(Collectors.toList());
        
        System.out.println("Batch Indexing " + documents.size() + " posts");
        postSearchRepository.saveAll(documents);
    }

    private PostDocument convertToDocument(Post post) {
        PostDocument postDocument = new PostDocument();
        postDocument.setId(post.getId().toString());
        postDocument.setTitle(post.getTitle());
        postDocument.setContent(post.getContent());
        postDocument.setCoverUrl(post.getCoverUrl());
        postDocument.setLikeCount(post.getLikeCount() != null ? post.getLikeCount() : 0);
        postDocument.setViewCount(post.getViewCount() != null ? post.getViewCount() : 0);
        postDocument.setCommentCount(post.getCommentCount() != null ? post.getCommentCount() : 0);
        postDocument.setStatus(post.getStatus());
        
        // Handle User Info (Fetch if missing)
        if (post.getUser() != null) {
            postDocument.setUserId(post.getUser().getId() != null ? post.getUser().getId() : post.getUserId());
            postDocument.setUsername(post.getUser().getUsername());
            postDocument.setNickname(post.getUser().getNickname());
            postDocument.setAvatarUrl(post.getUser().getAvatarUrl());
        } else if (post.getUserId() != null) {
            User user = userMapper.selectById(post.getUserId());
            if (user != null) {
                postDocument.setUserId(user.getId());
                postDocument.setUsername(user.getUsername());
                postDocument.setNickname(user.getNickname());
                postDocument.setAvatarUrl(user.getAvatarUrl());
            }
        } else {
            System.err.println("Warning: Indexing post " + post.getId() + " without user info.");
        }
        
        // Handle Tags (Fetch if missing)
        if (post.getTags() != null && !post.getTags().isEmpty()) {
            postDocument.setTags(post.getTags().stream().map(Tag::getName).collect(Collectors.toList()));
        } else {
            Set<Tag> tags = postMapper.selectTagsByPostId(post.getId());
            if (tags != null && !tags.isEmpty()) {
                postDocument.setTags(tags.stream().map(Tag::getName).collect(Collectors.toList()));
            }
        }
        return postDocument;
    }

    public SearchHits<PostDocument> searchPosts(String query, String tag) {
        if (tag != null && !tag.trim().isEmpty()) {
            // Tag-based exact search (Partition search)
             Query tagQuery = new StringQuery(
                "{\"bool\": {\"must\": [" +
                "  {\"term\":{\"tags.keyword\":\"" + tag.replace("\"", "\\\"") + "\"}}," +
                "  {\"term\":{\"status\":1}}" +
                "]}}"
            );
            return elasticsearchOperations.search(tagQuery, PostDocument.class);
        }
        
        if (query != null && !query.trim().isEmpty()) {
            searchStatsService.incrementSearchCount(query.trim());
        }
        
        String safeQuery = query.replace("\"", "\\\"");

        // Two-tier Chinese text search:
        // 1. phrase match with boost 2.0 → adjacent tokens score highest (precision)
        // 2. operator:and fallback → catches queries with extra stopwords like "三体的读后感" matching "三体读后感" (recall)
        Query stringQuery = new StringQuery(
            "{\"bool\": {\"must\": [" +
            "  {\"bool\": {" +
            "    \"should\": [" +
            "      {\"multi_match\":{\"query\":\"" + safeQuery + "\",\"fields\":[\"title^3\",\"content\",\"username\",\"nickname\"],\"type\":\"phrase\",\"slop\":0,\"boost\":2.0}}," +
            "      {\"multi_match\":{\"query\":\"" + safeQuery + "\",\"fields\":[\"title^3\",\"content\",\"username\",\"nickname\"],\"operator\":\"and\"}}," +
            "      {\"term\":{\"tags\":\"" + safeQuery + "\"}}," +
            "      {\"term\":{\"tags.keyword\":\"" + safeQuery + "\"}}" +
            "    ]" +
            "  }}," +
            "  {\"term\":{\"status\":1}}" +
            "]}}"
        );
        return elasticsearchOperations.search(stringQuery, PostDocument.class);
    }

    public List<String> suggest(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        
        // Use match_phrase_prefix for search suggestions on title
        Query stringQuery = new StringQuery(
            "{\"match_phrase_prefix\":{\"title\":\"" + query + "\"}}"
        );
        
        SearchHits<PostDocument> hits = elasticsearchOperations.search(stringQuery, PostDocument.class);
        return hits.stream()
            .map(hit -> hit.getContent().getTitle())
            .distinct()
            .limit(10)
            .collect(java.util.stream.Collectors.toList());
    }

    public List<SearchStats> getHotKeywords() {
        return searchStatsService.getHotKeywords();
    }

    public SearchHits<PostDocument> findSimilarPosts(String postId) {
        String mltQuery = "{\n" +
                "  \"more_like_this\" : {\n" +
                "    \"fields\" : [\"title\", \"content\"],\n" +
                "    \"like\" : [\n" +
                "      {\n" +
                "        \"_index\" : \"posts\",\n" +
                "        \"_id\" : \"" + postId + "\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"min_term_freq\" : 1,\n" +
                "    \"max_query_terms\" : 12,\n" +
                "    \"min_doc_freq\": 1\n" +
                "  }\n" +
                "}";
        Query stringQuery = new StringQuery(mltQuery);
        return elasticsearchOperations.search(stringQuery, PostDocument.class);
    }

    public void updateLikeCount(Long postId, Integer count) {
        java.util.Optional<PostDocument> optionalDoc = postSearchRepository.findById(postId.toString());
        if (optionalDoc.isPresent()) {
            PostDocument doc = optionalDoc.get();
            doc.setLikeCount(count);
            postSearchRepository.save(doc);
        }
    }

    public void deletePost(Long postId) {
        postSearchRepository.deleteById(postId.toString());
    }
}
