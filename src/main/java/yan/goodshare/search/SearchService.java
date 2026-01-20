package yan.goodshare.search;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import yan.goodshare.entity.Post;
import org.springframework.stereotype.Service;
import yan.goodshare.mapper.SearchStatsMapper;
import yan.goodshare.entity.SearchStats;
import java.util.List;

@Service
public class SearchService {

    private final PostSearchRepository postSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final SearchStatsMapper searchStatsMapper;

    public SearchService(PostSearchRepository postSearchRepository, ElasticsearchOperations elasticsearchOperations, SearchStatsMapper searchStatsMapper) {
        this.postSearchRepository = postSearchRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.searchStatsMapper = searchStatsMapper;
    }

    public void indexPost(Post post) {
        PostDocument postDocument = new PostDocument();
        postDocument.setId(post.getId().toString());
        postDocument.setTitle(post.getTitle());
        postDocument.setContent(post.getContent());
        postDocument.setCoverUrl(post.getCoverUrl());
        postDocument.setLikeCount(post.getLikeCount() != null ? post.getLikeCount() : 0);
        
        if (post.getUser() != null) {
            postDocument.setUserId(post.getUser().getId());
            postDocument.setUsername(post.getUser().getUsername());
            postDocument.setNickname(post.getUser().getNickname());
            postDocument.setAvatarUrl(post.getUser().getAvatarUrl());
        }
        
        postSearchRepository.save(postDocument);
    }

    public SearchHits<PostDocument> searchPosts(String query) {
        if (query != null && !query.trim().isEmpty()) {
            try {
                searchStatsMapper.incrementSearchCount(query.trim());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Query stringQuery = new StringQuery(
            "{\"multi_match\":{\"query\":\"" + query + "\",\"fields\":[\"title\",\"content\"]}}"
        );
        return elasticsearchOperations.search(stringQuery, PostDocument.class);
    }

    public List<SearchStats> getHotKeywords() {
        return searchStatsMapper.selectTopKeywords(10);
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
