package yan.goodshare.search;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import yan.goodshare.entity.Post;
import org.springframework.stereotype.Service;
//

@Service
public class SearchService {

    private final PostSearchRepository postSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    public SearchService(PostSearchRepository postSearchRepository, ElasticsearchOperations elasticsearchOperations) {
        this.postSearchRepository = postSearchRepository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public void indexPost(Post post) {
        PostDocument postDocument = new PostDocument();
        postDocument.setId(post.getId().toString());
        postDocument.setTitle(post.getTitle());
        postDocument.setContent(post.getContent());
        postSearchRepository.save(postDocument);
    }

    public SearchHits<PostDocument> searchPosts(String query) {
        Query stringQuery = new StringQuery(
            "{\"multi_match\":{\"query\":\"" + query + "\",\"fields\":[\"title\",\"content\"]}}"
        );
        return elasticsearchOperations.search(stringQuery, PostDocument.class);
    }
}
