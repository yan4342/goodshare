package yan.goodshare.product;

import org.springframework.stereotype.Service;
import yan.goodshare.post.Post;
import yan.goodshare.post.PostRepository;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final PriceRepository priceRepository;
    private final PostRepository postRepository;

    public ProductService(ProductRepository productRepository, PriceRepository priceRepository, PostRepository postRepository) {
        this.productRepository = productRepository;
        this.priceRepository = priceRepository;
        this.postRepository = postRepository;
    }

    public Product addProductToPost(Long postId, Product product) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        product.setPost(post);
        return productRepository.save(product);
    }

    public Price addPriceToProduct(Long productId, Price price) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        price.setProduct(product);
        return priceRepository.save(price);
    }

    public List<Product> getProductsByPost(Long postId) {
        return productRepository.findByPostId(postId);
    }
}
